// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import {
  CompressionParameters,
  DataCompressor,
  ThirdDimension,
} from "../data-compressor";
import {
  ABSENT,
  ALTITUDE,
  ELEVATION,
  decode as flexPolylineDecode,
  encode as flexPolylineEncode,
} from "@here/flexpolyline";

// FlexiblePolyline encodes/decodes compressed data using the Flexible Polyline
// encoding ( https://github.com/heremaps/flexible-polyline ), which is a variant of
// the Encoded Polyline Algorithm Format. The algorithm handles both 2D and 3D data.
export class FlexiblePolyline extends DataCompressor {
  supports3D(): boolean {
    return true;
  }

  encodeFromLatLngArray(
    latLngArray: Array<Array<number>>,
    parameters: CompressionParameters,
  ): string {
    // Validate parameters.
    if (parameters.precisionLngLat < 0 || parameters.precisionLngLat > 15) {
      throw new Error(
        "Invalid CompressionParameters for FlexiblePolyline: precisionLngLat must be between 0 and 15.",
      );
    }
    if (
      parameters.precisionThirdDimension < 0 ||
      parameters.precisionThirdDimension > 15
    ) {
      throw new Error(
        "Invalid CompressionParameters for FlexiblePolyline: precisionThirdDimension must be between 0 and 15.",
      );
    }

    // The underlying algorithm allows for more third dimension types than just Altitude and Elevation, but since
    // those are the only acceptable types in the GeoJSON spec, that's all we'll support here.
    switch (parameters.thirdDimension) {
      case ThirdDimension.Altitude:
        return flexPolylineEncode({
          polyline: latLngArray,
          precision: parameters.precisionLngLat,
          thirdDim: ALTITUDE,
          thirdDimPrecision: parameters.precisionThirdDimension,
        });
      case ThirdDimension.Elevation:
        return flexPolylineEncode({
          polyline: latLngArray,
          precision: parameters.precisionLngLat,
          thirdDim: ELEVATION,
          thirdDimPrecision: parameters.precisionThirdDimension,
        });
      default:
        return flexPolylineEncode({
          polyline: latLngArray,
          precision: parameters.precisionLngLat,
        });
    }
  }

  decodeToLatLngArray(
    polyline: string,
  ): [Array<Array<number>>, CompressionParameters] {
    const decodedLine = flexPolylineDecode(polyline);
    let thirdDimension: ThirdDimension;
    switch (decodedLine.thirdDim) {
      case ALTITUDE:
        thirdDimension = ThirdDimension.Altitude;
        break;
      case ELEVATION:
        thirdDimension = ThirdDimension.Elevation;
        break;
      case ABSENT:
        thirdDimension = ThirdDimension.None;
        break;
      default:
        throw Error("Unsupported/invalid third dimension type.");
    }

    return [
      decodedLine.polyline,
      {
        precisionLngLat: decodedLine.precision,
        precisionThirdDimension: decodedLine.thirdDimPrecision,
        thirdDimension: thirdDimension,
      },
    ];
  }
}
