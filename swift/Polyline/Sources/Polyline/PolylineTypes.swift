// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import Foundation

/** Defines the default encoding precision for coordinates */
public let DefaultPrecision = 6;

/** The version of flexible-polyline that's supported by this implementation */
let FlexiblePolylineFormatVersion = 1;

/** Defines the set of compression algorithms that are supported by this library. */
public enum CompressionAlgorithm {
    /** Encoder/decoder for the [Flexible Polyline](https://github.com/heremaps/flexible-polyline) format. */
    case FlexiblePolyline
    /** Encoder/decoder for the [Encoded Polyline Algorithm Format](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
    * with 5 bits of precision.
    */
    case Polyline5
    /** Encoder/decoder for the [Encoded Polyline Algorithm Format](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
    * with 6 bits of precision.
    */
    case Polyline6
}

/** Defines how to interpret a third dimension value if it exists. */
public enum ThirdDimension:Int {
    /** No third dimension specified */
    case None = 0
    /** Third dimension is level */
    case Level = 1
    /** Third dimension is altitude (height above the Earth's surface) */
    case Altitude = 2
    /** Third dimension is elevation (height of the Earth's surface relative to the reference geoid) */
    case Elevation = 3
}

/** The optional set of parameters for encoding a set of LngLat coordinates.
 * Currently, only the FlexiblePolyline algorithm supports these parameters. The Polyline5 / Polyline6
 * algorithms ignore them, as they don't support 3D data and we've defined them to use
 * a fixed precision value.
 */
public struct CompressionParameters {
    /** The number of decimal places of precision to use for compressing longitude and latitude.
    */
    public let precisionLngLat: Int;
    /** The number of decimal places of precision to use for compressing the third dimension of data.
    */
    public let precisionThirdDimension: Int;
    /** The type of third dimension data being encoded - none, level, altitude, or elevation.
    */
    public let thirdDimension: ThirdDimension;
    
    public init(precisionLngLat: Int = DefaultPrecision, precisionThirdDimension: Int = 0, thirdDimension: ThirdDimension = ThirdDimension.None) {
        self.precisionLngLat = precisionLngLat;
        self.precisionThirdDimension = precisionThirdDimension;
        self.thirdDimension = thirdDimension;
    }
};


public enum DecodeError: Error {
    // Empty input string is considered an error.
    case emptyInput
    // Invalid input, the encoded character doesn't exist in the decoding table.
    case invalidEncodedCharacter
    // Invalid encoding, the last block contained an extra 0x20 'continue' bit.
    case extraContinueBit
    // The decoded header has an unknown version number.
    case invalidHeaderVersion
    // The decoded coordinate has invalid lng/lat values.
    case invalidCoordinateValue
    // Decoding ended before all the dimensions for a coordinate were decoded.
    case missingCoordinateDimension
};


public enum EncodeError: Error {
    // Invalid precision value, the valid range is 0 - 11.
    case invalidPrecisionValue
    // All the coordinates need to have the same number of dimensions.
    case inconsistentCoordinateDimensions
    // Latitude values need to be in [-90, 90] and longitude values need to be in [-180, 180]
    case invalidCoordinateValue
};


public enum GeoJsonError: Error {
    // LineString coordinate arrays need at least 2 entries (start, end)
    case invalidLineStringLength
    // Polygon coordinate arrays need at least 4 entries (v0, v1, v2, v0)
    case invalidPolygonLength
    // Polygons need the first and last coordinate to match
    case invalidPolygonClosure
}
