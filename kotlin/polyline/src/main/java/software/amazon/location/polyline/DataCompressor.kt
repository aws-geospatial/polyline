// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.amazon.location.polyline

import kotlin.math.abs

// DataCompressor is an abstract base class that defines the interface for
// encoding/decoding compressed coordinate arrays. The coordinate arrays represent either
// LineString ("polyline") or Polygon geometry.
// To make this compressed data easy to use with MapLibre, DataCompressor provides
// methods for decoding the data into different types of GeoJSON outputs:
//  - decodeToLineStringFeature / decodeToPolygonFeature:
//      These produce a GeoJSON Feature object that can be directly passed into MapLibre as a geojson source.
//  - decodeToLineString / decodeToPolygon:
//      These produce a GeoJSON Geometry object that can be manually assembled into a Feature to pass
//      into MapLibre as a geojson source.

// Concrete implementations of this class are expected to implement the following APIs:
// - compressLngLatArray(lngLatArray, compressionParameters) -> compressedData
// - decompressLngLatArray(compressedData) -> [lngLatArray, compressionParameters]

internal open class DataCompressor {
    // Encode an array of LngLat data into a string of compressed data.
    // The coordinates may optionally have a third dimension of data.
    @Throws(Exception::class)
    open fun compressLngLatArray(
        lngLatArray: Array<DoubleArray>,
        parameters: Polyline.CompressionParameters
    ): String {
        return ""
    }

    // Decode a string of compressed data into an array of LngLat data.
    // The coordinates may optionally have a third dimension of data.
    @Throws(Exception::class)
    open fun decompressLngLatArray(
        compressedData: String
    ): Pair<Array<DoubleArray>, Polyline.CompressionParameters> {
        return Pair(arrayOf(doubleArrayOf()), Polyline.CompressionParameters())
    }

    // Helper method to determine whether the polygon is wound in CCW (counterclockwise) or CW (clockwise) order.
    private fun polygonIsCounterClockwise(lngLatArray: Array<DoubleArray>): Boolean {
        // If the data isn't a polygon, then it can't be a counter-clockwise polygon.
        // (A polygon requires at least 3 unique points and a 4th last point that matches the first)
        if (lngLatArray.size < 4) {
            return false
        }

        // To determine if a polygon has a counterclockwise winding order, all we need to
        // do is calculate the area of the polygon.
        // If the area is positive, it's counterclockwise.
        // If the area is negative, it's clockwise.
        // If the area is 0, it's neither, so we'll still return false for counterclockwise.
        // This implementation currently assumes that only 2D winding order is important and
        // ignores any optional third dimension.
        var area = 0.0
        for (idx in 0 until lngLatArray.size - 1) {
            val x1 = lngLatArray[idx][0]
            val y1 = lngLatArray[idx][1]
            val x2 = lngLatArray[idx + 1][0]
            val y2 = lngLatArray[idx + 1][1]
            area += x1 * y2 - x2 * y1
        }
        // If we needed the actual area value, we should divide by 2 here, but since we only
        // need to check the sign, we can skip the division.
        return area > 0
    }

    // Helper method to determine if two LngLat positions are equivalent within a given epsilon range.
    private fun positionsAreEquivalent(pos1: DoubleArray, pos2: DoubleArray): Boolean {
        // Verify that the two positions are equal within an epsilon.
        // This epsilon was picked because most compressed data uses <= 6 digits of precision,
        // so this epsilon is large enough to detect intentionally different data, and small
        // enough to detect equivalency for values that just have compression artifact drift.
        val epsilon = 0.000001

        if (pos1.size != pos2.size) {
            return false
        }

        // Loop through longitude, latitude, and optional 3rd dimension to make sure each one is equivalent.
        for (idx in pos1.indices) {
            if (abs(pos1[idx] - pos2[idx]) >= epsilon) {
                return false
            }
        }

        return true
    }

    @Throws(DecodeException::class)
    private fun decodeLineString(compressedData: String): Pair<String, Polyline.CompressionParameters> {
        val (decodedLine, compressionParameters) = try {
            decompressLngLatArray(compressedData)
        } catch (e: Exception) {
            throw e
        }

        // Validate that the result is a valid GeoJSON LineString per the RFC 7946 GeoJSON spec:
        // "The 'coordinates' member is an array of two or more positions"
        if (decodedLine.size < 2) {
            throw DecodeException("LineString coordinate arrays need at least 2 entries (start, end)", Polyline.DecodeError.InvalidLineStringLength)
        }

        return Pair(
            """
            {
                "type": "LineString",
                "coordinates": ${decodedLine.contentDeepToString()}
            }
            """,
            compressionParameters
        )
    }

