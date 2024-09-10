// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.amazon.location.polyline.compressors

import software.amazon.location.polyline.CompressionParameters
import software.amazon.location.polyline.DataCompressor
import software.amazon.location.polyline.algorithm.PolylineDecoder
import software.amazon.location.polyline.algorithm.PolylineEncoder

// This class implements the Encoded Polyline Algorithm Format
// (https://developers.google.com/maps/documentation/utilities/polylinealgorithm).
// This algorithm is commonly used with either 5 or 6 bits of precision.
// To improve usability and decrease user error, we present Polyline5 and Polyline6
// as two distinct compression algorithms.
open class EncodedPolyline(private val precision: Int) : DataCompressor() {
    private val dataContainsHeader = false
    private val polylineEncodingTable = "?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
    private val polylineDecodingTable = intArrayOf(
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
        15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33,
        34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52,
        53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, -1
    )

    private val encoder = PolylineEncoder(polylineEncodingTable, dataContainsHeader)
    private val decoder = PolylineDecoder(polylineDecodingTable, dataContainsHeader)

    override fun compressLngLatArray(
        lngLatArray: Array<DoubleArray>,
        parameters: CompressionParameters
    ): String {
        return encoder.encode(lngLatArray, precision)
    }

    override fun decompressLngLatArray(compressedData: String): Pair<Array<DoubleArray>, CompressionParameters> {
        val (lngLatArray, header) = decoder.decode(compressedData, precision)
        return Pair(lngLatArray, CompressionParameters(header.precisionLngLat))
    }
}

// Polyline5 and Polyline6 encodes/decodes compressed data with 5 or 6 bits of precision respectively.
class Polyline5 : EncodedPolyline(5)
class Polyline6 : EncodedPolyline(6)
