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
  CompressionParameters,
  defaultCompressionParameters,
} from "./polyline-types";
import { PolylineEncoder } from "./polyline-encoder";
import { PolylineDecoder } from "./polyline-decoder";

// FlexiblePolyline encodes/decodes compressed data using the Flexible Polyline
// encoding ( https://github.com/heremaps/flexible-polyline ), which is a variant of
// the Encoded Polyline Algorithm Format. The algorithm handles both 2D and 3D data.
export class FlexiblePolyline extends DataCompressor {
  readonly DataContainsHeader = true;
  readonly FlexPolylineEncodingTable =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
  // The lookup table contains conversion values for ASCII characters 0-127.
  // Only the characters listed in the encoding table will contain valid
  // decoding entries below.
  readonly FlexPolylineDecodingTable = [
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, 52, 53, 54, 55, 56, 57, 58, 59, 60,
    61, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
    13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, 63, -1,
    26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44,
    45, 46, 47, 48, 49, 50, 51,
  ];
  readonly encoder = new PolylineEncoder(
    this.FlexPolylineEncodingTable,
    this.DataContainsHeader,
  );
  readonly decoder = new PolylineDecoder(
    this.FlexPolylineDecodingTable,
    this.DataContainsHeader,
  );

  constructor() {
    super();
  }

  encodeFromLngLatArray(
    lngLatArray: Array<Array<number>>,
    parameters: CompressionParameters,
  ): string {
    const fullParameters = { ...defaultCompressionParameters, ...parameters };

    return this.encoder.encode(
      lngLatArray,
      fullParameters.precisionLngLat,
      fullParameters.thirdDimension,
      fullParameters.precisionThirdDimension,
    );
  }

  decodeToLngLatArrayPrivate(
    polyline: string,
  ): [Array<Array<number>>, CompressionParameters] {
    const [lngLatArray, header] = this.decoder.decode(polyline);

    return [lngLatArray, header];
  }
}

abstract class EncodedPolyline extends DataCompressor {
  readonly precision: number;
  readonly PolylineEncodingTable =
    "?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
  // The lookup table contains conversion values for ASCII characters 0-127.
  // Only the characters listed in the encoding table will contain valid
  // decoding entries below.
  readonly PolylineDecodingTable = [
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
    15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33,
    34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52,
    53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, -1,
  ];
  readonly encoder = new PolylineEncoder(this.PolylineEncodingTable, false);
  readonly decoder = new PolylineDecoder(this.PolylineDecodingTable, false);

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
    const [lngLatArray, header] = this.decoder.decode(
      compressedData,
      this.precision,
    );
    return [lngLatArray, { precisionLngLat: header.precisionLngLat }];
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
