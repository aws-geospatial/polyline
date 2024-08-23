// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

/** Defines the default encoding precision for coordinates */
export const DefaultPrecision = 6;

/** The version of flexible-polyline that's supported by this implementation */
export const FlexiblePolylineFormatVersion = 1;

/** Defines the set of compression algorithms that are supported by this library. */
export enum CompressionAlgorithm {
    /** Encoder/decoder for the [Flexible Polyline](https://github.com/heremaps/flexible-polyline) format. */
    FlexiblePolyline,
    /** Encoder/decoder for the [Encoded Polyline Algorithm Format](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
     * with 5 bits of precision.
     */
    Polyline5,
    /** Encoder/decoder for the [Encoded Polyline Algorithm Format](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
     * with 6 bits of precision.
     */
    Polyline6,
}

/** Defines how to interpret a third dimension value if it exists. */
export enum ThirdDimension {
    /** No third dimension specified */
    None,
    /** Third dimension is level */
    Level = 1,
    /** Third dimension is altitude (height above the Earth's surface) */
    Altitude = 2,
    /** Third dimension is elevation (height of the Earth's surface relative to the reference geoid) */
    Elevation = 3,
}

/** The optional set of parameters for encoding a set of LngLat coordinates.
 * Currently, only the FlexiblePolyline algorithm supports these parameters. The Polyline5 / Polyline6
 * algorithms ignore them, as they don't support 3D data and we've defined them to use
 * a fixed precision value.
 */
export type CompressionParameters = {
    /** The number of decimal places of precision to use for compressing longitude and latitude.
     */
    precisionLngLat?: number;
    /** The number of decimal places of precision to use for compressing the third dimension of data.
     */
    precisionThirdDimension?: number;
    /** The type of third dimension data being encoded - none, altitude, or elevation.
     */
    thirdDimension?: ThirdDimension;
};

export const defaultCompressionParameters = {
    precisionLngLat: DefaultPrecision,
    precisionThirdDimension: DefaultPrecision,
    thirdDimension: ThirdDimension.None,
};
