// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// This class implements both the Encoded Polyline Algorithm Format
// (https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
// and the Flexible-Polyline variation of the algorithm (https://github.com/heremaps/flexible-polyline).

// This implementation has two differences to improve usability:
// - It uses well-defined rounding to ensure deterministic results across all programming languages.
// - It caps the max encoding/decoding precision to 11 decimal places (1 micrometer), because 15 places will
//   lose precision when using 64-bit floating-point numbers.

import {
  CompressionParameters,
  FlexiblePolylineFormatVersion,
  ThirdDimension,
} from "./polyline-types";

export class PolylineDecoder {
  // decodingTable is a lookup table that converts ASCII values from 0x00-0x7F
  // to the appropriate decoded 0x00-0x3F value. Polyline and Flexible-Polyline
  // use different character encodings, so they need different decoding tables.
  readonly decodingTable: number[];

  // containsHeader is true if the format includes a header (Flexible-Polyline),
  // and false if it doesn't (Polyline).
  readonly containsHeader: boolean;

  constructor(decodingTable: number[], containsHeader: boolean) {
    this.decodingTable = decodingTable;
    this.containsHeader = containsHeader;
  }

  // Given an encoded string and a starting index, this decodes a single encoded signed value.
  // The decoded value will be an integer that still needs the decimal place moved over based
  // on the number of digits of encoded precision.
  private decodeSignedValue(
    encoded: string,
    startIndex: number,
  ): [result: number, nextIndex: number] {
    // decode an unsigned value
    const [unsignedValue, nextIndex] = this.decodeUnsignedValue(
      encoded,
      startIndex,
    );
    // If the unsigned value has a 1 encoded in its least significant bit,
    // it's negative, so flip the bits.
    let signedValue = unsignedValue;
    if (unsignedValue & 1) {
      signedValue = ~signedValue;
    }
    // Shift the result by one to remove the encoded sign bit.
    signedValue >>= 1;
    return [signedValue, nextIndex];
  }

  // Given an encoded string and a starting index, this decodes a single encoded
  // unsigned value. The flexible-polyline algorithm uses this directly to decode
  // the header bytes, since those are encoded without the sign bit as the header
  // values are known to be unsigned (which saves 2 bits).
  private decodeUnsignedValue(
    encoded: string,
    startIndex: number,
  ): [result: number, nextIndex: number] {
    let result = 0;
    let shift = 0;
    let index = startIndex;

    // For each ASCII character, get the 6-bit (0x00 - 0x3F) value that
    // it represents. Shift the accumulated result by 5 bits, add the new
    // 5-bit chunk to the bottom, and keep going for as long as the 6th bit
    // is set.
    while (index < encoded.length) {
      const charCode = encoded.charCodeAt(index);
      const value = this.decodingTable[charCode];
      result |= (value & 0x1f) << shift;
      shift += 5;
      index++;

      // We've reached the final 5-bit chunk for this value, so return.
      // We also return the index, which represents the starting index of the
      // next value to decode.
      if ((value & 0x20) === 0) {
        return [result, index];
      }
    }

    // If we've run out of encoded characters without finding an empty 6th bit,
    // something has gone wrong.
    throw Error(
      "Invalid encoding - last block contained an extra 0x20 'continue' bit.",
    );
  }

  private decodeHeader(
    encoded: string,
  ): [header: CompressionParameters, index: number] {
    // If the data has a header, the first value is expected to be the header version
    // and the second value is compressed metadata containing precision and dimension information.
    const [headerVersion, metadataIndex] = this.decodeUnsignedValue(encoded, 0);
    if (headerVersion !== FlexiblePolylineFormatVersion) {
      throw new Error("Invalid format version");
    }
    const [metadata, nextIndex] = this.decodeUnsignedValue(
      encoded,
      metadataIndex,
    );
    return [
      {
        precisionLngLat: metadata & 0x0f,
        thirdDimension: (metadata >> 4) & 0x07,
        precisionThirdDimension: (metadata >> 7) & 0x0f,
      },
      nextIndex,
    ];
  }

  decode(
    encoded: string,
    encodePrecision: number = 0,
  ): [lngLatArray: Array<Array<number>>, header: CompressionParameters] {
    // If the data doesn't have a header, default to the passed-in precision and no 3rd dimension.
    let header: CompressionParameters = {
      precisionLngLat: encodePrecision,
      thirdDimension: ThirdDimension.None,
      precisionThirdDimension: 0,
    };

    // Track the index of the next character to decode from the encoded string.
    let index = 0;

    if (this.containsHeader) {
      [header, index] = this.decodeHeader(encoded);
    }

    const numDimensions = header.thirdDimension ? 3 : 2;
    const outputLngLatArray: Array<Array<number>> = [];

    // The data either contains lat/lng or lat/lng/z values that will be decoded.
    // precisionDivisors are the divisors needed to convert the values from integers
    // back to floating-point.
    const precisionDivisors = [
      10 ** header.precisionLngLat,
      10 ** header.precisionLngLat,
      10 ** header.precisionThirdDimension,
    ];

    // maxAllowedValues are the maximum absolute values allowed for lat/lng/z. This is used for
    // error-checking the coordinate values as they're being decoded.
    const maxAllowedValues = [90, 180, Infinity];

    // While decoding, we want to switch from lat/lng/z to lng/lat/z, so this index tells us
    // what position to put the dimension in for the resulting coordinate.
    const resultDimensionIndex = [1, 0, 2];

    // Decoded values are deltas from the previous coordinate values, so track the previous values.
    const lastScaledCoordinate = [0, 0, 0];

    // Keep decoding until we reach the end of the string.
    while (index < encoded.length) {
      // Each time through the loop we'll decode one full coordinate.
      const coordinate: number[] = [];
      let deltaValue = 0;

      // Decode each dimension for the coordinate.
      for (let dimension = 0; dimension < numDimensions; dimension += 1) {
        if (index >= encoded.length) {
          throw Error("Encoding unexpectedly ended early.");
        }

        [deltaValue, index] = this.decodeSignedValue(encoded, index);
        lastScaledCoordinate[dimension] += deltaValue;
        // Get the final lat/lng/z value by scaling the integer back down based on the number of
        // digits of precision.
        const value =
          lastScaledCoordinate[dimension] / precisionDivisors[dimension];
        if (Math.abs(value) > maxAllowedValues[dimension]) {
          throw Error(
            `Invalid input. Compressed data contains invalid coordinate value: ${value}`,
          );
        }
        coordinate[resultDimensionIndex[dimension]] = value;
      }
      outputLngLatArray.push(coordinate);
    }

    return [outputLngLatArray, header];
  }
}
