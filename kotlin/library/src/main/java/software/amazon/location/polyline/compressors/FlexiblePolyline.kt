// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// This class implements the Flexible-Polyline variation of the
// Encoded Polyline algorithm (https://github.com/heremaps/flexible-polyline).
// The algorithm supports both 2D and 3D data.

package software.amazon.location.polyline.compressors

import software.amazon.location.polyline.Polyline
import software.amazon.location.polyline.DataCompressor
import software.amazon.location.polyline.algorithm.PolylineDecoder
import software.amazon.location.polyline.algorithm.PolylineEncoder

internal class FlexiblePolyline : DataCompressor() {
    private val dataContainsHeader = true
    private val flexPolylineEncodingTable =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val flexPolylineDecodingTable = intArrayOf(
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, 52, 53, 54, 55, 56, 57, 58, 59, 60,
        61, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
        13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, 63, -1,
        26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44,
        45, 46, 47, 48, 49, 50, 51
    )

    private val encoder: PolylineEncoder
    private val decoder: PolylineDecoder

    init {
        encoder = PolylineEncoder(
            encodingTable = flexPolylineEncodingTable,
            includeHeader = dataContainsHeader
        )
        decoder = PolylineDecoder(
            decodingTable = flexPolylineDecodingTable,
            containsHeader = dataContainsHeader
        )
    }

    override fun compressLngLatArray(
        lngLatArray: Array<DoubleArray>,
        parameters: Polyline.CompressionParameters
    ): PolylineEncoder.CompressResult {
        return encoder.encode(
                lngLatArray = lngLatArray,
                precision = parameters.precisionLngLat,
                thirdDim = parameters.thirdDimension,
                thirdDimPrecision = parameters.precisionThirdDimension
            )
    }

    override fun decompressLngLatArray(compressedData: String): PolylineDecoder.DecompressResult {
       return decoder.decode(encoded = compressedData)
    }
}
