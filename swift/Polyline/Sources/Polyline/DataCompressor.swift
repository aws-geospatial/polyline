// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import Foundation;

// DataCompressor is an abstract base class that defines the interface for
// encoding/decoding compressed coordinate arrays. The coordinate arrays represent either
// LineString ("polyline") or Polygon geometry.
// To make this compressed data easy to use with MapLibre, DataCompressor provides
// methods for decoding the data into different types of GeoJSON outputs:
//  - decodeToLineStringFeature / decodeToPolygonFeature:
//      These produce a GeoJSON Feature object that can be directly passed into MapLibre as a geojson source.
//  - decodeToLineString / decodeToPolygon:
//      These produce a GeoJSON Geometry object that can be manually assembled into a Feature to pass
//      into MapLibre as a geojson source.

// Concrete implementations of this class are expected to implement the following APIs:
// - compressLngLatArray(lngLatArray, compressionParameters) -> compressedData
// - decompressLngLatArray(compressedData) -> [lngLatArray, compressionParameters]

class DataCompressor {
    // Encode an array of LngLat data into a string of compressed data.
    // The coordinates may optionally have a third dimension of data.
    func compressLngLatArray(
        lngLatArray: Array<Array<Double>>,
        parameters: CompressionParameters
    ) throws -> String {
        return "";
    }
    
    // Decode a string of compressed data into an array of LngLat data.
    // The coordinates may optionally have a third dimension of data.
    func decompressLngLatArray(
        compressedData: String
    ) throws -> (Array<Array<Double>>, CompressionParameters) {
        return ([], CompressionParameters(precisionLngLat: DefaultPrecision, precisionThirdDimension: 0, thirdDimension: ThirdDimension.None));
    }
    
    // Helper method to determine whether the polygon is wound in CCW (counterclockwise) or CW (clockwise) order.
    private func polygonIsCounterClockwise(
        lngLatArray: Array<Array<Double>>
    ) -> Bool {
        // If the data isn't a polygon, then it can't be a counter-clockwise polygon.
        // (A polygon requires at least 3 unique points and a 4th last point that matches the first)
        if (lngLatArray.count < 4) {
            return false;
        }
        
        // To determine if a polygon has a counterclockwise winding order, all we need to
        // do is calculate the area of the polygon.
        // If the area is positive, it's counterclockwise.
        // If the area is negative, it's clockwise.
        // If the area is 0, it's neither, so we'll still return false for counterclockwise.
        // This implementation currently assumes that only 2D winding order is important and
        // ignores any optional third dimension.
        var area = 0.0;
        for idx in 0...(lngLatArray.count - 2) {
            let x1 = lngLatArray[idx][0];
            let y1 = lngLatArray[idx][1];
            let x2 = lngLatArray[idx + 1][0];
            let y2 = lngLatArray[idx + 1][1];
            area += x1 * y2 - x2 * y1;
        }
        // If we needed the actual area value, we should divide by 2 here, but since we only
        // need to check the sign, we can skip the division.
        return area > 0;
    }
    
    // Helper method to determine if two LngLat positions are equivalent within a given epsilon range.
    private func positionsAreEquivalent(
        _ pos1: Array<Double>,
        _ pos2: Array<Double>
    ) -> Bool {
        // Verify that the two positions are equal within an epsilon.
        // This epsilon was picked because most compressed data uses <= 6 digits of precision,
        // so this epsilon is large enough to detect intentionally different data, and small
        // enough to detect equivalency for values that just have compression artifact drift.
        let epsilon = 0.000001;
        if (pos1.count != pos2.count) {
            return false;
        }
        // Loop through longitude, latitude, and optional 3rd dimension to make sure each one is equivalent.
        for idx in 0...(pos1.count - 1) {
            if (abs(pos1[idx] - pos2[idx]) >= epsilon) {
                return false;
            }
        }
        return true;
    }
    
    private func decodeLineString(
        _ compressedData: String
    ) throws -> (String, CompressionParameters) {
        let (decodedLine, compressionParameters) =
        try self.decompressLngLatArray(compressedData: compressedData);
        // Validate that the result is a valid GeoJSON LineString per the RFC 7946 GeoJSON spec:
        // "The 'coordinates' member is an array of two or more positions"
        if (decodedLine.count < 2) {
            throw GeoJsonError.invalidLineStringLength;
        }
        return (
            """
            {
            "type": "LineString",
            "coordinates": \(decodedLine)
            }
            """,
            compressionParameters
        );
    }
    
