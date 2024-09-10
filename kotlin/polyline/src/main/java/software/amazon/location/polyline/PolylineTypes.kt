// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.amazon.location.polyline

/** Defines the default encoding precision for coordinates */
const val DefaultPrecision = 6

/** The version of flexible-polyline that's supported by this implementation */
const val FlexiblePolylineFormatVersion = 1

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
    val precisionLngLat: Int = DefaultPrecision,
    /** The number of decimal places of precision to use for compressing the third dimension of data. */
    val precisionThirdDimension: Int = 0,
    /** The type of third dimension data being encoded - none, level, altitude, or elevation. */
    val thirdDimension: ThirdDimension = ThirdDimension.None
)

// Decode exceptions
class EmptyInputException : Exception("Empty input string")
class InvalidEncodedCharacterException : Exception("Invalid input, the encoded character doesn't exist in the decoding table")
class ExtraContinueBitException : Exception("Invalid encoding, the last block contained an extra 0x20 'continue' bit")
class InvalidHeaderVersionException: Exception("The decoded header has an unknown version number")
class MissingCoordinateDimensionException: Exception("Decoding ended before all the dimensions for a coordinate were decoded")

// Encode exceptions
class InvalidPrecisionValueException : Exception("Invalid precision value, the valid range is 0 - 11")
class InconsistentCoordinateDimensionsException : Exception("All the coordinates need to have the same number of dimensions")
class InvalidCoordinateValueException : Exception("Latitude values need to be in [-90, 90] and longitude values need to be in [-180, 180]")

// GeoJson exceptions
class InvalidLineStringLengthException : Exception("LineString coordinate arrays need at least 2 entries (start, end)")
class InvalidPolygonLengthException : Exception("Polygon coordinate arrays need at least 4 entries (v0, v1, v2, v0)")
class InvalidPolygonClosureException : Exception("Polygons need the first and last coordinate to match")
