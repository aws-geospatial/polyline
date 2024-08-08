// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import * as polyline from "..";
import { LineString, Polygon } from "geojson";

// Verify that the exported API matches our expectations.
describe("Expected methods are exported", () => {
  it.each([
    // Exposed enums
    "CompressionAlgorithm",
    // Exposed methods
    "getCompressionAlgorithm",
    "setCompressionAlgorithm",
    "encodeFromLngLatArray",
    "decodeToLngLatArray",
    "decodeToLineString",
    "decodeToPolygon",
    "decodeToLineStringFeature",
    "decodeToPolygonFeature",
  ])('Should export API named "%s"', (apiName: string) => {
    expect(apiName in polyline).toBe(true);
  });
});

// Verify that each type of supported compression algorithm can be set in the library
describe("getCompressionAlgorithm/setCompressionAlgorithm work correctly", () => {
  it("Setting a bad decoder type throws an error", () => {
    expect(() => {
      polyline.setCompressionAlgorithm(-1 as polyline.CompressionAlgorithm);
    }).toThrow(Error);
  });

  // Test the default decoder separately because we want to verify that it *is* the default and we'll
  // need a bit of extra logic to change away from it and back to ensure that setDecoder truly works correctly.
  it("Should default to FlexiblePolyline", () => {
    expect(polyline.getCompressionAlgorithm()).toBe(
      polyline.CompressionAlgorithm.FlexiblePolyline,
    );
  });
  it("Should be able to set FlexiblePolyline", () => {
    // Since we default to FlexiblePolyline first set to something other than FlexiblePolyline
    polyline.setCompressionAlgorithm(polyline.CompressionAlgorithm.Polyline5);
    // Now set back to FlexiblePolyline
    polyline.setCompressionAlgorithm(
      polyline.CompressionAlgorithm.FlexiblePolyline,
    );
    expect(polyline.getCompressionAlgorithm()).toBe(
      polyline.CompressionAlgorithm.FlexiblePolyline,
    );
  });

  // Verify that all of the non-default algorithms can be set correctly
  it.each([
    ["Polyline5", polyline.CompressionAlgorithm.Polyline5],
    ["Polyline6", polyline.CompressionAlgorithm.Polyline6],
  ])(
    'Should be able to set non-default algorithm "%s"',
    (enumName: string, algorithm: polyline.CompressionAlgorithm) => {
      polyline.setCompressionAlgorithm(algorithm);
      expect(polyline.getCompressionAlgorithm()).toBe(algorithm);
    },
  );
});

