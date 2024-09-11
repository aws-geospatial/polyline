// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.amazon.location.polyline

import software.amazon.location.polyline.compressors.FlexiblePolyline
import software.amazon.location.polyline.compressors.Polyline5
import software.amazon.location.polyline.compressors.Polyline6

object Polyline {

    /** Defines the set of compression algorithms that are supported by this library. */
    enum class CompressionAlgorithm {
        /** Encoder/decoder for the [Flexible Polyline](https://github.com/heremaps/flexible-polyline) format. */
        FlexiblePolyline,

        /** Encoder/decoder for the [Encoded Polyline Algorithm Format](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
         * with 5 bits of precision.
         */
        Polyline5,

        /** Encoder/decoder for the [Encoded Polyline Algorithm Format](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
         * with 6 bits of precision.
         */
        Polyline6
    }

    /** Defines how to interpret a third dimension value if it exists. */
    enum class ThirdDimension(val value: Int) {
        /** No third dimension specified */
        None(0),
        /** Third dimension is level */
        Level(1),
        /** Third dimension is altitude (height above the Earth's surface) */
        Altitude(2),
        /** Third dimension is elevation (height of the Earth's surface relative to the reference geoid) */
        Elevation(3)
    }

    /** The optional set of parameters for encoding a set of LngLat coordinates.
     * Currently, only the FlexiblePolyline algorithm supports these parameters. The Polyline5 / Polyline6
     * algorithms ignore them, as they don't support 3D data and we've defined them to use
     * a fixed precision value.
     */
    data class CompressionParameters(
        /** The number of decimal places of precision to use for compressing longitude and latitude. */
        val precisionLngLat: Int = 6,
        /** The number of decimal places of precision to use for compressing the third dimension of data. */
        val precisionThirdDimension: Int = 0,
        /** The type of third dimension data being encoded - none, level, altitude, or elevation. */
        val thirdDimension: ThirdDimension = ThirdDimension.None
    )

    // Decode errors
    enum class DecodeError {
        EmptyInput,
        InvalidEncodedCharacter,
        ExtraContinueBit,
        InvalidHeaderVersion,
        MissingCoordinateDimension,
        InvalidCoordinateValue,
        InvalidLineStringLength,
        InvalidPolygonLength,
        InvalidPolygonClosure
    }

    enum class EncodeError {
        InvalidPrecisionValue,
        InconsistentCoordinateDimensions,
        InvalidCoordinateValue
    }

    sealed class EncodeResult {
        data class Success(val encodedData: String) : EncodeResult()
        data class Error(val error:EncodeError) : EncodeResult()
    }

    sealed class DecodeToArrayResult {
        data class Success(val lngLatArray: Array<DoubleArray>) : DecodeToArrayResult()
        data class Error(val error:DecodeError) : DecodeToArrayResult()
    }

    sealed class DecodeToGeoJsonResult {
        data class Success(val geojson: String) : DecodeToGeoJsonResult()
        data class Error(val error:DecodeError) : DecodeToGeoJsonResult()
    }

    // The default algorithm is FlexiblePolyline. This was selected as it is the newest and most flexible format
    // of the different decoding types supported.
    private var compressor: DataCompressor = FlexiblePolyline()

    /** Get the currently-selected compression algorithm.
     * @returns The current compression algorithm.
     */
    fun getCompressionAlgorithm(): CompressionAlgorithm {
        return when (compressor) {
            is Polyline5 -> CompressionAlgorithm.Polyline5
            is Polyline6 -> CompressionAlgorithm.Polyline6
            else -> CompressionAlgorithm.FlexiblePolyline
        }
    }

    /** Set the compression algorithm to use for subsequent encode/decode calls.
     * @param compressionType The compression algorithm to use.
     * @throws IllegalArgumentException if an invalid compression algorithm is specified.
     */
    fun setCompressionAlgorithm(compressionType: CompressionAlgorithm = CompressionAlgorithm.FlexiblePolyline) {
        compressor = when (compressionType) {
            CompressionAlgorithm.Polyline5 -> Polyline5()
            CompressionAlgorithm.Polyline6 -> Polyline6()
            CompressionAlgorithm.FlexiblePolyline -> FlexiblePolyline()
        }
    }