    @Throws(DecodeException::class)
    private fun decodePolygon(compressedData: Array<String>): Pair<String, Polyline.CompressionParameters> {
        val decodedPolygon: MutableList<Array<DoubleArray>> = mutableListOf()
        var shouldBeCounterclockwise = true // The first ring of a polygon should be counterclockwise
        var compressionParameters = Polyline.CompressionParameters()

        for (ring in compressedData) {
            val (decodedRing, ringCompressionParameters) = try {
                decompressLngLatArray(ring)
            } catch (e: Exception) {
                throw e
            }

            // Validate that the result is a valid GeoJSON Polygon linear ring per the RFC 7946 GeoJSON spec.

            // 1. "A linear ring is a closed LineString with 4 or more positions."
            if (decodedRing.size < 4) {
                throw DecodeException("Polygon coordinate arrays need at least 4 entries (v0, v1, v2, v0)", Polyline.DecodeError.InvalidPolygonLength)
            }

            // 2. "The first and last positions are equivalent, and they MUST contain identical values;
            //     their representation SHOULD also be identical."
            // We validate equivalency within a small epsilon.
            if (!positionsAreEquivalent(decodedRing[0], decodedRing[decodedRing.size - 1])) {
                throw DecodeException("Polygons need the first and last coordinate to match", Polyline.DecodeError.InvalidPolygonClosure)
            }

            // 3. "A linear ring MUST follow the right-hand rule with respect to the area it bounds,
            //     i.e., exterior rings are counterclockwise, and holes are clockwise."
            // "Note: the [GJ2008] specification did not discuss linear ring winding
            //    order.  For backwards compatibility, parsers SHOULD NOT reject
            //    Polygons that do not follow the right-hand rule."
            // "For Polygons with more than one of these rings, the first MUST be
            //       the exterior ring, and any others MUST be interior rings.  The
            //       exterior ring bounds the surface, and the interior rings (if
            //       present) bound holes within the surface."

            // With all this taken together, we should enforce the winding order as opposed to just
            // validating it.
            if (shouldBeCounterclockwise != polygonIsCounterClockwise(decodedRing)) {
                decodedRing.reverse()
            }

            decodedPolygon.add(decodedRing)

            // Set compressionParameter metadata to whatever the last compression parameters were that were used.
            // This may need to have more complicated logic at some point if different rings have different compression
            // parameters and we want to capture all of them.
            compressionParameters = ringCompressionParameters

            // All rings after the first should be clockwise.
            shouldBeCounterclockwise = false
        }

        val polygonCoordinates: String = decodedPolygon.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ", "
        ) { outerArray ->
            outerArray.contentDeepToString()
        }

        return Pair(
            """
            {
                "type": "Polygon",
                "coordinates": $polygonCoordinates
            }
            """,
            compressionParameters
        )
    }

    private fun compressionParametersToGeoJsonProperties(
        parameters: Polyline.CompressionParameters
    ): String {
        return when (parameters.thirdDimension) {
            Polyline.ThirdDimension.Level -> """
            {
                "precision": ${parameters.precisionLngLat},
                "thirdDimensionPrecision": ${parameters.precisionThirdDimension},
                "thirdDimensionType": "level"
            }
            """
            Polyline.ThirdDimension.Elevation -> """
            {
                "precision": ${parameters.precisionLngLat},
                "thirdDimensionPrecision": ${parameters.precisionThirdDimension},
                "thirdDimensionType": "elevation"
            }
            """
            Polyline.ThirdDimension.Altitude -> """
            {
                "precision": ${parameters.precisionLngLat},
                "thirdDimensionPrecision": ${parameters.precisionThirdDimension},
                "thirdDimensionType": "altitude"
            }
            """
            else -> """
            {
                "precision": ${parameters.precisionLngLat}
            }
            """
        }
    }

    fun encodeFromLngLatArray(
        lngLatArray: Array<DoubleArray>,
        parameters: Polyline.CompressionParameters
    ): Polyline.EncodeResult {
        return try {
            Polyline.EncodeResult.Success(compressLngLatArray(lngLatArray, parameters))
        } catch (e: EncodeException) {
            Polyline.EncodeResult.Error(e.err)
        }
    }

    fun decodeToLngLatArray(compressedData: String): Polyline.DecodeToArrayResult {
        val (decodedLngLatArray, _) = try {
            decompressLngLatArray(compressedData)
        } catch (e: DecodeException) {
            return Polyline.DecodeToArrayResult.Error(e.err)
        }
        return Polyline.DecodeToArrayResult.Success(decodedLngLatArray)
    }

    fun decodeToLineString(compressedData: String): Polyline.DecodeToGeoJsonResult {
        val (lineString, _) = try {
            decodeLineString(compressedData)
        } catch (e: DecodeException) {
            return Polyline.DecodeToGeoJsonResult.Error(e.err)
        }
        return Polyline.DecodeToGeoJsonResult.Success(lineString)
    }

    fun decodeToPolygon(compressedData: Array<String>): Polyline.DecodeToGeoJsonResult {
        val (polygon, _) = try {
            decodePolygon(compressedData)
        } catch (e: DecodeException) {
            return Polyline.DecodeToGeoJsonResult.Error(e.err)
        }
        return Polyline.DecodeToGeoJsonResult.Success(polygon)
    }

    fun decodeToLineStringFeature(compressedData: String): Polyline.DecodeToGeoJsonResult {
        val (lineString, compressionParameters) = try {
            decodeLineString(compressedData)
        } catch (e: DecodeException) {
            return Polyline.DecodeToGeoJsonResult.Error(e.err)
        }
        return Polyline.DecodeToGeoJsonResult.Success("""
        {
            "type": "Feature",
            "geometry": $lineString,
            "properties": ${compressionParametersToGeoJsonProperties(parameters = compressionParameters)}
        }
        """)
    }

    fun decodeToPolygonFeature(compressedData: Array<String>): Polyline.DecodeToGeoJsonResult {
        val (polygon, compressionParameters) = try {
            decodePolygon(compressedData)
        } catch (e: DecodeException) {
            return Polyline.DecodeToGeoJsonResult.Error(e.err)
        }
        return Polyline.DecodeToGeoJsonResult.Success("""
        {
            "type": "Feature",
            "geometry": $polygon,
            "properties": ${compressionParametersToGeoJsonProperties(parameters = compressionParameters)}
        }
        """)
    }

}
