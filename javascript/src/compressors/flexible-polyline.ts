// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// This class implements the Flexible-Polyline variation of the
// Encoded Polyline algorithm (https://github.com/heremaps/flexible-polyline).
// The algorithm supports both 2D and 3D data.

import { DataCompressor } from "../data-compressor";
import {
  CompressionParameters,
  DefaultPrecision,
  ThirdDimension,
} from "../polyline-types";
import { PolylineEncoder } from "../algorithm/encoder";
import { PolylineDecoder } from "../algorithm/decoder";

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

  compressLngLatArray(
    lngLatArray: Array<Array<number>>,
    parameters: CompressionParameters,
  ): string {
    // Set any parameters that weren't passed in to their default values.
    const DefaultCompressionParameters = {
      precisionLngLat: DefaultPrecision,
      precisionThirdDimension: DefaultPrecision,
      thirdDimension: ThirdDimension.None,
    };
    const fullParameters = { ...DefaultCompressionParameters, ...parameters };

    return this.encoder.encode(
      lngLatArray,
      fullParameters.precisionLngLat,
      fullParameters.thirdDimension,
      fullParameters.precisionThirdDimension,
    );
  }

  decompressLngLatArray(
    encodedData: string,
  ): [Array<Array<number>>, CompressionParameters] {
    const [lngLatArray, header] = this.decoder.decode(encodedData);

    return [lngLatArray, header];
  }
}
