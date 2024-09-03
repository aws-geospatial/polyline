// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import Foundation
import XCTest
@testable import Polyline

// Simplified GeoJSON structures for validating the outputs

struct LineString: Decodable {
    let type: String;
    let coordinates: [[Double]];
}
struct Polygon: Decodable {
    let type: String;
    let coordinates: [[[Double]]];
}
struct Properties: Decodable {
    let precision: Int;
    let thirdDimensionPrecision: Int?;
    let thirdDimensionType: String?;
}
struct LineStringFeature: Decodable {
    let type: String;
    let geometry: LineString;
    let properties: Properties;
}
struct PolygonFeature: Decodable {
    let type: String;
    let geometry: Polygon;
    let properties: Properties;
}

// Tests to validate the polyline library

final class PolylineTests: XCTestCase {
    
    let algorithms : [CompressionAlgorithm] = [.FlexiblePolyline, .Polyline5, .Polyline6];
    
    override func setUp() {
        // Reset the compression algorithm back to the default for each unit test.
        Polyline.setCompressionAlgorithm();
    }
    
    private func validateLineString(geojson: String, coords: [[Double]]) {
        let geojsonData = geojson.data(using: .utf8)!;
        let lineString:LineString = try! JSONDecoder().decode(LineString.self, from: geojsonData);

        XCTAssertEqual(lineString.type, "LineString");
        XCTAssertEqual(lineString.coordinates, coords);
    }
    
    private func validatePolygon(geojson: String, coords: [[[Double]]]) {
        let geojsonData = geojson.data(using: .utf8)!;
        let polygon:Polygon = try! JSONDecoder().decode(Polygon.self, from: geojsonData);

        XCTAssertEqual(polygon.type, "Polygon");
        XCTAssertEqual(polygon.coordinates, coords);
    }
    
    private func validateProperties(properties: Properties, parameters: CompressionParameters) {
        XCTAssertEqual(properties.precision, parameters.precisionLngLat);
        XCTAssertEqual(properties.thirdDimensionPrecision != nil, parameters.thirdDimension != ThirdDimension.None);
        if (properties.thirdDimensionPrecision != nil) {
            XCTAssertEqual(properties.thirdDimensionPrecision, parameters.precisionThirdDimension);
        }
        XCTAssertEqual(properties.thirdDimensionType != nil, parameters.thirdDimension != ThirdDimension.None);
        if (properties.thirdDimensionType != nil) {
            switch properties.thirdDimensionType {
            case "level":
                XCTAssertEqual(parameters.thirdDimension, ThirdDimension.Level);
            case "altitude":
                XCTAssertEqual(parameters.thirdDimension, ThirdDimension.Altitude);
            case "elevation":
                XCTAssertEqual(parameters.thirdDimension, ThirdDimension.Elevation);
            default:
                XCTFail("Unknown third dimension type");
            }
            XCTAssertEqual(properties.thirdDimensionPrecision, parameters.precisionThirdDimension);
        }
    }

    private func validateLineStringFeature(geojson: String, coords: [[Double]], parameters: CompressionParameters) {
        let geojsonData = geojson.data(using: .utf8)!;
        let lineStringFeature:LineStringFeature = try! JSONDecoder().decode(LineStringFeature.self, from: geojsonData);

        XCTAssertEqual(lineStringFeature.type, "Feature");
        XCTAssertEqual(lineStringFeature.geometry.type, "LineString");
        XCTAssertEqual(lineStringFeature.geometry.coordinates, coords);
        validateProperties(properties: lineStringFeature.properties, parameters: parameters);
    }
    
    private func validatePolygonFeature(geojson: String, coords: [[[Double]]], parameters: CompressionParameters) {
        let geojsonData = geojson.data(using: .utf8)!;
        let polygonFeature:PolygonFeature = try! JSONDecoder().decode(PolygonFeature.self, from: geojsonData);

        XCTAssertEqual(polygonFeature.type, "Feature");
        XCTAssertEqual(polygonFeature.geometry.type, "Polygon");
        XCTAssertEqual(polygonFeature.geometry.coordinates, coords);
        validateProperties(properties: polygonFeature.properties, parameters: parameters);
    }
    
    
    
