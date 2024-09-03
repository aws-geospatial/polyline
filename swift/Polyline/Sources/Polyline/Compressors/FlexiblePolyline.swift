// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// This class implements the Flexible-Polyline variation of the
// Encoded Polyline algorithm (https://github.com/heremaps/flexible-polyline).
// The algorithm supports both 2D and 3D data.

import Foundation;

class FlexiblePolyline : DataCompressor {
    let DataContainsHeader = true;
    let FlexPolylineEncodingTable =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    // The lookup table contains conversion values for ASCII characters 0-127.
    // Only the characters listed in the encoding table will contain valid
    // decoding entries below.
    let FlexPolylineDecodingTable = [
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
         -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
         -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, 52, 53, 54, 55, 56, 57, 58, 59, 60,
         61, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
         13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, 63, -1,
         26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44,
         45, 46, 47, 48, 49, 50, 51,
    ];
    
    let encoder : PolylineEncoder;
    let decoder : PolylineDecoder;
    
    override init() {
        self.encoder = PolylineEncoder(
            encodingTable: self.FlexPolylineEncodingTable,
            includeHeader: self.DataContainsHeader
        );
        self.decoder = PolylineDecoder(
            decodingTable: self.FlexPolylineDecodingTable,
            containsHeader: self.DataContainsHeader
        );
        super.init();
    }
    
    override func compressLngLatArray(
        lngLatArray: Array<Array<Double>>,
        parameters: CompressionParameters
    ) throws -> String {
        return try self.encoder.encode(
            lngLatArray: lngLatArray,
            precision: parameters.precisionLngLat,
            thirdDim: parameters.thirdDimension,
            thirdDimPrecision: parameters.precisionThirdDimension
        );
    }
    
    override func decompressLngLatArray(
        compressedData: String
    ) throws -> (Array<Array<Double>>, CompressionParameters) {
        let (lngLatArray, header) = try self.decoder.decode(encoded: compressedData);
        
        return (lngLatArray, header);
    }
}
