// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// This class implements both the Encoded Polyline Algorithm Format
// (https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
// and the Flexible-Polyline variation of the algorithm (https://github.com/heremaps/flexible-polyline).

// This implementation has two differences to improve usability:
// - It uses well-defined rounding to ensure deterministic results across all programming languages.
//   The Flexible-Polyline algorithm definition says to use the rounding rules of the programming
//   language, but this can cause inconsistent rounding depending on what language happens to be used
//   on both the encoding and decoding sides.
// - It caps the max encoding/decoding precision to 11 decimal places (1 micrometer), because 12+ places can
//   lose precision when using 64-bit floating-point numbers to store integers.

import {
  FlexiblePolylineFormatVersion,
  ThirdDimension,
} from "../polyline-types";

export class PolylineEncoder {
  // encodingTable is a lookup table that converts values from 0x00-0x3F
  // to the appropriate encoded ASCII character. Polyline and Flexible-Polyline
  // use different character encodings.
  readonly encodingTable: string;

  // includeHeader is true if the format includes a header (Flexible-Polyline),
  // and false if it doesn't (Polyline).
  readonly includeHeader: boolean;

  constructor(encodingTable: string, includeHeader: boolean) {
    this.encodingTable = encodingTable;
    this.includeHeader = includeHeader;
  }

  // The original polyline algorithm supposedly uses "round to nearest, ties away from 0"
  // for its rounding rule. Flexible-polyline uses the rounding rules of the implementing
  // language. Our generalized implementation will use the "round to nearest, ties away from 0"
  // rule for all languages to keep the encoding deterministic across implementations.
  private polylineRound(value: number): number {
    return value < 0 ? -Math.round(-value) : Math.round(value);
  }

  encode(
    lngLatArray: Array<Array<number>>,
    precision: number,
    thirdDim: number = ThirdDimension.None,
    thirdDimPrecision: number = 0,
  ): string {
    if (precision < 0 || precision > 11) {
      throw Error(
        "Only precision values of 0-11 decimal digits are supported.",
      );
    }
    if (!Object.values(ThirdDimension).includes(+thirdDim)) {
      throw Error("thirdDim is an invalid ThirdDimension value.");
    }
    if (thirdDimPrecision < 0 || thirdDimPrecision > 11) {
      throw Error(
        "Only thirdDimPrecision values of 0-11 decimal digits are supported.",
      );
    }

    if (!lngLatArray.length) {
      return "";
    }

    const numDimensions = thirdDim ? 3 : 2;

    // The data will either encode lat/lng or lat/lng/z values.
    // precisionMultipliers are the multipliers needed to convert the values
    // from floating-point to scaled integers.
    const precisionMultipliers = [
      10 ** precision,
      10 ** precision,
      10 ** thirdDimPrecision,
    ];

    // While encoding, we want to switch from lng/lat/z to lat/lng/z, so this index tells us
    // what index to grab from the input coordinate when encoding each dimension.
    const inputDimensionIndex = [1, 0, 2];

    // maxAllowedValues are the maximum absolute values allowed for lat/lng/z. This is used for
    // error-checking the coordinate values as they're being encoded.
    const maxAllowedValues = [90, 180, Infinity];

    // Encoded values are deltas from the previous coordinate values, so track the previous lat/lng/z values.
    const lastScaledCoordinate = [0, 0, 0];

    let output = "";

    // Flexible-polyline starts with an encoded header that contains precision and dimension metadata.
    if (this.includeHeader) {
      output = this.encodeHeader(precision, thirdDim, thirdDimPrecision);
    }

    lngLatArray.forEach((coordinate) => {
      if (coordinate.length != numDimensions) {
        throw Error(
          "Invalid input. All coordinates need to have the same number of dimensions.",
        );
      }

      for (let dimension = 0; dimension < numDimensions; dimension++) {
        // Even though our input data is in lng/lat/z order, this is where we grab them in
        // lat/lng/z order for encoding.
        const inputValue = coordinate[inputDimensionIndex[dimension]];
        // While looping through, also verify the input data is valid
        if (Math.abs(inputValue) > maxAllowedValues[dimension]) {
          throw Error(
            `Invalid input. Input coordinates must contain valid lng/lat coordinate data. Found ${coordinate}.`,
          );
        }
        // Scale the value based on the number of digits of precision, encode the delta between
        // it and the previous value to the output, and track it as the previous value for encoding
        // the next delta.
        const scaledValue = this.polylineRound(
          inputValue * precisionMultipliers[dimension],
        );
        if (!Number.isSafeInteger(scaledValue)) {
          throw Error(
            "Invalid input. Scaled coordinate value exceeds the supported safe integer range.",
          );
        }
        const deltaValue = scaledValue - lastScaledCoordinate[dimension];
        if (!Number.isSafeInteger(deltaValue)) {
          throw Error(
            "Invalid input. Scaled coordinate delta exceeds the supported safe integer range.",
          );
        }
        output += this.encodeSignedValue(deltaValue);
        lastScaledCoordinate[dimension] = scaledValue;
      }
    });

    return output;
  }

  private encodeHeader(
    precision: number,
    thirdDim: number,
    thirdDimPrecision: number,
  ): string {
    // Combine all the metadata about the encoded data into a single value for the header.
    const metadataValue =
      (thirdDimPrecision << 7) | (thirdDim << 4) | precision;
    return (
      this.encodeUnsignedValue(FlexiblePolylineFormatVersion) +
      this.encodeUnsignedValue(metadataValue)
    );
  }

  // Given a single input unsigned scaled value, this encodes into a series of
  // ASCII characters. The flexible-polyline algorithm uses this directly to encode
  // the header bytes, since those are known not to need a sign bit.
  private encodeUnsignedValue(value: number): string {
    if (!Number.isSafeInteger(value) || value < 0) {
      throw Error(
        "Invalid input. Cannot encode an unsafe or negative unsigned value.",
      );
    }

    let encodedString = "";
    let remainingValue = value;
    // Loop through each 5-bit chunk in the value, add a 6th bit if there
    // will be additional chunks, and encode to an ASCII value.
    while (remainingValue > 0x1f) {
      const chunk = (remainingValue % 0x20) + 0x20;
      encodedString += this.encodingTable[chunk];
      remainingValue = Math.floor(remainingValue / 0x20);
    }
    // For the last chunk, set the 6th bit to 0 (since there are no more chunks) and encode it.
    return encodedString + this.encodingTable[remainingValue];
  }

  // Given a single input signed scaled value, this encodes into a series of
  // ASCII characters.
  private encodeSignedValue(value: number): string {
    if (!Number.isSafeInteger(value)) {
      throw Error(
        "Invalid input. Cannot encode a signed value outside the safe integer range.",
      );
    }

    // Zig-zag encoding normally doubles the magnitude and adds the sign bit.
    // Encode the first chunk separately so the doubled value never needs to be
    // represented as a potentially unsafe JavaScript number.
    const isNegative = value < 0;
    const magnitude = isNegative ? -value - 1 : value;
    const firstChunk = (magnitude % 0x10) * 2 + (isNegative ? 1 : 0);
    const remainingValue = Math.floor(magnitude / 0x10);

    if (remainingValue === 0) {
      return this.encodingTable[firstChunk];
    }
    return (
      this.encodingTable[firstChunk + 0x20] +
      this.encodeUnsignedValue(remainingValue)
    );
  }
}