    func testDefaultsToFlexiblePolyline() {
        XCTAssertEqual(Polyline.getCompressionAlgorithm(), .FlexiblePolyline);
    }
    
    func testSettingFlexiblePolyline() {
        // Since we default to FlexiblePolyline first set to something other than FlexiblePolyline
        Polyline.setCompressionAlgorithm(.Polyline5);
        // Now set back to FlexiblePolyline
        Polyline.setCompressionAlgorithm(.FlexiblePolyline);
        XCTAssertEqual(Polyline.getCompressionAlgorithm(), .FlexiblePolyline);
    }
    
    // Verify that all of the non-default algorithms can be set correctly
    func testSettingNonDefaultAlgorithm() {
        let nonDefaultAlgorithms: [CompressionAlgorithm] = [ .Polyline5, .Polyline6 ];
        
        for algorithm in nonDefaultAlgorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            XCTAssertEqual(Polyline.getCompressionAlgorithm(), algorithm);
        }
    }
    
    func testDecodingEmptyDataThrowsError() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            XCTAssertThrowsError(try Polyline.decodeToLineString("")) { error in
                XCTAssertEqual(error as! Polyline.DecodeError, Polyline.DecodeError.emptyInput);
            };
        }
    }
    
    func testDecodingBadDataThrowsError() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            // The characters in the string below are invalid for each of the decoding algorithms.
            // For polyline5/polyline6, only ?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~ are valid.
            // For flexiblePolyline, only ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_ are valid.
            XCTAssertThrowsError(try Polyline.decodeToLineString("!#$%(*)")) { error in
                XCTAssertEqual(error as! Polyline.DecodeError, Polyline.DecodeError.invalidEncodedCharacter);
            };

        }
    }
    
    func testEncodingInputPointValuesAreValidated() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            // Longitude too low
            XCTAssertThrowsError(try Polyline.encodeFromLngLatArray(lngLatArray: [[-181.0, 5.0], [0.0, 0.0]])) { error in
                XCTAssertEqual(error as! Polyline.EncodeError, Polyline.EncodeError.invalidCoordinateValue);
            };

            // Longitude too high
            XCTAssertThrowsError(try Polyline.encodeFromLngLatArray(lngLatArray: [[181.0, 5.0], [0.0, 0.0]])) { error in
                XCTAssertEqual(error as! Polyline.EncodeError, Polyline.EncodeError.invalidCoordinateValue);
            };

            // Latitude too low
            XCTAssertThrowsError(try Polyline.encodeFromLngLatArray(lngLatArray: [[5.0, -91.0], [0.0, 0.0]])) { error in
                XCTAssertEqual(error as! Polyline.EncodeError, Polyline.EncodeError.invalidCoordinateValue);
            };

            // Latitude too high
            XCTAssertThrowsError(try Polyline.encodeFromLngLatArray(lngLatArray: [[5.0, 91.0], [0.0, 0.0]])) { error in
                XCTAssertEqual(error as! Polyline.EncodeError, Polyline.EncodeError.invalidCoordinateValue);
            };

        }
    }
    
    func testEncodingMixedDimensionalityThrowsError() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            // Mixing 2D and 3D throws error
            XCTAssertThrowsError(try Polyline.encodeFromLngLatArray(lngLatArray: [[5.0, 5.0], [10.0, 10.0, 10.0]])) { error in
                XCTAssertEqual(error as! Polyline.EncodeError, Polyline.EncodeError.inconsistentCoordinateDimensions);
            };
            // Mixing 3D and 2D throws error
            XCTAssertThrowsError(try Polyline.encodeFromLngLatArray(lngLatArray: [[5.0, 5.0, 5.0], [10.0, 10.0]])) { error in
                XCTAssertEqual(error as! Polyline.EncodeError, Polyline.EncodeError.inconsistentCoordinateDimensions);
            };
        }
    }
    
    func testEncodingUnsupportedDimensionsThrowsError() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            // 1D throws error
            XCTAssertThrowsError(try Polyline.encodeFromLngLatArray(lngLatArray: [[5.0], [10.0]])) { error in
                XCTAssertEqual(error as! Polyline.EncodeError, Polyline.EncodeError.inconsistentCoordinateDimensions);
            };
            // 4D throws error
            XCTAssertThrowsError(try Polyline.encodeFromLngLatArray(lngLatArray: [[5.0, 5.0, 5.0, 5.0], [10.0, 10.0, 10.0, 10.0]])) { error in
                XCTAssertEqual(error as! Polyline.EncodeError, Polyline.EncodeError.inconsistentCoordinateDimensions);
            };
        }
    }
    
    func testEncodingEmptyInputProducesEmptyResults() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            XCTAssertEqual(try Polyline.encodeFromLngLatArray(lngLatArray:[]), "");
        }
    }
    
    func testDecodeToLineStringWithOnePositionThrowsError() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let encodedLine = try Polyline.encodeFromLngLatArray(lngLatArray: [[5.0, 5.0]]);
                XCTAssertThrowsError(try Polyline.decodeToLineString(encodedLine)) { error in
                    XCTAssertEqual(error as! Polyline.GeoJsonError, Polyline.GeoJsonError.invalidLineStringLength);
                };
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    func testDecodeToPolygonWithUnderFourPositionsThrowsError() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let encodedLine = try Polyline.encodeFromLngLatArray(lngLatArray: [[5.0, 5.0], [10.0, 10.0], [5.0, 5.0]]);
                XCTAssertThrowsError(try Polyline.decodeToPolygon([encodedLine])) { error in
                    XCTAssertEqual(error as! Polyline.GeoJsonError, Polyline.GeoJsonError.invalidPolygonLength);
                };
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    func testDecodeToPolygonWithMismatchedStartEndThrowsError() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let encodedLine = try Polyline.encodeFromLngLatArray(lngLatArray: [[5.0, 5.0], [10.0, 10.0], [15.0, 15.0], [20.0, 20.0]]);
                XCTAssertThrowsError(try Polyline.decodeToPolygon([encodedLine])) { error in
                    XCTAssertEqual(error as! Polyline.GeoJsonError, Polyline.GeoJsonError.invalidPolygonClosure);
                };
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    func testDecodeToLineStringProducesValidResults() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let coords = [[132.0, -67.0], [38.0, 62.0]];
                let encodedLine = try Polyline.encodeFromLngLatArray(lngLatArray: coords);
                let geojson = try Polyline.decodeToLineString(encodedLine);

                validateLineString(geojson:geojson, coords:coords);
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    func testDecodeToLineStringFeatureProducesValidResults() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let coords = [[132.0, -67.0], [38.0, 62.0]];
                let encodedLine = try Polyline.encodeFromLngLatArray(lngLatArray: coords);
                let geojson = try Polyline.decodeToLineStringFeature(encodedLine);
                validateLineStringFeature(geojson: geojson, coords: coords, parameters: CompressionParameters(
                    precisionLngLat:(algorithm == .Polyline5) ? 5 : DefaultPrecision));
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    func testDecodeToPolygonProducesValidResults() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let coords = [[0.0, 0.0], [10.0, 0.0], [5.0, 10.0], [0.0, 0.0]];
                let encodedRing = try Polyline.encodeFromLngLatArray(lngLatArray: coords);
                let geojson = try Polyline.decodeToPolygon([encodedRing]);
                validatePolygon(geojson:geojson, coords:[coords]);
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    func testDecodeToPolygonFeatureProducesValidResults() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let coords = [[0.0, 0.0], [10.0, 0.0], [5.0, 10.0], [0.0, 0.0]];
                let encodedRing = try Polyline.encodeFromLngLatArray(lngLatArray: coords);
                let geojson = try Polyline.decodeToPolygonFeature([encodedRing]);
                validatePolygonFeature(geojson: geojson, coords: [coords], parameters: CompressionParameters(
                    precisionLngLat:(algorithm == .Polyline5) ? 5 : DefaultPrecision)
                );
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    func testDecodeToPolygonWithCWOuterRingProducesCCWResult() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let coords = [[0.0, 0.0], [0.0, 10.0], [10.0, 10.0], [10.0, 0.0], [0.0, 0.0]];
                let encodedRing = try Polyline.encodeFromLngLatArray(lngLatArray: coords);
                let geojson = try Polyline.decodeToPolygon([encodedRing]);
                let ccwCoords = [[0.0, 0.0], [10.0, 0.0], [10.0, 10.0], [0.0, 10.0], [0.0, 0.0]];
                validatePolygon(geojson:geojson, coords:[ccwCoords]);
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    func testDecodeToPolygonWithCCWOuterRingProducesCCWResult() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let coords = [[0.0, 0.0], [10.0, 0.0], [10.0, 10.0], [0.0, 10.0], [0.0, 0.0]];
                let encodedRing = try Polyline.encodeFromLngLatArray(lngLatArray: coords);
                let geojson = try Polyline.decodeToPolygon([encodedRing]);
                validatePolygon(geojson:geojson, coords:[coords]);
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    func testDecodeToPolygonWithCWInnerRingsProducesCWResult() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let clockwiseCoords = [
                    [
                        [0.0, 0.0],
                        [10.0, 0.0],
                        [10.0, 10.0],
                        [0.0, 10.0],
                        [0.0, 0.0],
                    ], // CCW outer ring
                    [
                        [2.0, 2.0],
                        [2.0, 8.0],
                        [8.0, 8.0],
                        [8.0, 2.0],
                        [2.0, 2.0],
                    ], // CW inner ring
                    [
                        [4.0, 4.0],
                        [4.0, 6.0],
                        [6.0, 6.0],
                        [6.0, 4.0],
                        [4.0, 4.0],
                    ], // CW inner ring
                ];
                var encodedRings:Array<String> = [];
                for ring in clockwiseCoords {
                    encodedRings.append(try Polyline.encodeFromLngLatArray(lngLatArray: ring));
                }
                let geojson = try Polyline.decodeToPolygon(encodedRings);
                validatePolygon(geojson:geojson, coords:clockwiseCoords);
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    func testDecodeToPolygonWithCCWInnerRingsProducesCWResult() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let counterclockwiseCoords = [
                    [
                        [0.0, 0.0],
                        [10.0, 0.0],
                        [10.0, 10.0],
                        [0.0, 10.0],
                        [0.0, 0.0],
                    ], // CCW outer ring
                    [
                        [2.0, 2.0],
                        [8.0, 2.0],
                        [8.0, 8.0],
                        [2.0, 8.0],
                        [2.0, 2.0],
                    ], // CCW inner ring
                    [
                        [4.0, 4.0],
                        [6.0, 4.0],
                        [6.0, 6.0],
                        [4.0, 6.0],
                        [4.0, 4.0],
                    ], // CCW inner ring
                ];
                var encodedRings:Array<String> = [];
                for ring in counterclockwiseCoords {
                    encodedRings.append(try Polyline.encodeFromLngLatArray(lngLatArray: ring));
                }
                let geojson = try Polyline.decodeToPolygon(encodedRings);
                let expectedCoords = [
                    [
                        [0.0, 0.0],
                        [10.0, 0.0],
                        [10.0, 10.0],
                        [0.0, 10.0],
                        [0.0, 0.0],
                    ], // CCW outer ring
                    [
                        [2.0, 2.0],
                        [2.0, 8.0],
                        [8.0, 8.0],
                        [8.0, 2.0],
                        [2.0, 2.0],
                    ], // CW inner ring
                    [
                        [4.0, 4.0],
                        [4.0, 6.0],
                        [6.0, 6.0],
                        [6.0, 4.0],
                        [4.0, 4.0],
                    ], // CW inner ring
                ];
                validatePolygon(geojson:geojson, coords:expectedCoords);
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    func testDecodeToLineStringWithRangesOfInputsProducesValidResults() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let coords = [
                    // A few different valid longitude values (positive, zero, negative)
                    [167.0, 5.0],
                    [0.0, 5.0],
                    [-167.0, 5.0],
                    // A few different valid latitude values (positive, zero, negative)
                    [5.0, 87.0],
                    [5.0, 0.0],
                    [5.0, -87.0],
                    // A few different high-precision values
                    [123.45678, 76.54321],
                    [-123.45678, -76.54321],
                ];
                let encodedLine = try Polyline.encodeFromLngLatArray(lngLatArray: coords);
                let geojson = try Polyline.decodeToLineString(encodedLine);

                validateLineString(geojson:geojson, coords:coords);
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    
    func testDecodeToPolygonWithRangesOfInputsProducesValidResults() {
        for algorithm in algorithms {
            Polyline.setCompressionAlgorithm(algorithm);
            
            do {
                let coords = [
                    // A few different valid longitude values (positive, zero, negative)
                    [167.0, 5.0],
                    [0.0, 5.0],
                    [-167.0, 5.0],
                    // A few different valid latitude values (positive, zero, negative)
                    [5.0, 87.0],
                    [5.0, 0.0],
                    [5.0, -87.0],
                    // A few different high-precision values
                    [123.45678, 76.54321],
                    [-123.45678, -76.54321],
                    // Close the polygon ring
                    [167.0, 5.0],
                ];
                let encodedLine = try Polyline.encodeFromLngLatArray(lngLatArray: coords);
                let geojson = try Polyline.decodeToPolygon([encodedLine]);
                validatePolygon(geojson:geojson, coords:[coords]);
            }
            catch {
                XCTFail("Unexpected error");
            }
        }
    }
    
    // The following tests use hard-coded compressed data because we want them to contain invalid values and our
    // encoding method would prevent that. The compressed data was generated by calling encodeFromLngLatArray with the
    // input validation temporarily disabled.
    
    func testFlexiblePolylineDecodeInvalidValuesThrowsError() {
        Polyline.setCompressionAlgorithm(.FlexiblePolyline);
        let invalidStrings = [
            "AGgsmytFg0lxJ_rmytF_zlxJ", // Header version = 0
            "CGgsmytFg0lxJ_rmytF_zlxJ", // Header version = 2
        ];
        for invalidString in invalidStrings {
            XCTAssertThrowsError(try Polyline.decodeToLngLatArray(invalidString)) { error in
                XCTAssertEqual(error as! Polyline.DecodeError, Polyline.DecodeError.invalidHeaderVersion);
            };
        }
    }
    
    func testFlexiblePolylineDecodeInvalidHeaderThrowsError() {
        Polyline.setCompressionAlgorithm(.FlexiblePolyline);
        let invalidStrings = [
            "BGg0lxJ_zrn5K_zlxJg0rn5K", // [[-181, 5], [0, 0]] - longitude too low
            "BGg0lxJg0rn5K_zlxJ_zrn5K", // [[181, 5], [0, 0]] - longitude too high
            "BG_rmytFg0lxJgsmytF_zlxJ", // [[5, -91], [0, 0]] - latitude too low
            "BGgsmytFg0lxJ_rmytF_zlxJ", // [[5, 91], [0, 0]] - latitude too high
         ];
        for invalidString in invalidStrings {
            XCTAssertThrowsError(try Polyline.decodeToLngLatArray(invalidString)) { error in
                XCTAssertEqual(error as! Polyline.DecodeError, Polyline.DecodeError.invalidCoordinateValue);
            };
        }
    }
    
    func testPolyline5DecodeInvalidValuesThrowsError() {
        Polyline.setCompressionAlgorithm(.Polyline5);
        let invalidStrings = [
            "_qo]~pvoa@~po]_qvoa@", // [[-181, 5], [0, 0]] - longitude too low
            "_qo]_qvoa@~po]~pvoa@", // [[181, 5], [0, 0]] - longitude too high
            "~lljP_qo]_mljP~po]", // [[5, -91], [0, 0]] - latitude too low
            "_mljP_qo]~lljP~po]", // [[5, 91], [0, 0]] - latitude too high
        ];
        for invalidString in invalidStrings {
            XCTAssertThrowsError(try Polyline.decodeToLngLatArray(invalidString)) { error in
                XCTAssertEqual(error as! Polyline.DecodeError, Polyline.DecodeError.invalidCoordinateValue);
            };
        }
    }
    
    
    func testPolyline6DecodeInvalidValuesThrowsError() {
        Polyline.setCompressionAlgorithm(.Polyline6);
        let invalidStrings = [
            "_sdpH~rjfxI~rdpH_sjfxI", // [[-181, 5], [0, 0]] - longitude too low
            "_sdpH_sjfxI~rdpH~rjfxI", // [[181, 5], [0, 0]] - longitude too high
            "~jeqlD_sdpH_keqlD~rdpH", // [[5, -91], [0, 0]] - latitude too low
            "_keqlD_sdpH~jeqlD~rdpH", // [[5, 91], [0, 0]] - latitude too high
        ];
        for invalidString in invalidStrings {
            XCTAssertThrowsError(try Polyline.decodeToLngLatArray(invalidString)) { error in
                XCTAssertEqual(error as! Polyline.DecodeError, Polyline.DecodeError.invalidCoordinateValue);
            };
        }
    }
        
    // FlexiblePolyline is the only format that supports 3D data, so specifically test that algorithm to ensure
    // that the 3D data works as expected.
    
    func testFlexiblePolylineLngLatArrayHandlesThirdDimensionTypes() {
        Polyline.setCompressionAlgorithm(.FlexiblePolyline);
        let coords = [
            [0.0, 0.0, 5.0],
            [10.0, 0.0, 0.0],
            [10.0, 10.0, -5.0],
            [0.0, 10.0, 0.0],
            [0.0, 0.0, 5.0],
        ];
        for thirdDimension in [Polyline.ThirdDimension.Level, Polyline.ThirdDimension.Altitude, Polyline.ThirdDimension.Elevation] {
            do {
                let encodedLine = try Polyline.encodeFromLngLatArray(lngLatArray: coords, parameters: CompressionParameters(
                    thirdDimension: thirdDimension
                ));
                let result = try Polyline.decodeToLngLatArray(encodedLine);
                XCTAssertEqual(result, coords);
            } catch {
                XCTFail("Unexpected error");
            }
        }
    }
    func testFlexiblePolylineLineStringHandlesThirdDimensionTypes() {
        Polyline.setCompressionAlgorithm(.FlexiblePolyline);
        let coords = [
            [0.0, 0.0, 5.0],
            [10.0, 0.0, 0.0],
            [10.0, 10.0, -5.0],
            [0.0, 10.0, 0.0],
            [0.0, 0.0, 5.0],
        ];
        for thirdDimension in [Polyline.ThirdDimension.Level, Polyline.ThirdDimension.Altitude, Polyline.ThirdDimension.Elevation] {
            do {
                let encodedLine = try Polyline.encodeFromLngLatArray(lngLatArray: coords, parameters: CompressionParameters(
                    thirdDimension: thirdDimension
                ));
                let geojson = try Polyline.decodeToLineString(encodedLine);

                validateLineString(geojson:geojson, coords:coords);
            } catch {
                XCTFail("Unexpected error");
            }
        }
    }
    func testFlexiblePolylineLineStringFeatureHandlesThirdDimensionTypes() {
        Polyline.setCompressionAlgorithm(.FlexiblePolyline);
        let coords = [
            [0.0, 0.0, 5.0],
            [10.0, 0.0, 0.0],
            [10.0, 10.0, -5.0],
            [0.0, 10.0, 0.0],
            [0.0, 0.0, 5.0],
        ];
        for thirdDimension in [Polyline.ThirdDimension.Level, Polyline.ThirdDimension.Altitude, Polyline.ThirdDimension.Elevation] {
            do {
                let parameters = CompressionParameters(
                    thirdDimension: thirdDimension
                );
                let encodedLine = try Polyline.encodeFromLngLatArray(lngLatArray: coords, parameters: parameters);
                let geojson = try Polyline.decodeToLineStringFeature(encodedLine);
                validateLineStringFeature(geojson:geojson, coords:coords, parameters:parameters);
            } catch {
                XCTFail("Unexpected error");
            }
        }
    }
    func testFlexiblePolylinePolygonHandlesThirdDimensionTypes() {
        Polyline.setCompressionAlgorithm(.FlexiblePolyline);
        let ringCoords = [
          [
            [0.0, 0.0, 5.0],
            [10.0, 0.0, 0.0],
            [10.0, 10.0, -5.0],
            [0.0, 10.0, 0.0],
            [0.0, 0.0, 5.0],
          ], // outer ring
          [
            [2.0, 2.0, 5.0],
            [2.0, 8.0, 0.0],
            [8.0, 8.0, -5.0],
            [8.0, 2.0, 0.0],
            [2.0, 2.0, 5.0],
          ], // inner ring
        ];
        for thirdDimension in [Polyline.ThirdDimension.Level, Polyline.ThirdDimension.Altitude, Polyline.ThirdDimension.Elevation] {
            do {
                var encodedRings:Array<String> = [];
                for ring in ringCoords {
                  encodedRings.append(
                    try Polyline.encodeFromLngLatArray(lngLatArray: ring, parameters: 
                      CompressionParameters(thirdDimension: thirdDimension)
                    ));
                }
                let geojson = try Polyline.decodeToPolygon(encodedRings);
                validatePolygon(geojson:geojson, coords:ringCoords);
            } catch {
                XCTFail("Unexpected error");
            }
        }
    }
    func testFlexiblePolylinePolygonFeatureHandlesThirdDimensionTypes() {
        Polyline.setCompressionAlgorithm(.FlexiblePolyline);
        let ringCoords = [
          [
            [0.0, 0.0, 5.0],
            [10.0, 0.0, 0.0],
            [10.0, 10.0, -5.0],
            [0.0, 10.0, 0.0],
            [0.0, 0.0, 5.0],
          ], // outer ring
          [
            [2.0, 2.0, 5.0],
            [2.0, 8.0, 0.0],
            [8.0, 8.0, -5.0],
            [8.0, 2.0, 0.0],
            [2.0, 2.0, 5.0],
          ], // inner ring
        ];
        for thirdDimension in [Polyline.ThirdDimension.Level, Polyline.ThirdDimension.Altitude, Polyline.ThirdDimension.Elevation] {
            do {
                let parameters = CompressionParameters(thirdDimension: thirdDimension);
                var encodedRings:Array<String> = [];
                for ring in ringCoords {
                  encodedRings.append(
                    try Polyline.encodeFromLngLatArray(lngLatArray: ring, parameters:
                      parameters
                    ));
                }
                let geojson = try Polyline.decodeToPolygonFeature(encodedRings);
                validatePolygonFeature(geojson:geojson, coords:ringCoords, parameters:parameters);
            } catch {
                XCTFail("Unexpected error");
            }
        }
    }
    func testPolylineErrorsOnThreeDimensions() {
        let coords = [
            [0.0, 0.0, 5.0],
            [10.0, 0.0, 0.0],
            [10.0, 10.0, -5.0],
            [0.0, 10.0, 0.0],
            [0.0, 0.0, 5.0],
        ];
        for algorithm in [Polyline.CompressionAlgorithm.Polyline5, Polyline.CompressionAlgorithm.Polyline6] {
            Polyline.setCompressionAlgorithm(algorithm);
            XCTAssertThrowsError(try Polyline.encodeFromLngLatArray(lngLatArray: coords, parameters: CompressionParameters(
                thirdDimension: ThirdDimension.Altitude
            ))) { error in
                XCTAssertEqual(error as! Polyline.EncodeError, Polyline.EncodeError.inconsistentCoordinateDimensions);
            };
        }
    }
    
    // Verify that FlexiblePolyline checks for valid encoding settings
    
    func testFlexiblePolylineEncodeThrowsErrorWithNegative2DPrecision() {
        Polyline.setCompressionAlgorithm(.FlexiblePolyline);
        
        let coords = [[0.0, 0.0, 5.0], [10.0, 0.0, 0.0]];
        XCTAssertThrowsError(try Polyline.encodeFromLngLatArray(lngLatArray: coords, parameters: CompressionParameters(precisionLngLat: -5))) { error in
            XCTAssertEqual(error as! Polyline.EncodeError, Polyline.EncodeError.invalidPrecisionValue);
        };
    }
    func testFlexiblePolylineEncodeThrowsErrorWithNegative3DPrecision() {
        Polyline.setCompressionAlgorithm(.FlexiblePolyline);
        
        let coords = [[0.0, 0.0, 5.0], [10.0, 0.0, 0.0]];
        XCTAssertThrowsError(try Polyline.encodeFromLngLatArray(lngLatArray: coords, parameters: CompressionParameters(precisionThirdDimension: -5))) { error in
            XCTAssertEqual(error as! Polyline.EncodeError, Polyline.EncodeError.invalidPrecisionValue);
        };
    }}

