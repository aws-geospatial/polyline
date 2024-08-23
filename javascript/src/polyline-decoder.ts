// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// This class implements both the Encoded Polyline Algorithm Format
// (https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
// and the Flexible-Polyline variation of the algorithm (https://github.com/heremaps/flexible-polyline).

// This implementation has two differences to improve usability:
// - It uses well-defined rounding to ensure deterministic results across all programming languages.
// - It caps the max encoding/decoding precision to 11 decimal places (1 micrometer), because 15 places will
//   lose precision when using 64-bit floating-point numbers.

import {FlexiblePolylineFormatVersion, ThirdDimension} from "./polyline-types";

export class PolylineDecoder {

    readonly DECODING_TABLE = [
        62, -1, -1, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, -1,
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
        22, 23, 24, 25, -1, -1, -1, -1, 63, -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51
    ];

    private toSigned(val:number):number {
        // Decode the sign from an unsigned value
        let res = val;
        if (res & 1) {
            res = ~res;
        }
        res >>= 1;
        return +res.toString();
    }


    private decodeUnsignedValues(encoded:string, isFlexPolyline:boolean) {
        let result = 0;
        let shift = 0;
        const resList = [];

        encoded.split('').forEach((char) => {
            const value = isFlexPolyline ? this.DECODING_TABLE[char.charCodeAt(0) - 45] : (char.charCodeAt(0) - 63);
            result |= (value & 0x1F) << shift;
            if ((value & 0x20) === 0) {
                resList.push(result);
                result = 0;
                shift = 0;
            } else {
                shift += 5;
            }
        });

        if (shift > 0) {
            throw new Error('Invalid encoding');
        }

        return resList;
    }

    private decodeHeader(version, encodedHeader) {
        if (+version.toString() !== FlexiblePolylineFormatVersion) {
            throw new Error('Invalid format version');
        }
        const headerNumber = +encodedHeader.toString();
        const precision = headerNumber & 15;
        const thirdDim = (headerNumber >> 4) & 7;
        const thirdDimPrecision = (headerNumber >> 7) & 15;
        return { precision, thirdDim, thirdDimPrecision };
    }

    decode(encoded:string, isFlexPolyline:boolean, encodePrecision:number = 0) {
        const decoder = this.decodeUnsignedValues(encoded, isFlexPolyline);

        const precision = encodePrecision;
        let thirdDim = ThirdDimension.None;
        const thirdDimPrecision = 0;
        let header = {precision, thirdDim, thirdDimPrecision};

        let factorDegree = 10 ** precision;
        let factorZ = 0;
        let i = 0;

        if (isFlexPolyline) {
            header = this.decodeHeader(decoder[0], decoder[1]);
            factorDegree = 10 ** header.precision;
            factorZ = 10 ** header.thirdDimPrecision;
            thirdDim = header.thirdDim;
            i += 2;
        }

        let lastLat = 0;
        let lastLng = 0;
        let lastZ = 0;
        const res = [];

        for (;i < decoder.length;) {
            const deltaLat = this.toSigned(decoder[i]);
            const deltaLng = this.toSigned(decoder[i + 1]);
            lastLat += deltaLat;
            lastLng += deltaLng;

            if (thirdDim) {
                const deltaZ = this.toSigned(decoder[i + 2]);
                lastZ += deltaZ;
                res.push([lastLng / factorDegree, lastLat / factorDegree, lastZ / factorZ]);
                i += 3;
            } else {
                res.push([lastLng / factorDegree, lastLat / factorDegree]);
                i += 2;
            }
        }

        if (i !== decoder.length) {
            throw new Error('Invalid encoding. Premature ending reached');
        }

        for (const lngLat of res) {
            // While looping through, also verify that each latLng value is within valid ranges.
            if (
                lngLat.length < 2 ||
                Math.abs(lngLat[0]) > 180 ||
                Math.abs(lngLat[1]) > 90
            ) {
                throw Error(
                    `Invalid input. Compressed data must contain valid lng/lat data. Found ${lngLat}.`,
                );
            }
        }

        return {
            ...header,
            polyline: res,
        };
    }
}

