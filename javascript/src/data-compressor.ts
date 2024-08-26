// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { LineString, Polygon, Feature, GeoJsonProperties } from "geojson";
import {
  ThirdDimension,
  CompressionParameters,
  defaultCompressionParameters,
} from "./polyline-types";

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

export abstract class DataCompressor {
  // Encode an array of LngLat data into a string of compressed data. The coordinates may optionally have a third
  // dimension of data.
  abstract encodeFromLngLatArray(
    lngLatArray: Array<Array<number>>,
    parameters: CompressionParameters,
  ): string;

  // Decode a string of compressed data into an array of LatLng data. The coordinates may optionally have a third
  // dimension of data.
  // All of the existing algorithms specifically decode to LatLng, which is why we've made that a part of
  // API expectation here. The results get flipped later into LngLat when assembling into GeoJSON.
  protected abstract decodeToLngLatArrayPrivate(
    compressedData: string,
  ): [Array<Array<number>>, CompressionParameters];

  // Helper method to determine whether the polygon is wound in CCW (counterclockwise) or CW (clockwise) order.
  private polygonIsCounterClockwise(
    lngLatArray: Array<Array<number>>,
  ): boolean {
    // If the data isn't a polygon, then it can't be a counter-clockwise polygon.
    // (A polygon requires at least 3 unique points and a 4th last point that matches the first)
    if (lngLatArray.length < 4) {
      return false;
    }

    // To determine if a polygon has a counterclockwise winding order, all we need to
    // do is calculate the area of the polygon.
    // If the area is positive, it's counterclockwise.
    // If the area is negative, it's clockwise.
    // If the area is 0, it's neither, so we'll still return false for counterclockwise.
    // This implementation currently assumes that only 2D winding order is important and
    // ignores any optional third dimension.
    let area = 0;
    for (let idx = 0; idx < lngLatArray.length - 1; idx++) {
      const x1 = lngLatArray[idx][0];
      const y1 = lngLatArray[idx][1];
      const x2 = lngLatArray[idx + 1][0];
      const y2 = lngLatArray[idx + 1][1];
      area += x1 * y2 - x2 * y1;
    }
    // If we needed the actual area value, we should divide by 2 here, but since we only
    // need to check the sign, we can skip the division.
    return area > 0;
  }

  // Helper method to determine if two LngLat positions are equivalent within a given epsilon range.
  private positionsAreEquivalent(
    pos1: Array<number>,
    pos2: Array<number>,
  ): boolean {
    // Verify that the two positions are equal within an epsilon.
    // This epsilon was picked because most compressed data uses <= 6 digits of precision,
    // so this epsilon is large enough to detect intentionally different data, and small
    // enough to detect equivalency for values that just have compression artifact drift.
    const epsilon = 0.000001;
    if (pos1.length != pos2.length) {
      return false;
    }
    // Loop through longitude, latitude, and optional 3rd dimension to make sure each one is equivalent.
    for (let idx = 0; idx < pos1.length; idx++) {
      if (Math.abs(pos1[idx] - pos2[idx]) >= epsilon) {
        return false;
      }
    }
    return true;
  }

  private decodeLineString(
    compressedData: string,
  ): [LineString, CompressionParameters] {
    const [decodedLine, compressionParameters] =
      this.decodeToLngLatArrayPrivate(compressedData);
    // Validate that the result is a valid GeoJSON LineString per the RFC 7946 GeoJSON spec:
    // "The 'coordinates' member is an array of two or more positions"
    if (decodedLine.length < 2) {
      throw Error(
        `Invalid LineString. LineStrings must contain 2 or more positions. It contains ${decodedLine.length} positions.`,
      );
    }
    return [
      {
        type: "LineString",
        coordinates: decodedLine,
      },
      compressionParameters,
    ];
  }

  private decodePolygon(
    compressedData: Array<string>,
  ): [Polygon, CompressionParameters] {
    const decodedPolygon = [];
    let shouldBeCounterclockwise = true; // The first ring of a polygon should be counterclockwise
    let compressionParameters: CompressionParameters = {};
    for (const ring of compressedData) {
      const [decodedRing, ringCompressionParameters] =
        this.decodeToLngLatArrayPrivate(ring);

      // Validate that the result is a valid GeoJSON Polygon linear ring per the RFC 7946 GeoJSON spec.

      // 1. "A linear ring is a closed LineString with 4 or more positions."
      if (decodedRing.length < 4) {
        throw Error(
          `Invalid polygon. Polygons must contain 4 or more positions. It contains ${decodedRing.length} positions. Consider decoding as a LineString.`,
        );
      }

      // 2. "The first and last positions are equivalent, and they MUST contain identical values;
      //     their representation SHOULD also be identical."
      // We validate equivalency within a small epsilon.
      if (
        !this.positionsAreEquivalent(
          decodedRing[0],
          decodedRing[decodedRing.length - 1],
        )
      ) {
        throw Error(
          `Invalid polygon. The first and last positions must contain identical values. The values are ${decodedRing[0]}, ${decodedRing.at(-1)}. Consider decoding as a LineString.`,
        );
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
      if (
        shouldBeCounterclockwise != this.polygonIsCounterClockwise(decodedRing)
      ) {
        decodedRing.reverse();
      }

      decodedPolygon.push(decodedRing);

      // Set compressionParameter metadata to whatever the last compression parameters were that were used.
      // This may need to have more complicated logic at some point if different rings have different compression
      // parameters and we want to capture all of them.
      compressionParameters = ringCompressionParameters;

      // All rings after the first should be clockwise.
      shouldBeCounterclockwise = false;
    }
    return [
      {
        type: "Polygon",
        coordinates: decodedPolygon,
      },
      compressionParameters,
    ];
  }

  private compressionParametersToGeoJsonProperties(
    parameters: CompressionParameters,
  ): GeoJsonProperties {
    switch (parameters.thirdDimension) {
      case ThirdDimension.Elevation:
        return {
          precision: parameters.precisionLngLat,
          thirdDimensionPrecision: parameters.precisionThirdDimension,
          thirdDimensionType: "elevation",
        };
      case ThirdDimension.Altitude:
        return {
          precision: parameters.precisionLngLat,
          thirdDimensionPrecision: parameters.precisionThirdDimension,
          thirdDimensionType: "altitude",
        };
      default:
        return {
          precision: parameters.precisionLngLat,
        };
    }
  }

  decodeToLngLatArray(compressedData: string): Array<Array<number>> {
    const [decodedLngLatArray] =
      this.decodeToLngLatArrayPrivate(compressedData);

    return decodedLngLatArray;
  }

  decodeToLineString(compressedData: string): LineString {
    const [lineString] = this.decodeLineString(compressedData);
    return lineString;
  }

  decodeToPolygon(compressedData: Array<string>): Polygon {
    const [polygon] = this.decodePolygon(compressedData);
    return polygon;
  }

  decodeToLineStringFeature(compressedData: string): Feature {
    const [lineString, compressionParameters] =
      this.decodeLineString(compressedData);
    return {
      type: "Feature",
      geometry: lineString,
      properties: this.compressionParametersToGeoJsonProperties(
        compressionParameters,
      ),
    };
  }

  decodeToPolygonFeature(compressedData: Array<string>): Feature {
    const [polygon, compressionParameters] = this.decodePolygon(compressedData);
    return {
      type: "Feature",
      geometry: polygon,
      properties: this.compressionParametersToGeoJsonProperties(
        compressionParameters,
      ),
    };
  }
}