    private func decodePolygon(
        _ compressedData: Array<String>
    ) throws -> (String, CompressionParameters) {
        var decodedPolygon : Array<Array<Array<Double>>> = [];
        var shouldBeCounterclockwise = true; // The first ring of a polygon should be counterclockwise
        var compressionParameters: CompressionParameters = CompressionParameters();
        for ring in compressedData {
            var (decodedRing, ringCompressionParameters) = try self.decompressLngLatArray(compressedData: ring);
            
            // Validate that the result is a valid GeoJSON Polygon linear ring per the RFC 7946 GeoJSON spec.
            
            // 1. "A linear ring is a closed LineString with 4 or more positions."
            if (decodedRing.count < 4) {
                throw GeoJsonError.invalidPolygonLength;
            }
            
            // 2. "The first and last positions are equivalent, and they MUST contain identical values;
            //     their representation SHOULD also be identical."
            // We validate equivalency within a small epsilon.
            if (
                !self.positionsAreEquivalent(
                    decodedRing[0],
                    decodedRing[decodedRing.count - 1]
                )
            ) {
                throw GeoJsonError.invalidPolygonClosure;
            }
            
            // 3. "A linear ring MUST follow the right-hand rule with respect to the area it bounds,
            //     i.e., exterior rings are counterclockwise, and holes are clockwise."
            // "Note: the [GJ2008] specification did not discuss linear ring winding
            //    order.  For backwards compatibility, parsers SHOULD NOT reject
            //    Polygons that do not follow the right-hand rule."
            // "For Polygons with more than one of these rings, the first MUST be
            //       the exterior ring, and any others MUST be interior rings.  The
            //       exterior ring bounds the surface, and the interior rings (if
            //       present) bound holes within the surface."
            
            // With all this taken together, we should enforce the winding order as opposed to just
            // validating it.
            if (
                shouldBeCounterclockwise != self.polygonIsCounterClockwise(lngLatArray: decodedRing)
            ) {
                decodedRing.reverse();
            }
            
            decodedPolygon.append(decodedRing);
            
            // Set compressionParameter metadata to whatever the last compression parameters were that were used.
            // This may need to have more complicated logic at some point if different rings have different compression
            // parameters and we want to capture all of them.
            compressionParameters = ringCompressionParameters;
            
            // All rings after the first should be clockwise.
            shouldBeCounterclockwise = false;
        }
        return (
            """
            {
            "type": "Polygon",
            "coordinates": \(decodedPolygon)
            }
            """,
            compressionParameters
        );
    }
    
    private func compressionParametersToGeoJsonProperties(
        parameters: CompressionParameters
    ) -> String {
        switch (parameters.thirdDimension) {
        case ThirdDimension.Level:
            return """
            {
            "precision": \(parameters.precisionLngLat),
            "thirdDimensionPrecision": \(parameters.precisionThirdDimension),
            "thirdDimensionType": "level"
            }
            """;
        case ThirdDimension.Elevation:
            return """
            {
            "precision": \(parameters.precisionLngLat),
            "thirdDimensionPrecision": \(parameters.precisionThirdDimension),
            "thirdDimensionType": "elevation"
            }
            """;
        case ThirdDimension.Altitude:
            return """
            {
            "precision": \(parameters.precisionLngLat),
            "thirdDimensionPrecision": \(parameters.precisionThirdDimension),
            "thirdDimensionType": "altitude"
            }
            """;
        default:
            return """
            {
            "precision": \(parameters.precisionLngLat)
            }
            """;
        }
    }
    
    func encodeFromLngLatArray(
        lngLatArray: Array<Array<Double>>,
        parameters: CompressionParameters
    ) throws -> String {
        return try self.compressLngLatArray(lngLatArray: lngLatArray, parameters: parameters);
    }
    
    func decodeToLngLatArray(compressedData: String) throws -> Array<Array<Double>> {
        let (decodedLngLatArray, _) = try self.decompressLngLatArray(compressedData: compressedData);
        
        return decodedLngLatArray;
    }
    
    func decodeToLineString(compressedData: String) throws -> String {
        let (lineString, _) = try self.decodeLineString(compressedData);
        return lineString;
    }
    
    func decodeToPolygon(compressedData: Array<String>) throws -> String {
        let (polygon, _) = try self.decodePolygon(compressedData);
        return polygon;
    }
    
    func decodeToLineStringFeature(compressedData: String) throws -> String {
        let (lineString, compressionParameters) = try self.decodeLineString(compressedData);
        return """
        {
        "type": "Feature",
        "geometry": \(lineString),
        "properties": \(self.compressionParametersToGeoJsonProperties(parameters: compressionParameters))
        }
        """;
    }
    
    func decodeToPolygonFeature(compressedData: Array<String>) throws -> String {
        let (polygon, compressionParameters) = try self.decodePolygon(compressedData);
        return """
        {
        "type": "Feature",
        "geometry": \(polygon),
        "properties": \(self.compressionParametersToGeoJsonProperties(parameters: compressionParameters))
        }
        """;
    }
}
