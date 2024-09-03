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

import Foundation

class PolylineEncoder {
    // encodingTable is a lookup table that converts values from 0x00-0x3F
    // to the appropriate encoded ASCII character. Polyline and Flexible-Polyline
    // use different character encodings.
    let encodingTable: String;
    
    // includeHeader is true if the format includes a header (Flexible-Polyline),
    // and false if it doesn't (Polyline).
    let includeHeader: Bool;
    
    init(encodingTable: String, includeHeader: Bool) {
        self.encodingTable = encodingTable;
        self.includeHeader = includeHeader;
    }
    
    // The original polyline algorithm supposedly uses "round to nearest, ties away from 0"
    // for its rounding rule. Flexible-polyline uses the rounding rules of the implementing
    // language. Our generalized implementation will use the "round to nearest, ties away from 0"
    // rule for all languages to keep the encoding deterministic across implementations.
    private func polylineRound(_ value: Double) -> Int64 {
        let rounded = floor(abs(value) + 0.5);
        return (value >= 0.0) ? Int64(rounded) : Int64(-rounded);
    }
    
    func encode(
        lngLatArray: Array<Array<Double>>,
        precision: Int,
        thirdDim: ThirdDimension = ThirdDimension.None,
        thirdDimPrecision: Int = 0
    ) throws -> String {
        if (precision < 0 || precision > 11) {
            throw EncodeError.invalidPrecisionValue;
        }
        if (thirdDimPrecision < 0 || thirdDimPrecision > 11) {
            throw EncodeError.invalidPrecisionValue;
        }
        
        if (lngLatArray.count == 0) {
            return "";
        }
        
        let numDimensions = (thirdDim != ThirdDimension.None) ? 3 : 2;
        
        // The data will either encode lat/lng or lat/lng/z values.
        // precisionMultipliers are the multipliers needed to convert the values
        // from floating-point to scaled integers.
        let precisionMultipliers = [
            pow(10.0, Double(precision)),
            pow(10.0, Double(precision)),
            pow(10.0, Double(thirdDimPrecision))
        ];
        
        // While encoding, we want to switch from lng/lat/z to lat/lng/z, so this index tells us
        // what index to grab from the input coordinate when encoding each dimension.
        let inputDimensionIndex = [1, 0, 2];
        
        // maxAllowedValues are the maximum absolute values allowed for lat/lng/z. This is used for
        // error-checking the coordinate values as they're being encoded.
        let maxAllowedValues = [90.0, 180.0, Double.greatestFiniteMagnitude];
        
        // Encoded values are deltas from the previous coordinate values, so track the previous lat/lng/z values.
        var lastScaledCoordinate:[Int64] = [0, 0, 0];
        
        var output = "";
        
        // Flexible-polyline starts with an encoded header that contains precision and dimension metadata.
        if (self.includeHeader) {
            output = self.encodeHeader(precision: precision, thirdDim: thirdDim, thirdDimPrecision: thirdDimPrecision);
        }
        
        for coordinate in lngLatArray {
            if (coordinate.count != numDimensions) {
                throw EncodeError.inconsistentCoordinateDimensions;
            }
            
            for dimension in 0...(numDimensions - 1) {
                // Even though our input data is in lng/lat/z order, this is where we grab them in
                // lat/lng/z order for encoding.
                let inputValue = coordinate[inputDimensionIndex[dimension]];
                // While looping through, also verify the input data is valid
                if (abs(inputValue) > maxAllowedValues[dimension]) {
                    throw EncodeError.invalidCoordinateValue;
                }
                // Scale the value based on the number of digits of precision, encode the delta between
                // it and the previous value to the output, and track it as the previous value for encoding
                // the next delta.
                let scaledValue = self.polylineRound((inputValue * precisionMultipliers[dimension]));
                output += self.encodeSignedValue(scaledValue - lastScaledCoordinate[dimension]);
                lastScaledCoordinate[dimension] = scaledValue;
            }
        }
        
        return output;
    }
    
    private func encodeHeader(
        precision: Int,
        thirdDim: ThirdDimension,
        thirdDimPrecision: Int
    ) -> String {
        // Combine all the metadata about the encoded data into a single value for the header.
        let metadataValue =
        (thirdDimPrecision << 7) | (thirdDim.rawValue << 4) | precision;
        return (
            self.encodeUnsignedValue(Int64(FlexiblePolylineFormatVersion)) +
            self.encodeUnsignedValue(Int64(metadataValue))
        );
    }
    
    // Given a single input unsigned scaled value, this encodes into a series of
    // ASCII characters. The flexible-polyline algorithm uses this directly to encode
    // the header bytes, since those are known not to need a sign bit.
    private func encodeUnsignedValue(_ value: Int64) -> String {
        var encodedString = "";
        var remainingValue = value;
        // Loop through each 5-bit chunk in the value, add a 6th bit if there
        // will be additional chunks, and encode to an ASCII value.
        while (remainingValue > 0x1f) {
            let chunk = Int(remainingValue & 0x1f) | 0x20;
            let encodedChar = self.encodingTable[self.encodingTable.index(self.encodingTable.startIndex, offsetBy: chunk)];
            encodedString += [encodedChar];
            remainingValue >>= 5;
        }
        // For the last chunk, set the 6th bit to 0 (since there are no more chunks) and encode it.
        let finalEncodedChar = self.encodingTable[self.encodingTable.index(self.encodingTable.startIndex, offsetBy: Int(remainingValue))];
        return encodedString + [finalEncodedChar];
    }
    
    // Given a single input signed scaled value, this encodes into a series of
    // ASCII characters.
    private func encodeSignedValue(_ value: Int64) -> String {
        var unsignedValue = value;
        // Shift the value over by 1 bit to make room for the sign bit at the end.
        unsignedValue <<= 1;
        // If the input value is negative, flip all the bits, including the sign bit.
        if (value < 0) {
            unsignedValue = ~unsignedValue;
        }
        
        return self.encodeUnsignedValue(unsignedValue);
    }
}
