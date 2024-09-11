// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// This class implements both the Encoded Polyline Algorithm Format
// (https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
// and the Flexible-Polyline variation of the algorithm (https://github.com/heremaps/flexible-polyline).

// This implementation has two differences to improve usability:
// - It uses well-defined rounding to ensure deterministic results across all programming languages.
//   The Flexible-Polyline algorithm definition says to use the rounding rules of the programming
//   language, but this can cause inconsistent rounding depending on what language happens to be used
//   on both the encoding and decoding sides.
// - It caps the max encoding/decoding precision to 11 decimal places (1 micrometer), because 12+ places can
//   lose precision when using 64-bit floating-point numbers to store integers.

package software.amazon.location.polyline.algorithm

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import software.amazon.location.polyline.Polyline
import software.amazon.location.polyline.EncodeException
import software.amazon.location.polyline.FlexiblePolylineFormatVersion

/** Encodes lng/lat coordinate arrays into polyline strings
 * @param encodingTable A lookup table that converts values from 0x00-0x3F
 *     to the appropriate encoded ASCII character. Polyline and Flexible-Polyline
 *     use different character encodings.
 * @param includeHeader True if the format includes a header (Flexible-Polyline),
 *    and false if it doesn't (Polyline).
 */
internal class PolylineEncoder(
    private val encodingTable: String,
    private val includeHeader: Boolean
) {

    // The original polyline algorithm supposedly uses "round to nearest, ties away from 0"
    // for its rounding rule. Flexible-polyline uses the rounding rules of the implementing
    // language. Our generalized implementation will use the "round to nearest, ties away from 0"
    // rule for all languages to keep the encoding deterministic across implementations.
    private fun polylineRound(value: Double): Long {
        val rounded = floor(abs(value) + 0.5)
        return if (value >= 0.0) rounded.toLong() else (-rounded).toLong()
    }

    @Throws(EncodeException::class)
    fun encode(
        lngLatArray: Array<DoubleArray>,
        precision: Int,
        thirdDim: Polyline.ThirdDimension = Polyline.ThirdDimension.None,
        thirdDimPrecision: Int = 0
    ): String {
        if (precision < 0 || precision > 11) {
            throw EncodeException("Invalid precision value, the valid range is 0 - 11", Polyline.EncodeError.InvalidPrecisionValue)
        }
        if (thirdDimPrecision < 0 || thirdDimPrecision > 11) {
            throw EncodeException("Invalid precision value, the valid range is 0 - 11", Polyline.EncodeError.InvalidPrecisionValue)
        }

        if (lngLatArray.isEmpty()) {
            return ""
        }

        val numDimensions = if (thirdDim != Polyline.ThirdDimension.None) 3 else 2

        // The data will either encode lat/lng or lat/lng/z values.
        // precisionMultipliers are the multipliers needed to convert the values
        // from floating-point to scaled integers.
        val precisionMultipliers = doubleArrayOf(
            10.0.pow(precision.toDouble()),
            10.0.pow(precision.toDouble()),
            10.0.pow(thirdDimPrecision.toDouble())
        )

        // While encoding, we want to switch from lng/lat/z to lat/lng/z, so this index tells us
        // what index to grab from the input coordinate when encoding each dimension.
        val inputDimensionIndex = intArrayOf(1, 0, 2)

        // maxAllowedValues are the maximum absolute values allowed for lat/lng/z. This is used for
        // error-checking the coordinate values as they're being encoded.
        val maxAllowedValues = doubleArrayOf(90.0, 180.0, Double.MAX_VALUE)

        // Encoded values are deltas from the previous coordinate values, so track the previous lat/lng/z values.
        val lastScaledCoordinate = LongArray(3) { 0 }

        var output = ""

        // Flexible-polyline starts with an encoded header that contains precision and dimension metadata.
        if (includeHeader) {
            output = encodeHeader(precision, thirdDim, thirdDimPrecision)
        }

        for (coordinate in lngLatArray) {
            if (coordinate.size != numDimensions) {
                throw EncodeException("All the coordinates need to have the same number of dimensions", Polyline.EncodeError.InconsistentCoordinateDimensions)
            }

            for (dimension in 0 until numDimensions) {
                // Even though our input data is in lng/lat/z order, this is where we grab them in
                // lat/lng/z order for encoding.
                val inputValue = coordinate[inputDimensionIndex[dimension]]
                // While looping through, also verify the input data is valid
                if (abs(inputValue) > maxAllowedValues[dimension]) {
                    throw EncodeException("Latitude values need to be in [-90, 90] and longitude values need to be in [-180, 180]", Polyline.EncodeError.InvalidCoordinateValue)
                }
                // Scale the value based on the number of digits of precision, encode the delta between
                // it and the previous value to the output, and track it as the previous value for encoding
                // the next delta.
                val scaledValue = polylineRound(inputValue * precisionMultipliers[dimension])
                output += encodeSignedValue(scaledValue - lastScaledCoordinate[dimension])
                lastScaledCoordinate[dimension] = scaledValue
            }
        }

        return output
    }

    private fun encodeHeader(
        precision: Int,
        thirdDim: Polyline.ThirdDimension,
        thirdDimPrecision: Int
    ): String {
        // Combine all the metadata about the encoded data into a single value for the header.
        val metadataValue = (thirdDimPrecision shl 7) or (thirdDim.value shl 4) or precision
        return (
                encodeUnsignedValue(FlexiblePolylineFormatVersion.toLong()) +
                        encodeUnsignedValue(metadataValue.toLong())
                )
    }

    private fun encodeUnsignedValue(value: Long): String {
        var encodedString = ""
        var remainingValue = value
        // Loop through each 5-bit chunk in the value, add a 6th bit if there
        // will be additional chunks, and encode to an ASCII value.
        while (remainingValue > 0x1f) {
            val chunk = (remainingValue and 0x1f) or 0x20
            val encodedChar = encodingTable[chunk.toInt()]
            encodedString += encodedChar
            remainingValue = remainingValue shr 5
        }
        // For the last chunk, set the 6th bit to 0 (since there are no more chunks) and encode it.
        val finalEncodedChar = encodingTable[remainingValue.toInt()]
        return encodedString + finalEncodedChar
    }

    private fun encodeSignedValue(value: Long): String {
        var unsignedValue = value
        // Shift the value over by 1 bit to make room for the sign bit at the end.
        unsignedValue = unsignedValue shl 1
        // If the input value is negative, flip all the bits, including the sign bit.
        if (value < 0) {
            unsignedValue = unsignedValue.inv()
        }

        return encodeUnsignedValue(unsignedValue)
    }

}