// Verify that encoding/decoding works properly for each supported algorithm type.
describe.each([
  ["FlexiblePolyline", polyline.CompressionAlgorithm.FlexiblePolyline],
  ["Polyline5", polyline.CompressionAlgorithm.Polyline5],
  ["Polyline6", polyline.CompressionAlgorithm.Polyline6],
])(
  "%s: Encoding/decoding produces the expected coordinate results",
  (name: string, algorithm: polyline.CompressionAlgorithm) => {
    // Make sure to set the compression algorithm before each test.
    beforeEach(() => {
      polyline.setCompressionAlgorithm(algorithm);
    });

    // Test the input checks for compressed data
    it("Decoding empty data throws an error", () => {
      expect(() => {
        polyline.decodeToLineString("");
      }).toThrow(Error);
    });

    it("Decoding bad data throws an error", () => {
      // The characters in the string below are invalid for each of the decoding algorithms.
      // For polyline5/polyline6, only ?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~ are valid.
      // For flexiblePolyline, only ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_ are valid.
      expect(() => {
        polyline.decodeToLineString("!#$%(*)");
      }).toThrow(Error);
    });

    // Test the input checks for uncompressed data
    it.each([
      [
        "longitude too low",
        [
          [-181, 5],
          [0, 0],
        ],
      ],
      [
        "longitude too high",
        [
          [181, 5],
          [0, 0],
        ],
      ],
      [
        "latitude too low",
        [
          [5, -91],
          [0, 0],
        ],
      ],
      [
        "latitude too high",
        [
          [5, 91],
          [0, 0],
        ],
      ],
    ])(
      "encodeFromLngLatArray throws an error if an input point is invalid: %s",
      (invalidMsg: string, coords: Array<Array<number>>) => {
        expect(() => {
          polyline.encodeFromLngLatArray(coords);
        }).toThrow(Error);
      },
    );

    it("encodeFromLngLatArray throws an error if 2D and 3D coordinates are mixed", () => {
      expect(() => {
        polyline.encodeFromLngLatArray([
          [5, 5],
          [10, 10, 10],
        ]);
      }).toThrow(Error);
    });

    it("encodeFromLngLatArray throws an error if 3D and 2D coordinates are mixed", () => {
      expect(() => {
        polyline.encodeFromLngLatArray([
          [5, 5, 5],
          [10, 10],
        ]);
      }).toThrow(Error);
    });

    it("encodeFromLngLatArray throws an error for 1D coordinates", () => {
      expect(() => {
        polyline.encodeFromLngLatArray([[5], [10]]);
      }).toThrow(Error);
    });

    it("encodeFromLngLatArray throws an error for 4D coordinates", () => {
      expect(() => {
        polyline.encodeFromLngLatArray([
          [5, 5, 5, 5],
          [10, 10, 10, 10],
        ]);
      }).toThrow(Error);
    });

    // Validate basic output checks for decoded data that validate for the requested geometry type

    it("decodeToLineString throws an error if the input has < 2 positions", () => {
      const coords = [[5, 5]];
      const encodedLine = polyline.encodeFromLngLatArray(coords);
      expect(() => {
        polyline.decodeToLineString(encodedLine);
      }).toThrow(Error);
    });

    it("decodeToPolygon throws an error if the input has < 4 positions", () => {
      const coords = [
        [5, 5],
        [10, 10],
        [5, 5],
      ];
      const encodedRing = polyline.encodeFromLngLatArray(coords);
      expect(() => {
        polyline.decodeToPolygon([encodedRing]);
      }).toThrow(Error);
    });

    it("decodeToPolygon throws an error if the first and last decoded positions don't match", () => {
      const coords = [
        [5, 5],
        [10, 10],
        [15, 15],
        [20, 20],
      ];
      const encodedRing = polyline.encodeFromLngLatArray(coords);
      expect(() => {
        polyline.decodeToPolygon([encodedRing]);
      }).toThrow(Error);
    });

    // Validate proper GeoJSON output
    it("decodeToLineString produces a valid expected LineString", () => {
      const coords = [
        [132, -67],
        [38, 62],
      ];
      const encodedLine = polyline.encodeFromLngLatArray(coords);
      const result = polyline.decodeToLineString(encodedLine);
      expect(result.type).toEqual("LineString");
      expect(result.coordinates).toEqual(coords);
    });

    it("decodeToLineStringFeature produces a valid expected LineString inside a Feature", () => {
      const coords = [
        [132, -67],
        [38, 62],
      ];
      const encodedLine = polyline.encodeFromLngLatArray(coords);
      const result = polyline.decodeToLineStringFeature(encodedLine);
      expect(result.type).toEqual("Feature");
      expect(result.geometry.type).toEqual("LineString");
      const lineString = result.geometry as LineString;
      expect(lineString.coordinates).toEqual(coords);
    });

    it("decodeToPolygon produces a valid expected Polygon", () => {
      const coords = [
        [0, 0],
        [10, 0],
        [5, 10],
        [0, 0],
      ];
      const encodedRing = polyline.encodeFromLngLatArray(coords);
      const result = polyline.decodeToPolygon([encodedRing]);
      expect(result.type).toEqual("Polygon");
      // For polygons, we expect an array of coordinate arrays. Our data should only have a single
      // array, for the exterior ring.
      expect(result.coordinates.length).toEqual(1);
      expect(result.coordinates[0]).toEqual(coords);
    });

    it("decodeToPolygonFeature produces a valid expected Polygon inside a Feature", () => {
      const coords = [
        [0, 0],
        [10, 0],
        [5, 10],
        [0, 0],
      ];
      const encodedRing = polyline.encodeFromLngLatArray(coords);
      const result = polyline.decodeToPolygonFeature([encodedRing]);
      expect(result.type).toEqual("Feature");
      expect(result.geometry.type).toEqual("Polygon");
      const polygon = result.geometry as Polygon;
      // For polygons, we expect an array of coordinate arrays. Our data should only have a single
      // array, for the exterior ring.
      expect(polygon.coordinates.length).toEqual(1);
      expect(polygon.coordinates[0]).toEqual(coords);
    });

    // Validate polygon winding order checks
    it("decodeToPolygon: decoding CW winding order as an outer ring produces CCW result", () => {
      const clockwiseCoords = [
        [0, 0],
        [0, 10],
        [10, 10],
        [10, 0],
        [0, 0],
      ];
      const encodedLine = polyline.encodeFromLngLatArray(clockwiseCoords);
      const result = polyline.decodeToPolygon([encodedLine]);
      // Note - this mutates clockwiseCoords, but since we don't use it again, it's ok.
      const counterclockwiseCoords = clockwiseCoords.reverse();
      expect(result.coordinates[0]).toEqual(counterclockwiseCoords);
    });

    it("decodeToPolygon: decoding CCW winding order as an outer ring produces CCW result", () => {
      const counterclockwiseCoords = [
        [0, 0],
        [10, 0],
        [10, 10],
        [0, 10],
        [0, 0],
      ];
      const encodedLine = polyline.encodeFromLngLatArray(
        counterclockwiseCoords,
      );
      const result = polyline.decodeToPolygon([encodedLine]);
      expect(result.coordinates[0]).toEqual(counterclockwiseCoords);
    });

    it("decodeToPolygon: decoding CW winding order as inner rings produces CW results", () => {
      const clockwiseCoords = [
        [
          [0, 0],
          [0, 10],
          [10, 10],
          [10, 0],
          [0, 0],
        ], // outer ring
        [
          [2, 2],
          [2, 8],
          [8, 8],
          [8, 2],
          [2, 2],
        ], // inner ring
        [
          [4, 4],
          [4, 6],
          [6, 6],
          [6, 4],
          [4, 4],
        ], // inner ring
      ];
      const encodedRings = [];
      for (const ring of clockwiseCoords) {
        encodedRings.push(polyline.encodeFromLngLatArray(ring));
      }
      const result = polyline.decodeToPolygon(encodedRings);
      // Only validate the inner rings in this test.
      for (let idx = 1; idx < clockwiseCoords.length; idx++) {
        expect(result.coordinates[idx]).toEqual(clockwiseCoords[idx]);
      }
    });

    it("decodeToPolygon: decoding CCW winding order as inner rings produces CW results", () => {
      const counterclockwiseCoords = [
        [
          [0, 0],
          [10, 0],
          [10, 10],
          [0, 10],
          [0, 0],
        ], // outer ring
        [
          [2, 2],
          [8, 2],
          [8, 8],
          [2, 8],
          [2, 2],
        ], // inner ring
        [
          [4, 4],
          [6, 4],
          [6, 6],
          [4, 6],
          [4, 4],
        ], // inner ring
      ];
      const encodedRings = [];
      for (const ring of counterclockwiseCoords) {
        encodedRings.push(polyline.encodeFromLngLatArray(ring));
      }
      const result = polyline.decodeToPolygon(encodedRings);
      // Only validate the inner rings in this test.
      for (let idx = 1; idx < counterclockwiseCoords.length; idx++) {
        // Note - this mutates each line of counterclockwiseCoords, but since we don't use it again, it's ok.
        const clockwiseCoords = counterclockwiseCoords[idx].reverse();
        expect(result.coordinates[idx]).toEqual(clockwiseCoords);
      }
    });

    // Validate that different combinations of data decode correctly.
    it("decodeToLineString correctly decodes different coordinate values", () => {
      const coords = [
        // A few different valid longitude values (positive, zero, negative)
        [167, 5],
        [0, 5],
        [-167, 5],
        // A few different valid latitude values (positive, zero, negative)
        [5, 87],
        [5, 0],
        [5, -87],
        // A few different high-precision values
        [123.45678, 76.54321],
        [-123.45678, -76.54321],
      ];
      const encodedLine = polyline.encodeFromLngLatArray(coords);
      const result = polyline.decodeToLineString(encodedLine);
      expect(result.coordinates.length).toEqual(coords.length);
      for (let coordIdx = 0; coordIdx < result.coordinates.length; coordIdx++) {
        for (
          let lngLatIdx = 0;
          lngLatIdx < result.coordinates[coordIdx].length;
          lngLatIdx++
        ) {
          // Compare at 5 decimals of precision since that's the smallest precision we encode against across
          // all the compression formats.
          const precision = 5;
          expect(result.coordinates[coordIdx][lngLatIdx]).toBeCloseTo(
            coords[coordIdx][lngLatIdx],
            precision,
          );
        }
      }
    });

    it("decodeToPolygon correctly decodes different coordinate values", () => {
      const coords = [
        // A few different valid longitude values (positive, zero, negative)
        [167, 5],
        [0, 5],
        [-167, 5],
        // A few different valid latitude values (positive, zero, negative)
        [5, 87],
        [5, 0],
        [5, -87],
        // A few different high-precision values
        [123.45678, 76.54321],
        [-123.45678, -76.54321],
        // Close the polygon ring
        [167, 5],
      ];
      const encodedRing = polyline.encodeFromLngLatArray(coords);
      const result = polyline.decodeToPolygon([encodedRing]);
      expect(result.coordinates[0].length).toEqual(coords.length);
      for (
        let coordIdx = 0;
        coordIdx < result.coordinates[0].length;
        coordIdx++
      ) {
        for (
          let lngLatIdx = 0;
          lngLatIdx < result.coordinates[0][coordIdx].length;
          lngLatIdx++
        ) {
          // Compare at 5 decimals of precision since that's the smallest precision we encode against across
          // all the compression formats.
          const precision = 5;
          expect(result.coordinates[0][coordIdx][lngLatIdx]).toBeCloseTo(
            coords[coordIdx][lngLatIdx],
            precision,
          );
        }
      }
    });
  },
);

