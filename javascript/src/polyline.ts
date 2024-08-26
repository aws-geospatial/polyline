// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// This class implements both the Encoded Polyline Algorithm Format
// (https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
// and the Flexible-Polyline variation of the algorithm (https://github.com/heremaps/flexible-polyline).

// This implementation has two differences to improve usability:
// - It uses well-defined rounding to ensure deterministic results across all programming languages.
// - It caps the max encoding/decoding precision to 11 decimal places (1 micrometer), because 15 places will
//   lose precision when using 64-bit floating-point numbers.

import { DataCompressor } from "./data-compressor";
import {
  ThirdDimension,
  CompressionParameters,
  defaultCompressionParameters,
} from "./polyline-types";
import { PolylineEncoder } from "./polyline-encoder";
import { PolylineDecoder } from "./polyline-decoder";

// FlexiblePolyline encodes/decodes compressed data using the Flexible Polyline
// encoding ( https://github.com/heremaps/flexible-polyline ), which is a variant of
// the Encoded Polyline Algorithm Format. The algorithm handles both 2D and 3D data.
export class FlexiblePolyline extends DataCompressor {
  readonly FLEXPOLYLINE_ENCODING_TABLE =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
  readonly encoder = new PolylineEncoder(
    this.FLEXPOLYLINE_ENCODING_TABLE,
    true,
  );
  readonly decoder = new PolylineDecoder();

  constructor() {
    super();
  }

  encodeFromLngLatArray(
    lngLatArray: Array<Array<number>>,
    parameters: CompressionParameters,
  ): string {
    const fullParameters = { ...defaultCompressionParameters, ...parameters };

    // Validate parameters.
    if (
      fullParameters.precisionLngLat < 0 ||
      fullParameters.precisionLngLat > 15
    ) {
      throw new Error(
        "Invalid CompressionParameters for FlexiblePolyline: precisionLngLat must be between 0 and 15.",
      );
    }
    if (
      fullParameters.precisionThirdDimension < 0 ||
      fullParameters.precisionThirdDimension > 15
    ) {
      throw new Error(
        "Invalid CompressionParameters for FlexiblePolyline: precisionThirdDimension must be between 0 and 15.",
      );
    }

    // The underlying algorithm allows for more third dimension types than just Altitude and Elevation, but since
    // those are the only acceptable types in the GeoJSON spec, that's all we'll support here.
    switch (fullParameters.thirdDimension) {
      case ThirdDimension.Altitude:
        return this.encoder.encode(
          lngLatArray,
          fullParameters.precisionLngLat,
          ThirdDimension.Altitude,
          fullParameters.precisionThirdDimension,
        );
      case ThirdDimension.Elevation:
        return this.encoder.encode(
          lngLatArray,
          fullParameters.precisionLngLat,
          ThirdDimension.Elevation,
          fullParameters.precisionThirdDimension,
        );
      default:
        return this.encoder.encode(lngLatArray, fullParameters.precisionLngLat);
    }
  }

  decodeToLngLatArrayPrivate(
    polyline: string,
  ): [Array<Array<number>>, CompressionParameters] {
    const decodedLine = this.decoder.decode(polyline, true);
    const thirdDimension = decodedLine.thirdDim as ThirdDimension;

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

abstract class EncodedPolyline extends DataCompressor {
  readonly precision: number;
  readonly POLYLINE_ENCODING_TABLE =
    "?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
  readonly encoder = new PolylineEncoder(this.POLYLINE_ENCODING_TABLE, false);
  readonly decoder = new PolylineDecoder();

  constructor(precision: number) {
    super();
    this.precision = precision;
  }

  validateInput(compressedData: string) {
    // The compressed data input for Polyline5 / Polyline6 is expected to be base64-encoded into the
    // ASCII range of 63-126. Verify that the input data falls within that range.

    for (let i = 0; i < compressedData.length; i++) {
      const charCode = compressedData.charCodeAt(i);
      if (charCode < 63 || charCode > 126) {
        throw new Error(
          `Invalid input. Compressed data must have ASCII values of 63-126. input[${i}] = '${compressedData.charAt(i)}' (ASCII ${charCode}).`,
        );
      }
    }
  }

  encodeFromLngLatArray(
    lngLatArray: Array<Array<number>>,
    /* parameters: CompressionParameters, */
  ): string {
    if (lngLatArray[0].length != 2) {
      throw Error(
        "Invalid input. 3D data was provided but this data compressor does not support 3D data.",
      );
    }

    return this.encoder.encode(lngLatArray, this.precision);
  }

  decodeToLngLatArrayPrivate(
    compressedData: string,
  ): [Array<Array<number>>, CompressionParameters] {
    this.validateInput(compressedData);
    const result = this.decoder.decode(compressedData, false, this.precision);
    return [result.polyline, { precisionLngLat: this.precision }];
  }
}

// Polyline5 and Polyline6 encodes/decodes compressed data with 5 or 6 bits of precision respectively.
// While the underlying Polyline implementation allows for an arbitrary
// number of bits of precision to be encoded / decoded, location service providers seem
// to only choose 5 or 6 bits of precision, so those are the two algorithms that we'll explicitly offer here.

export class Polyline5 extends EncodedPolyline {
  constructor() {
    super(5);
  }
}

export class Polyline6 extends EncodedPolyline {
  constructor() {
    super(6);
  }
}
