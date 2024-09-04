package software.amazon.location.polyline

// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

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