    /** Encode the provided array of coordinate values into an encoded string.
     * @remarks
     * This takes in an array of two-dimensional or three-dimensional positions and encodes them into
     * the currently-selected compression format.
     * Example of 2D input data:
     * ```
     *   [ [5.0, 0.0], [10.0, 5.0], [10.0, 10.0], ]
     * ```
     * Example of 3D input data:
     * ```
     *   [ [5.0, 0.0, 200.0], [10.0, 5.0, 200.0], [10.0, 10.0, 205.0], ]
     * ```
     * @param lngLatArray  An array of lng/lat positions to encode. The positions may contain an optional 3rd dimension.
     * @param parameters Optional compression parameters. These are currently only used by the FlexiblePolyline algorithm.
     * @returns An encoded string containing the compressed coordinate values.
     * @throws IllegalArgumentException if the input data contains no coordinate pairs,
     * latitude values outside of -90, 90, longitude values outside of -180, 180,
     * data that isn't 2-dimensional or 3-dimensional, or data that is 3-dimensional with a compressor that doesn't support 3D data.
     */
    fun encodeFromLngLatArray(
        lngLatArray: Array<DoubleArray>,
        parameters: CompressionParameters = CompressionParameters()
    ): EncodeResult {
        return compressor.encodeFromLngLatArray(lngLatArray, parameters)
    }


    /** Decode the provided encoded data string into an array of coordinate values.
     * @remarks
     * Note that this method returns a raw array of coordinate values, which cannot be used as a MapLibre source
     * without first embedding it into a GeoJSON Feature. If you want to add the decoded data as a MapLibre source,
     * use either {@link decodeToLineStringFeature} or {@link decodeToPolygonFeature} instead.
     * Only use this method when you want to use the coordinate data directly.
     * @param compressedData  The encoded data string to decode. The data is expected to have valid lat/lng values.
     * @returns An array of coordinate value arrays.
     * @throws IllegalArgumentException if the encodedData contains invalid characters, no coordinate pairs,
     * latitude values outside of -90, 90, or longitude values outside of -180, 180.
     * @example
     * An example of decoded data:
     * ```
     *   [
     *     [5.0, 0.0],
     *     [10.0, 5.0],
     *     [10.0, 10.0],
     *   ]
     * ```
     */
    fun decodeToLngLatArray(compressedData: String): DecodeToArrayResult {
        return compressor.decodeToLngLatArray(compressedData)
    }

    /** Decode the provided encoded data string into a GeoJSON LineString.
     * @remarks
     * Note that this method returns a LineString, which cannot be used as a MapLibre source without first embedding it
     * into a GeoJSON Feature. If you want to add the LineString as a MapLibre source, use {@link decodeToLineStringFeature} instead.
     * Only use this method when you plan to manipulate the LineString further as opposed to using it directly as a source.
     * @param encodedData  The encoded data string to decode. The data is expected to have a minimum of two
     * coordinate pairs with valid lat/lng values.
     * @returns A GeoJSON LineString representing the decoded data.
     * @throws Error() if the encodedData contains invalid characters, < 2 coordinate pairs,
     * latitude values outside of [-90, 90], or longitude values outside of [-180, 180].
     * @example
     * An example of a decoded LineString:
     * ```json
     * {
     *   "type": "LineString",
     *   "coordinates": [
     *     [5.0, 0.0],
     *     [10.0, 5.0],
     *     [10.0, 10.0],
     *   ]
     * }
     * ```
     */
    fun decodeToLineString(encodedData: String): DecodeToGeoJsonResult {
        return compressor.decodeToLineString(encodedData)
    }

    /** Decode the provided encoded data string into a GeoJSON Polygon.
     * @remarks
     * Note that this method returns a Polygon, which cannot be used as a MapLibre source without first embedding it
     * into a GeoJSON Feature. If you want to add the Polygon as a MapLibre source, use {@link decodeToPolygonFeature} instead.
     * Only use this method when you plan to manipulate the Polygon further as opposed to using it directly as a source.
     * @param encodedData  An array of encoded data strings to decode. This is an array instead of a single string
     * because polygons can consist of multiple rings of compressed data. The first entry will be treated as the
     * outer ring and the remaining entries will be treated as inner rings. Each input ring can be wound either
     * clockwise or counterclockwise; they will get rewound to be GeoJSON-compliant in the output. Each ring is
     * expected to have a minimum of four coordinate pairs with valid lat/lng data, and the last coordinate pair
     * must match the first to make an explicit ring.
     * @returns A GeoJSON Polygon representing the decoded data. The first entry in the output coordinates
     * represents the outer ring and any remaining entries represent inner rings.
     * @throws Error() if the encodedData contains invalid characters, < 4 coordinate pairs, first/last coordinates that
     * aren't approximately equal, latitude values outside of [-90, 90], or longitude values outside of [-180, 180].
     * @example
     * An example of a decoded Polygon:
     * ```json
     * {
     *   "type": "Polygon",
     *   "coordinates": [
     *     [[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]], // outer ring
     *     [[2, 2], [2,  8], [8 , 8 ], [8 , 2], [2, 2]], // inner ring
     *     [[4, 4], [4,  6], [6 , 6 ], [6 , 4], [4, 4]]  // inner ring
     *   ]
     * }
     * ```
     */
     fun decodeToPolygon(encodedData: Array<String>): DecodeToGeoJsonResult {
        return compressor.decodeToPolygon(encodedData)
    }