// The following tests use hard-coded compressed data because we want them to contain invalid values and our
// encoding method would prevent that. The compressed data was generated by calling encodeFromLngLatArray with the
// input validation temporarily disabled.
describe("Decoding invalid data throws an error", () => {
  it.each([
    ["longitude too low", "BGg0lxJ_zrn5K_zlxJg0rn5K"], // [[-181, 5], [0, 0]]
    ["longitude too high", "BGg0lxJg0rn5K_zlxJ_zrn5K"], // [[181, 5], [0, 0]]
    ["latitude too low", "BG_rmytFg0lxJgsmytF_zlxJ"], // [[5, -91], [0, 0]]
    ["latitude too high", "BGgsmytFg0lxJ_rmytF_zlxJ"], // [[5, 91], [0, 0]]
  ])(
    "FlexiblePolyline: Decoding throws an error with invalid coordinates: %s",
    (invalidMsg: string, encodedLine: string) => {
      polyline.setCompressionAlgorithm(
        polyline.CompressionAlgorithm.FlexiblePolyline,
      );
      expect(() => {
        polyline.decodeToLngLatArray(encodedLine);
      }).toThrow(Error);
    },
  );

  it.each([
    ["longitude too low", "_qo]~pvoa@~po]_qvoa@"], // [[-181, 5], [0, 0]]
    ["longitude too high", "_qo]_qvoa@~po]~pvoa@"], // [[181, 5], [0, 0]]
    ["latitude too low", "~lljP_qo]_mljP~po]"], // [[5, -91], [0, 0]]
    ["latitude too high", "_mljP_qo]~lljP~po]"], // [[5, 91], [0, 0]]
  ])(
    "Polyline5: Decoding throws an error with invalid coordinates: %s",
    (invalidMsg: string, encodedLine: string) => {
      polyline.setCompressionAlgorithm(polyline.CompressionAlgorithm.Polyline5);
      expect(() => {
        polyline.decodeToLngLatArray(encodedLine);
      }).toThrow(Error);
    },
  );

  it.each([
    ["longitude too low", "_sdpH~rjfxI~rdpH_sjfxI"], // [[-181, 5], [0, 0]]
    ["longitude too high", "_sdpH_sjfxI~rdpH~rjfxI"], // [[181, 5], [0, 0]]
    ["latitude too low", "~jeqlD_sdpH_keqlD~rdpH"], // [[5, -91], [0, 0]]
    ["latitude too high", "_keqlD_sdpH~jeqlD~rdpH"], // [[5, 91], [0, 0]]
  ])(
    "Polyline6: Decoding throws an error with invalid coordinates: %s",
    (invalidMsg: string, encodedLine: string) => {
      polyline.setCompressionAlgorithm(polyline.CompressionAlgorithm.Polyline6);
      expect(() => {
        polyline.decodeToLngLatArray(encodedLine);
      }).toThrow(Error);
    },
  );
});

