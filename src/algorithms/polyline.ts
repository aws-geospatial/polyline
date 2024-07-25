// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import { CompressionParameters, DataCompressor } from "../data-compressor";
import {
  encode as polylineEncode,
  decode as polylineDecode,
} from "@mapbox/polyline";

function validateInput(compressedData: string) {
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

// Polyline encodes/decodes compressed data using the Encoded Polyline Algorithm Format
// ( https://developers.google.com/maps/documentation/utilities/polylinealgorithm ).
// The algorithm only supports 2D data.
export class Polyline extends DataCompressor {
  readonly precision: number;
  constructor(precision: number) {
    super();
    this.precision = precision;
  }

  supports3D(): boolean {
    return false;
  }

  encodeFromLatLngArray(
    latLngArray: Array<Array<number>>,
    /* parameters: CompressionParameters, */
  ): string {
    return polylineEncode(latLngArray, this.precision);
  }
  decodeToLatLngArray(
    compressedData: string,
  ): [Array<Array<number>>, CompressionParameters] {
    validateInput(compressedData);
    return [
      polylineDecode(compressedData, this.precision),
      { precisionLngLat: this.precision },
    ];
  }
}

// Polyline5 and Polyline6 encodes/decodes compressed data with 5 or 6 bits of precision respectively.
// While the underlying Polyline implementation allows for an arbitrary
// number of bits of precision to be encoded / decoded, location service providers seem
// to only choose 5 or 6 bits of precision, so those are the two algorithms that we'll explicitly offer here.

export class Polyline5 extends Polyline {
  constructor() {
    super(5);
  }
}

export class Polyline6 extends Polyline {
  constructor() {
    super(6);
  }
}