    /** Decode the provided encoded data string into a GeoJSON Feature containing a LineString.
     * @param encodedData  The encoded data string to decode. The data is expected to have a minimum of two
     * coordinate pairs with valid lat/lng values.
     * @returns A GeoJSON Feature containing a LineString that represents the decoded data.
     * @throws Error() if the encodedData contains invalid characters, < 2 coordinate pairs,
     * latitude values outside of [-90, 90], or longitude values outside of [-180, 180]
     * @example
     * An example of a decoded LineString as a Feature:
     * ```json
     * {
     *   "type": "Feature",
     *   "properties": {},
     *   "geometry": {
     *     "type": "LineString",
     *     "coordinates": [
     *       [5.0, 0.0],
     *       [10.0, 5.0],
     *       [10.0, 10.0],
     *     ]
     *   }
     * }
     * ```
     * The result of this method can be used with MapLibre's `addSource` to add a named data source or embedded directly
     * with MapLibre's `addLayer` to both add and render the result:
     * ```javascript
     * var decodedGeoJSON = polylineDecoder.decodeToLineStringFeature(encodedRoutePolyline);
     * map.addLayer({
     *   id: 'route',
     *   type: 'line',
     *     source: {
     *       type: 'geojson',
     *       data: decodedGeoJSON
     *     },
     *     layout: {
     *       'line-join': 'round',
     *       'line-cap': 'round'
     *     },
     *       paint: {
     *         'line-color': '#3887be',
     *         'line-width': 5,
     *         'line-opacity': 0.75
     *       }
     * });
     * ```
     */
     fun decodeToLineStringFeature(encodedData: String): DecodeToGeoJsonResult {
        return compressor.decodeToLineStringFeature(encodedData)
    }

    /** Decode the provided encoded data string into a GeoJSON Feature containing a Polygon.
     * @param encodedData  An array of encoded data strings to decode. This is an array instead of a single string
     * because polygons can consist of multiple rings of compressed data. The first entry will be treated as the
     * outer ring and the remaining entries will be treated as inner rings. Each input ring can be wound either
     * clockwise or counterclockwise; they will get rewound to be GeoJSON-compliant in the output. Each ring is
     * expected to have a minimum of four coordinate pairs with valid lat/lng data, and the last coordinate pair
     * must match the first to make an explicit ring.
     * @returns A GeoJSON Feature containing a Polygon that represents the decoded data. The first entry in the
     * output coordinates represents the outer ring and any remaining entries represent inner rings.
     * @throws Error() if the encodedData contains invalid characters, < 4 coordinate pairs, first/last coordinates that
     * aren't approximately equal, latitude values outside of [-90, 90], or longitude values outside of [-180, 180].
     * @example
     * An example of a decoded Polygon as a Feature:
     * ```json
     * {
     *   'type': 'Feature',
     *   'properties': {},
     *   'geometry': {
     *     "type": "Polygon",
     *     "coordinates": [
     *       [[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]], // outer ring
     *       [[2, 2], [2,  8], [8 , 8 ], [8 , 2], [2, 2]], // inner ring
     *       [[4, 4], [4,  6], [6 , 6 ], [6 , 4], [4, 4]]  // inner ring
     *     ]
     *   }
     * }
     * ```
     * The result of this method can be used with MapLibre's `addSource` to add a named data source or embedded directly
     * with MapLibre's `addLayer` to both add and render the result:
     * ```javascript
     * var decodedGeoJSON = polylineDecoder.decodeToPolygonFeature(encodedIsolinePolygons);
     * map.addLayer({
     *   id: 'isoline',
     *   type: 'fill',
     *     source: {
     *       type: 'geojson',
     *       data: decodedGeoJSON
     *     },
     *     layout: {},
     *     paint: {
     *       'fill-color': '#FF0000',
     *       'fill-opacity': 0.6
    }
     * });
     * ```
     */
    fun decodeToPolygonFeature(encodedData: Array<String>): DecodeToGeoJsonResult {
        return compressor.decodeToPolygonFeature(encodedData)
    }

}