// FlexiblePolyline is the only format that supports 3D data, so specifically test that algorithm to ensure
// that the 3D data works as expected.
describe("Decoding 3D data with FlexiblePolyline produces the expected results", () => {
  // Make sure to set the compression algorithm before each test.
  beforeEach(() => {
    polyline.setCompressionAlgorithm(
      polyline.CompressionAlgorithm.FlexiblePolyline,
    );
  });

  // Test encoder/decoder with 3D data
  describe.each([
    ["altitude", polyline.ThirdDimension.Altitude],
    ["elevation", polyline.ThirdDimension.Elevation],
  ])(
    "3D produces expected results for %s",
    (name: string, thirdDimension: polyline.ThirdDimension) => {
      it("decodeToLngLatArray produces expected results", () => {
        const coords = [
          [0, 0, 5],
          [10, 0, 0],
          [10, 10, -5],
          [0, 10, 0],
          [0, 0, 5],
        ];
        const encodedLine = polyline.encodeFromLngLatArray(coords, {
          thirdDimension: thirdDimension,
        });
        const result = polyline.decodeToLngLatArray(encodedLine);
        expect(result).toEqual(coords);
      });

      it("decodeToLineString produces expected results", () => {
        const coords = [
          [0, 0, 5],
          [10, 0, 0],
          [10, 10, -5],
          [0, 10, 0],
          [0, 0, 5],
        ];
        const encodedLine = polyline.encodeFromLngLatArray(coords, {
          thirdDimension: thirdDimension,
        });
        const result = polyline.decodeToLineString(encodedLine);
        expect(result.coordinates).toEqual(coords);
      });

      it("decodeToPolygon produces expected results", () => {
        const ringCoords = [
          [
            [0, 0, 5],
            [10, 0, 0],
            [10, 10, -5],
            [0, 10, 0],
            [0, 0, 5],
          ], // outer ring
          [
            [2, 2, 5],
            [2, 8, 0],
            [8, 8, -5],
            [8, 2, 0],
            [2, 2, 5],
          ], // inner ring
        ];
        const encodedRings = [];
        for (const ring of ringCoords) {
          encodedRings.push(
            polyline.encodeFromLngLatArray(ring, {
              thirdDimension: thirdDimension,
            }),
          );
        }
        const result = polyline.decodeToPolygon(encodedRings);
        for (let idx = 0; idx < ringCoords.length; idx++) {
          expect(result.coordinates[idx]).toEqual(ringCoords[idx]);
        }
      });

      it("decodeToLineStringFeature produces expected results", () => {
        const coords = [
          [0, 0, 5],
          [10, 0, 0],
          [10, 10, -5],
          [0, 10, 0],
          [0, 0, 5],
        ];
        const encodedLine = polyline.encodeFromLngLatArray(coords, {
          thirdDimension: thirdDimension,
        });
        const result = polyline.decodeToLineStringFeature(encodedLine);
        expect(result.type).toEqual("Feature");
        expect(result.properties.thirdDimensionType).toEqual(name);
        expect(result.geometry.type).toEqual("LineString");
        const lineString = result.geometry as LineString;
        expect(lineString.coordinates).toEqual(coords);
      });

      it("decodeToPolygonFeature produces expected results", () => {
        const ringCoords = [
          [
            [0, 0, 5],
            [10, 0, 0],
            [10, 10, -5],
            [0, 10, 0],
            [0, 0, 5],
          ], // outer ring
          [
            [2, 2, 5],
            [2, 8, 0],
            [8, 8, -5],
            [8, 2, 0],
            [2, 2, 5],
          ], // inner ring
        ];
        const encodedRings = [];
        for (const ring of ringCoords) {
          encodedRings.push(
            polyline.encodeFromLngLatArray(ring, {
              thirdDimension: thirdDimension,
            }),
          );
        }
        const result = polyline.decodeToPolygonFeature(encodedRings);
        expect(result.type).toEqual("Feature");
        expect(result.geometry.type).toEqual("Polygon");
        expect(result.properties.thirdDimensionType).toEqual(name);
        const polygon = result.geometry as Polygon;
        expect(polygon.coordinates).toEqual(ringCoords);
      });
    },
  );
});

