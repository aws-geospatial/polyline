// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import {
  ThirdDimension,
  CompressionParameters,
  DataCompressor,
} from "./data-compressor";
import { FlexiblePolyline } from "./algorithms/flexible-polyline";
import { Polyline5, Polyline6 } from "./algorithms/polyline";

import { LineString, Polygon, Feature } from "geojson";

export { ThirdDimension, CompressionParameters };

// The default algorithm is FlexiblePolyline. This was selected as it is the newest and most flexible format
// of the different decoding types supported.
let compressor: DataCompressor = new FlexiblePolyline();

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

/** Get the currently-selected compression algorithm.
 * @returns The current compression algorithm.
 */
export function getCompressionAlgorithm(): CompressionAlgorithm {
  if (compressor instanceof FlexiblePolyline) {
    return CompressionAlgorithm.FlexiblePolyline;
  }
  if (compressor instanceof Polyline5) {
    return CompressionAlgorithm.Polyline5;
  }
  if (compressor instanceof Polyline6) {
    return CompressionAlgorithm.Polyline6;
  }
  throw new Error("Invalid polyline compression algorithm.");
}

/** Set the compression algorithm to use for subsequent encode/decode calls.
 * @param compressionType The compression algorithm to use.
 * @throws Error() if an invalid compression algorithm is specified.
 */
export function setCompressionAlgorithm(compressionType: CompressionAlgorithm) {
  switch (compressionType) {
    case CompressionAlgorithm.FlexiblePolyline: {
      if (!(compressor instanceof FlexiblePolyline)) {
        compressor = new FlexiblePolyline();
      }
      break;
    }
    case CompressionAlgorithm.Polyline5: {
      if (!(compressor instanceof Polyline5)) {
        compressor = new Polyline5();
      }
      break;
    }
    case CompressionAlgorithm.Polyline6: {
      if (!(compressor instanceof Polyline6)) {
        compressor = new Polyline6();
      }
      break;
    }
    default: {
      throw new Error("Invalid polyline compression algorithm.");
    }
  }
}

/** Encode the provided array of coordinate values into an encoded string.
 * @remarks
 * This takes in an array of two-dimensional or three-dimensional positions and encodes them into
 * the currently-selected compression format.
 * Example of 2D input data:
 * ```typescript
 *   [ [5.0, 0.0], [10.0, 5.0], [10.0, 10.0], ]
 * ```
 * Example of 3D input data:
 * ```typescript
 *   [ [5.0, 0.0, 200.0], [10.0, 5.0, 200.0], [10.0, 10.0, 205.0], ]
 * ```
 * @param lngLatArray  An array of lng/lat positions to encode. The positions may contain an optional 3rd dimension.
 * @param parameters Optional compression parameters. These are currently only used by the FlexiblePolyline algorithm.
 * @returns An encoded string containing the compressed coordinate values.
 * @throws Error() if the input data contains no coordinate pairs,
 * latitude values outside of [-90, 90], longitude values outside of [-180, 180],
 * data that isn't 2-dimensional or 3-dimensional, or data that is 3-dimensional with a compressor that doesn't support 3D data.
 */
export function encodeFromLngLatArray(
  lngLatArray: Array<Array<number>>,
  parameters: CompressionParameters = {},
): string {
  return compressor.encodeFromLngLatArray(lngLatArray, parameters);
}

/** Decode the provided encoded data string into an array of coordinate values.
 * @remarks
 * Note that this method returns a raw array of coordinate values, which cannot be used as a MapLibre source
 * without first embedding it into a GeoJSON Feature. If you want to add the decoded data as a MapLibre source,
 * use either {@link decodeToLineStringFeature} or {@link decodeToPolygonFeature} instead.
 * Only use this method when you want to use the coordinate data directly.
 * @param compressedData  The encoded data string to decode. The data is expected to have valid lat/lng values.
 * @returns An array of coordinate value arrays.
 * @throws Error() if the encodedData contains invalid characters, no coordinate pairs,
 * latitude values outside of [-90, 90], or longitude values outside of [-180, 180].
 * @example
 * An example of decoded data:
 * ```typescript
 *   [
 *     [5.0, 0.0],
 *     [10.0, 5.0],
 *     [10.0, 10.0],
 *   ]
 * ```
 */
export function decodeToLngLatArray(compressedData: string): Array<Array<number>> {
  return compressor.decodeToLngLatArray(compressedData);
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
export function decodeToLineString(encodedData: string): LineString {
  return compressor.decodeToLineString(encodedData);
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
export function decodeToPolygon(encodedData: Array<string>): Polygon {
  return compressor.decodeToPolygon(encodedData);
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
export function decodeToLineStringFeature(encodedData: string): Feature {
  return compressor.decodeToLineStringFeature(encodedData);
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
export function decodeToPolygonFeature(encodedData: Array<string>): Feature {
  return compressor.decodeToPolygonFeature(encodedData);
}
