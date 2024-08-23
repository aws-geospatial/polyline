// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// This class implements both the Encoded Polyline Algorithm Format
// (https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
// and the Flexible-Polyline variation of the algorithm (https://github.com/heremaps/flexible-polyline).

// This implementation has two differences to improve usability:
// - It uses well-defined rounding to ensure deterministic results across all programming languages.
// - It caps the max encoding/decoding precision to 11 decimal places (1 micrometer), because 15 places will
//   lose precision when using 64-bit floating-point numbers.

import {DefaultPrecision, FlexiblePolylineFormatVersion, ThirdDimension} from "./polyline-types";

export class PolylineEncoder {
    readonly encodingTable: string;
    readonly includeHeader: boolean;

    constructor(encodingTable:string, includeHeader:boolean) {
        this.encodingTable = encodingTable;
        this.includeHeader = includeHeader;
    }

    // The original polyline algorithm supposedly uses "round to nearest, ties away from 0"
    // for its rounding rule. Flexible-polyline uses the rounding rules of the implementing
    // language. Our generalized implementation will use the "round to nearest, ties away from 0"
    // rule for all languages to keep the encoding deterministic across implementations.
    private polylineRound(value : number) : number {
        return Math.sign(value) * Math.floor(Math.abs(value) + 0.5);
    }

    encode(lngLatArray:Array<Array<number>>, precision:number, thirdDim:number = ThirdDimension.None, thirdDimPrecision:number = 0):string {
        // Encode a sequence of lat,lng or lat,lng(,{third_dim}). Note that values should be of type Number
        //   `precision`: how many decimal digits of precision to store the latitude and longitude.
        //   `third_dim`: type of the third dimension if present in the input.
        //   `third_dim_precision`: how many decimal digits of precision to store the third dimension.
        if (!lngLatArray.length) { return ''; }

        const is2DData = lngLatArray[0].length === 2;

        // TODO: Verify precision ranges, no thirdDim unless flex polyline, thirdDim type.

        let output = '';
        const xyPrecisionMultiplier = 10 ** (Number.isInteger(precision) ? precision : DefaultPrecision);
        const zPrecisionMultiplier = 10 ** thirdDimPrecision;

        // Flexible-polyline starts with an encoded header that contains precision and dimension metadata.
        if (this.includeHeader) {
            output = this.encodeHeader(precision, thirdDim, thirdDimPrecision);
        }

        let lastLat = 0;
        let lastLng = 0;
        let lastZ = 0;

        lngLatArray.forEach((location) => {
            // While looping through, also verify that each lngLat value is within valid ranges.
            if (
                location.length < 2 ||
                Math.abs(location[0]) > 180 ||
                Math.abs(location[1]) > 90
            ) {
                throw Error(
                    `Invalid input. Input coordinates must contain valid lng/lat data. Found ${location}.`,
                );
            }
            if (location.length === 2) {
                if (!is2DData) {
                    throw Error(
                        "Invalid input. All coordinates need to have the same number of dimensions.",
                    );
                }
            } else if (location.length === 3) {
                if (is2DData) {
                    throw Error(
                        "Invalid input. All coordinates need to have the same number of dimensions.",
                    );
                }
                // If the input data has 3D data, preserve that in the data we're encoding.
            } else {
                throw Error("Invalid input. Coordinates must have 2 or 3 dimensions.");
            }



            const lat = this.polylineRound(location[1] * xyPrecisionMultiplier);
            output += this.encodeScaledValue(lat - lastLat);
            lastLat = lat;

            const lng = this.polylineRound(location[0] * xyPrecisionMultiplier);
            output += this.encodeScaledValue(lng - lastLng);
            lastLng = lng;

            if (thirdDim) {
                const z = this.polylineRound(location[2] * zPrecisionMultiplier);
                output += this.encodeScaledValue(z - lastZ);
                lastZ = z;
            }
        });

        return output;
    }

    private encodeHeader(precision:number, thirdDim:number, thirdDimPrecision:number):string {
        // Encode the `precision`, `third_dim` and `third_dim_precision` into one encoded char
        /*
        if (precision < 0 || precision > 15) {
            throw new Error('precision out of range. Should be between 0 and 15');
        }
        if (thirdDimPrecision < 0 || thirdDimPrecision > 15) {
            throw new Error('thirdDimPrecision out of range. Should be between 0 and 15');
        }
        if (thirdDim < 0 || thirdDim > 7 || thirdDim === 4 || thirdDim === 5) {
            throw new Error('thirdDim should be between 0, 1, 2, 3, 6 or 7');
        }
    */
        const res = (thirdDimPrecision << 7) | (thirdDim << 4) | precision;
        return this.encodeUnsignedNumber(FlexiblePolylineFormatVersion) + this.encodeUnsignedNumber(res);
    }

    private encodeUnsignedNumber(val:number):string {
        // Uses variable integer encoding to encode an unsigned integer. Returns the encoded string.
        let res = '';
        let numVal = val;
        while (numVal > 0x1F) {
            const pos = (numVal & 0x1F) | 0x20;
            res += this.encodingTable[pos];
            numVal >>= 5;
        }
        return res + this.encodingTable[numVal];
    }

    private encodeScaledValue(value:number):string {
        // Transform a integer `value` into a variable length sequence of characters.
        let numVal = value;
        const negative = numVal < 0;
        numVal <<= 1;
        if (negative) {
            numVal = ~numVal;
        }

        return this.encodeUnsignedNumber(numVal);
    }
}
