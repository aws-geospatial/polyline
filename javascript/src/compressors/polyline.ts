// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// This class implements the Encoded Polyline Algorithm Format
// (https://developers.google.com/maps/documentation/utilities/polylinealgorithm).
// This algorithm is commonly used with either 5 or 6 bits of precision.
// To improve usability and decrease user error, we present Polyline5 and Polyline6
// as two distinct compression algorithms.

import { DataCompressor } from "../data-compressor";
import { CompressionParameters } from "../polyline-types";
import { PolylineEncoder } from "../algorithm/encoder";
import { PolylineDecoder } from "../algorithm/decoder";

abstract class EncodedPolyline extends DataCompressor {
  readonly precision: number;

  // The original Encoded Polyline algorithm doesn't support having a header on the encoded data.
  readonly DataContainsHeader = false;

  readonly PolylineEncodingTable: string =
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
  readonly encoder = new PolylineEncoder(
    this.PolylineEncodingTable,
    this.DataContainsHeader,
  );
  readonly decoder = new PolylineDecoder(
    this.PolylineDecodingTable,
    this.DataContainsHeader,
  );

  constructor(precision: number) {
    super();
    this.precision = precision;
  }

  compressLngLatArray(
    lngLatArray: Array<Array<number>>,
    /* parameters: CompressionParameters, */
  ): string {
    return this.encoder.encode(lngLatArray, this.precision);
  }

  decompressLngLatArray(
    compressedData: string,
  ): [Array<Array<number>>, CompressionParameters] {
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
