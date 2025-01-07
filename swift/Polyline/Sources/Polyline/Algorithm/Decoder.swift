// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import Foundation

class PolylineDecoder {
    // decodingTable is a lookup table that converts ASCII values from 0x00-0x7F
    // to the appropriate decoded 0x00-0x3F value. Polyline and Flexible-Polyline
    // use different character encodings, so they need different decoding tables.
    let decodingTable : [Int];
    
    // containsHeader is true if the format includes a header (Flexible-Polyline),
    // and false if it doesn't (Polyline).
    let containsHeader : Bool;
    
    init(decodingTable: [Int], containsHeader: Bool) {
        self.decodingTable = decodingTable;
        self.containsHeader = containsHeader;
    }
    
    // Given an encoded string and a starting index, this decodes a single encoded signed value.
    // The decoded value will be an integer that still needs the decimal place moved over based
    // on the number of digits of encoded precision.
    private func decodeSignedValue(
        encoded: [UInt8],
        startIndex: Int
    ) throws -> (result: Int64, nextIndex: Int) {
        // decode an unsigned value
        let (unsignedValue, nextIndex) = try self.decodeUnsignedValue(
            encoded: encoded,
            startIndex: startIndex
        );
        // If the unsigned value has a 1 encoded in its least significant bit,
        // it's negative, so flip the bits.
        var signedValue = unsignedValue;
        if ((unsignedValue & 1) == 1) {
            signedValue = ~signedValue;
        }
        // Shift the result by one to remove the encoded sign bit.
        signedValue >>= 1;
        return (signedValue, nextIndex);
    }
    
    
    // Given an encoded string and a starting index, this decodes a single encoded
    // unsigned value. The flexible-polyline algorithm uses this directly to decode
    // the header bytes, since those are encoded without the sign bit as the header
    // values are known to be unsigned (which saves 2 bits).
    private func decodeUnsignedValue(
        encoded: [UInt8],
        startIndex: Int
    ) throws -> (result: Int64, nextIndex: Int) {
        var result:Int64 = 0;
        var shift = 0;
        var index = startIndex;
        
        // For each ASCII character, get the 6-bit (0x00 - 0x3F) value that
        // it represents. Shift the accumulated result by 5 bits, add the new
        // 5-bit chunk to the bottom, and keep going for as long as the 6th bit
        // is set.
        while (index < encoded.count) {
            let charCode = encoded[index];
            let value = self.decodingTable[Int(charCode)];
            if (value < 0) {
                throw DecodeError.invalidEncodedCharacter;
            }
            result |= Int64(value & 0x1f) << shift;
            shift += 5;
            index += 1;
            
            // We've reached the final 5-bit chunk for this value, so return.
            // We also return the index, which represents the starting index of the
            // next value to decode.
            if ((value & 0x20) == 0) {
                return (result, index);
            }
        }
        
        // If we've run out of encoded characters without finding an empty 6th bit,
        // something has gone wrong.
        throw DecodeError.extraContinueBit;
    }
    
    private func decodeHeader(
        encoded: [UInt8]
    ) throws -> (header: CompressionParameters, index: Int) {
        // If the data has a header, the first value is expected to be the header version
        // and the second value is compressed metadata containing precision and dimension information.
        let (headerVersion, metadataIndex) = try self.decodeUnsignedValue(encoded: encoded, startIndex: 0);
        if (headerVersion != FlexiblePolylineFormatVersion) {
            throw DecodeError.invalidHeaderVersion;
        }
        let (metadata, nextIndex) = try self.decodeUnsignedValue(
            encoded: encoded,
            startIndex: metadataIndex
        );
        let header = CompressionParameters(
            precisionLngLat: Int(metadata & 0x0f),
            precisionThirdDimension: Int(metadata >> 7) & 0x0f,
            thirdDimension: ThirdDimension(rawValue: Int((metadata >> 4)) & 0x07)!
        );
        return ( header: header, index: nextIndex );
    }
    
    
    func decode(
        encoded: String,
        encodePrecision: Int = 0
    ) throws -> (lngLatArray: Array<Array<Double>>, header: CompressionParameters) {
        // Empty input strings are considered invalid.
        if (encoded.count == 0) {
            throw DecodeError.emptyInput;
        }

        // If the data doesn't have a header, default to the passed-in precision and no 3rd dimension.
        var header = CompressionParameters(
            precisionLngLat: encodePrecision,
            precisionThirdDimension: 0,
            thirdDimension: ThirdDimension.None
        );

        // Convert the string to an array of uint8 values (via UTF8) so that we
        // can easily iterate and index through the characters.
        let encodedUtf8Array: [UInt8] = Array(encoded.utf8)

        // Track the index of the next character to decode from the encoded string.
        var index = 0;
        
        if (self.containsHeader) {
            (header, index) = try self.decodeHeader(encoded: encodedUtf8Array);
        }
        
        let numDimensions = (header.thirdDimension != ThirdDimension.None) ? 3 : 2;
        var outputLngLatArray: Array<Array<Double>> = [];
        
        // The data either contains lat/lng or lat/lng/z values that will be decoded.
        // precisionDivisors are the divisors needed to convert the values from integers
        // back to floating-point.
        let precisionDivisors:[Double] = [
            pow(10.0, Double(header.precisionLngLat)),
            pow(10.0, Double(header.precisionLngLat)),
            pow(10.0, Double(header.precisionThirdDimension))
        ];
        
        // maxAllowedValues are the maximum absolute values allowed for lat/lng/z. This is used for
        // error-checking the coordinate values as they're being decoded.
        let maxAllowedValues = [90.0, 180.0, Double.greatestFiniteMagnitude];
        
        // While decoding, we want to switch from lat/lng/z to lng/lat/z, so this index tells us
        // what position to put the dimension in for the resulting coordinate.
        let resultDimensionIndex = [1, 0, 2];
        
        // Decoded values are deltas from the previous coordinate values, so track the previous values.
        var lastScaledCoordinate:[Int64] = [0, 0, 0];
        
        // Keep decoding until we reach the end of the string.
        while (index < encodedUtf8Array.count) {
            // Each time through the loop we'll decode one full coordinate.
            var coordinate: [Double] = (numDimensions == 2) ? [0.0, 0.0] : [0.0, 0.0, 0.0];
            var deltaValue:Int64 = 0;
            
            // Decode each dimension for the coordinate.
            for dimension in 0...(numDimensions - 1) {
                if (index >= encodedUtf8Array.count) {
                    throw DecodeError.missingCoordinateDimension;
                }
                
                (deltaValue, index) = try self.decodeSignedValue(encoded: encodedUtf8Array, startIndex: index);
                lastScaledCoordinate[dimension] += deltaValue;
                // Get the final lat/lng/z value by scaling the integer back down based on the number of
                // digits of precision.
                let value =
                Double(lastScaledCoordinate[dimension]) / precisionDivisors[dimension];
                if (abs(value) > maxAllowedValues[dimension]) {
                    throw DecodeError.invalidCoordinateValue;
                }
                coordinate[resultDimensionIndex[dimension]] = value;
            }
            outputLngLatArray.append(coordinate);
        }
        
        return (outputLngLatArray, header);
    }

}