// Verify that the other algorithms throw errors with 3D data.
describe.each([
  ["Polyline5", polyline.CompressionAlgorithm.Polyline5],
  ["Polyline6", polyline.CompressionAlgorithm.Polyline6],
])(
  "%s: Encoding 3D data with algorithms that don't support it throws errors",
  (name: string, algorithm: polyline.CompressionAlgorithm) => {
    // Make sure to set the compression algorithm before each test.
    beforeEach(() => {
      polyline.setCompressionAlgorithm(algorithm);
  });

  it("encodeFromLngLatArray throws an error with 3D data", () => {
    const coords = [
      [0, 0, 5],
      [10, 0, 0],
    ];
    expect(() => {
      polyline.encodeFromLngLatArray(coords, {
        thirdDimension: polyline.ThirdDimension.Altitude,
      });
    }).toThrow(Error);
  });
});

// Verify that FlexiblePolyline check for valid encoding settings.
describe("Encoding data with FlexiblePolyline and bad settings throws errors", () => {
  // Make sure to set the compression algorithm before each test.
  beforeEach(() => {
    polyline.setCompressionAlgorithm(
        polyline.CompressionAlgorithm.FlexiblePolyline,
    );
  });

  it("encodeFromLngLatArray throws an error with negative 2D precision", () => {
    const coords = [
      [0, 0, 5],
      [10, 0, 0],
    ];
    expect(() => {
      polyline.encodeFromLngLatArray(coords, {
        precisionLngLat: -5
      });
    }).toThrow(Error);
  });

  it("encodeFromLngLatArray throws an error with negative 3D precision", () => {
    const coords = [
      [0, 0, 5],
      [10, 0, 0],
    ];
    expect(() => {
      polyline.encodeFromLngLatArray(coords, {
        precisionThirdDimension: -5
      });
    }).toThrow(Error);
  });

});
