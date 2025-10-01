// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.amazon.location.polyline.algorithm

import kotlin.math.abs
import kotlin.math.pow
import software.amazon.location.polyline.FlexiblePolylineFormatVersion
import software.amazon.location.polyline.Polyline

/** Decodes polyline strings into lng/lat coordinate arrays
 * @param decodingTable A lookup table that converts ASCII values from 0x00-0x7F
 *    to the appropriate decoded 0x00-0x3F value. Polyline and Flexible-Polyline
 *    use different character encodings, so they need different decoding tables.
 * @param containsHeader True if the format includes a header (Flexible-Polyline),
 *    and false if it doesn't (Polyline).
 */
internal class PolylineDecoder(
    private val decodingTable: IntArray,
    private val containsHeader: Boolean
) {
    sealed class DecompressResult {
        data class Success(val lngLatArray: Array<DoubleArray>, val parameters: Polyline.CompressionParameters) : DecompressResult()
        data class Error(val error: Polyline.DecodeError) : DecompressResult()
    }

    private sealed class DecodeValueResult {
        data class Success(val value:Long, val nextIndex:Int) : DecodeValueResult()
        data class Error(val error: Polyline.DecodeError) : DecodeValueResult()

    }

    private sealed class DecodeHeaderResult {
        data class Success(val parameters:Polyline.CompressionParameters, val nextIndex:Int) : DecodeHeaderResult()
        data class Error(val error: Polyline.DecodeError) : DecodeHeaderResult()

    }
    // Given an encoded string and a starting index, this decodes a single encoded signed value.
    // The decoded value will be an integer that still needs the decimal place moved over based
    // on the number of digits of encoded precision.
    private fun decodeSignedValue(
        encoded: String,
        startIndex: Int
    ): DecodeValueResult {
        // decode an unsigned value
        val unsignedValue : Long
        val nextIndex : Int
        when (val result = decodeUnsignedValue(encoded, startIndex)) {
            is DecodeValueResult.Success -> {
                unsignedValue = result.value
                nextIndex = result.nextIndex
            }
            is DecodeValueResult.Error -> return result
        }

        // If the unsigned value has a 1 encoded in its least significant bit,
        // it's negative, so flip the bits.
        var signedValue = unsignedValue
        if ((unsignedValue and 1).toInt() == 1) {
            signedValue = unsignedValue.inv()
        }

        // Shift the result by one to remove the encoded sign bit.
        signedValue = signedValue shr 1

        return DecodeValueResult.Success(signedValue, nextIndex)
    }

    // Given an encoded string and a starting index, this decodes a single encoded
    // unsigned value. The flexible-polyline algorithm uses this directly to decode
    // the header bytes, since those are encoded without the sign bit as the header
    // values are known to be unsigned (which saves 2 bits).
    private fun decodeUnsignedValue(
        encoded: String,
        startIndex: Int
    ): DecodeValueResult {
        var result: Long = 0
        var shift = 0
        var index = startIndex

        // For each ASCII character, get the 6-bit (0x00 - 0x3F) value that
        // it represents. Shift the accumulated result by 5 bits, add the new
        // 5-bit chunk to the bottom, and keep going for as long as the 6th bit
        // is set.
        while (index < encoded.length) {
            val charCode = encoded[index].code
            val value = decodingTable[charCode]
            if (value < 0) {
                return DecodeValueResult.Error(Polyline.DecodeError.InvalidEncodedCharacter)
            }
            result = result or (value.and(0x1f).toLong() shl shift)
            shift += 5
            index += 1

            // We've reached the final 5-bit chunk for this value, so return.
            // We also return the index, which represents the starting index of the
            // next value to decode.
            if (value.and(0x20) == 0) {
                return DecodeValueResult.Success(result, index)
            }
        }

        // If we've run out of encoded characters without finding an empty 6th bit,
        // something has gone wrong.
        return DecodeValueResult.Error(Polyline.DecodeError.ExtraContinueBit)
    }

    private fun decodeHeader(encoded: String): DecodeHeaderResult {
        // If the data has a header, the first value is expected to be the header version
        // and the second value is compressed metadata containing precision and dimension information.
        val headerVersion : Long
        val metadataIndex : Int

        when (val result = decodeUnsignedValue(encoded, 0)) {
            is DecodeValueResult.Success -> {
                headerVersion = result.value
                metadataIndex = result.nextIndex
            }
            is DecodeValueResult.Error -> return DecodeHeaderResult.Error(result.error)
        }

        if (headerVersion.toInt() != FlexiblePolylineFormatVersion) {
            return DecodeHeaderResult.Error(Polyline.DecodeError.InvalidHeaderVersion)
        }

        return when (val result = decodeUnsignedValue(encoded, metadataIndex)) {
            is DecodeValueResult.Success -> DecodeHeaderResult.Success(
                Polyline.CompressionParameters(
                    precisionLngLat = (result.value and 0x0f).toInt(),
                    precisionThirdDimension = (result.value shr 7 and 0x0f).toInt(),
                    thirdDimension = Polyline.ThirdDimension.entries[(result.value shr 4 and 0x07).toInt()]
                ),
                result.nextIndex
            )
            is DecodeValueResult.Error -> DecodeHeaderResult.Error(result.error)
        }
    }

    fun decode(
        encoded: String,
        encodePrecision: Int = 0
    ): DecompressResult {
        // Empty input strings are considered invalid.
        if (encoded.isEmpty()) {
            return DecompressResult.Error(Polyline.DecodeError.EmptyInput)
        }

        // If the data doesn't have a header, default to the passed-in precision and no 3rd dimension.
        var header = Polyline.CompressionParameters(
            precisionLngLat = encodePrecision,
            precisionThirdDimension = 0,
            thirdDimension = Polyline.ThirdDimension.None
        )

        // Track the index of the next character to decode from the encoded string.
        var index = 0

        if (containsHeader) {
            when (val result = decodeHeader(encoded)) {
                is DecodeHeaderResult.Success -> {
                    header = result.parameters
                    index = result.nextIndex
                }
                is DecodeHeaderResult.Error -> return DecompressResult.Error(result.error)
            }
        }

        val numDimensions = if (header.thirdDimension != Polyline.ThirdDimension.None) 3 else 2
        val outputLngLatArray = mutableListOf<DoubleArray>()

        // The data either contains lat/lng or lat/lng/z values that will be decoded.
        // precisionDivisors are the divisors needed to convert the values from integers
        // back to floating-point.
        val precisionDivisors = listOf(
            10.0.pow(header.precisionLngLat.toDouble()),
            10.0.pow(header.precisionLngLat.toDouble()),
            10.0.pow(header.precisionThirdDimension.toDouble())
        )

        // maxAllowedValues are the maximum absolute values allowed for lat/lng/z. This is used for
        // error-checking the coordinate values as they're being decoded.
        val maxAllowedValues = listOf(90.0, 180.0, Double.MAX_VALUE)

        // While decoding, we want to switch from lat/lng/z to lng/lat/z, so this index tells us
        // what position to put the dimension in for the resulting coordinate.
        val resultDimensionIndex = intArrayOf(1, 0, 2)

        // Decoded values are deltas from the previous coordinate values, so track the previous values.
        val lastScaledCoordinate = longArrayOf(0, 0, 0)

        // Keep decoding until we reach the end of the string.
        while (index < encoded.length) {
            // Each time through the loop we'll decode one full coordinate.
            val coordinate = if (numDimensions == 2) doubleArrayOf(0.0, 0.0) else doubleArrayOf(0.0, 0.0, 0.0)
            var deltaValue: Long

            // Decode each dimension for the coordinate.
            for (dimension in 0 until numDimensions) {
                if (index >= encoded.length) {
                    return DecompressResult.Error(Polyline.DecodeError.MissingCoordinateDimension)
                }

                when (val result = decodeSignedValue(encoded, index)) {
                    is DecodeValueResult.Success -> {
                        deltaValue = result.value
                        index = result.nextIndex
                    }
                    is DecodeValueResult.Error -> return DecompressResult.Error(result.error)
                }
                lastScaledCoordinate[dimension] += deltaValue
                // Get the final lat/lng/z value by scaling the integer back down based on the number of
                // digits of precision.
                val value = lastScaledCoordinate[dimension].toDouble() / precisionDivisors[dimension]
                if (abs(value) > maxAllowedValues[dimension]) {
                    return DecompressResult.Error(Polyline.DecodeError.InvalidCoordinateValue)
                }
                coordinate[resultDimensionIndex[dimension]] = value
            }
            outputLngLatArray.add(coordinate)
        }

        return DecompressResult.Success(outputLngLatArray.toTypedArray(), header)
    }

}