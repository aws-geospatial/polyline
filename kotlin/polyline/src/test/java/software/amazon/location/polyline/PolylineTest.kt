package software.amazon.location.polyline

import com.google.gson.Gson
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

// Simplified GeoJSON structures for validating the outputs

data class LineString(
    val type: String,
    val coordinates: Array<DoubleArray>
)
data class Polygon(
    val type: String,
    val coordinates: Array<Array<DoubleArray>>
)

data class Properties(
    val precision: Int,
    val thirdDimensionPrecision: Int?,
    val thirdDimensionType: String?
)

data class LineStringFeature(
    val type: String,
    val geometry: LineString,
    val properties: Properties
)

data class PolygonFeature(
    val type: String,
    val geometry: Polygon,
    val properties: Properties
)

class PolylineTest {

    private val algorithms: List<CompressionAlgorithm> = listOf(
        CompressionAlgorithm.FlexiblePolyline,
        CompressionAlgorithm.Polyline5,
        CompressionAlgorithm.Polyline6)

    private fun validateLineString(geojson: String, coords: Array<DoubleArray>) {
        val lineString= Gson().fromJson(geojson, LineString::class.java)
        assertEquals(lineString.type, "LineString")
        assertTrue(lineString.coordinates.contentDeepEquals(coords))
    }

    private fun validatePolygon(geojson: String, coords: Array<Array<DoubleArray>>) {
        val polygon= Gson().fromJson(geojson, Polygon::class.java)
        assertEquals(polygon.type, "Polygon")
        assertTrue(polygon.coordinates.contentDeepEquals(coords))
    }

    private fun validateProperties(properties: Properties, parameters: CompressionParameters) {
        assertEquals(properties.precision, parameters.precisionLngLat)
        assertEquals(properties.thirdDimensionPrecision != null, parameters.thirdDimension != ThirdDimension.None)
        if (properties.thirdDimensionPrecision != null) {
            assertEquals(properties.thirdDimensionPrecision, parameters.precisionThirdDimension)
        }
        assertEquals(properties.thirdDimensionType != null, parameters.thirdDimension != ThirdDimension.None)
        if (properties.thirdDimensionType != null) {
            when (properties.thirdDimensionType) {
                "level" -> assertEquals(parameters.thirdDimension, ThirdDimension.Level)
                "altitude" -> assertEquals(parameters.thirdDimension, ThirdDimension.Altitude)
                "elevation" -> assertEquals(parameters.thirdDimension, ThirdDimension.Elevation)
                else -> fail("Unknown third dimension type")
            }
            assertEquals(properties.thirdDimensionPrecision, parameters.precisionThirdDimension)
        }
    }

    private fun validateLineStringFeature(geojson: String, coords: Array<DoubleArray>, parameters: CompressionParameters) {
        val lineStringFeature = Gson().fromJson(geojson, LineStringFeature::class.java)
        assertEquals(lineStringFeature.type, "Feature")
        assertEquals(lineStringFeature.geometry.type, "LineString")
        assertTrue(lineStringFeature.geometry.coordinates.contentDeepEquals(coords))
        validateProperties(lineStringFeature.properties, parameters)
    }

    private fun validatePolygonFeature(geojson: String, coords: Array<Array<DoubleArray>>, parameters: CompressionParameters) {
        val polygonFeature = Gson().fromJson(geojson, PolygonFeature::class.java)
        assertEquals(polygonFeature.type, "Feature")
        assertEquals(polygonFeature.geometry.type, "Polygon")
        assertTrue(polygonFeature.geometry.coordinates.contentDeepEquals(coords))
        validateProperties(polygonFeature.properties, parameters)
    }

    @BeforeEach
    fun setup() {
        // Reset the compression algorithm back to the default for each unit test.
        setCompressionAlgorithm()
    }

    @Test
    fun `test defaults to flexible polyline`() {
        assertEquals(getCompressionAlgorithm(), CompressionAlgorithm.FlexiblePolyline)
    }

    @Test
    fun `test setting flexible polyline`() {
        // Since we default to FlexiblePolyline first set to something other than FlexiblePolyline
        setCompressionAlgorithm(CompressionAlgorithm.Polyline5)
        // Now set back to FlexiblePolyline
        setCompressionAlgorithm(CompressionAlgorithm.FlexiblePolyline)
        assertEquals(getCompressionAlgorithm(), CompressionAlgorithm.FlexiblePolyline)
    }

    @Test
    fun `test setting non-default algorithm`() {
        val nonDefaultAlgorithms = listOf(CompressionAlgorithm.Polyline5, CompressionAlgorithm.Polyline6)

        for (algorithm in nonDefaultAlgorithms) {
            setCompressionAlgorithm(algorithm)
            assertEquals(getCompressionAlgorithm(), algorithm)
        }
    }

    @Test
    fun testDecodingEmptyDataThrowsError() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)
            assertFailsWith<EmptyInputException>(
                message = "Expected EmptyInputException to be thrown",
                block = {
                    decodeToLineString("")
                })
        }
    }

    @Test
    fun testDecodingBadDataThrowsError() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)
            // The characters in the string below are invalid for each of the decoding algorithms.
            // For polyline5/polyline6, only ?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~ are valid.
            // For flexiblePolyline, only ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_ are valid.
            assertFailsWith<InvalidEncodedCharacterException>(
                message = "Expected InvalidEncodedCharacterException to be thrown",
                block = {
                    decodeToLineString("!#$%(*)&")
                })
        }
    }

    @Test
    fun testEncodingInputPointValuesAreValidated() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            // Longitude too low
            assertFailsWith<InvalidCoordinateValueException>(
                message = "Expected InvalidCoordinateValueException to be thrown",
                block = {
                    encodeFromLngLatArray(arrayOf(doubleArrayOf(-181.0, 5.0), doubleArrayOf(0.0, 0.0)))
                })

            // Longitude too high
            assertFailsWith<InvalidCoordinateValueException>(
                message = "Expected InvalidCoordinateValueException to be thrown",
                block = {
                    encodeFromLngLatArray(arrayOf(doubleArrayOf(181.0, 5.0), doubleArrayOf(0.0, 0.0)))
                })

            // Latitude too low
            assertFailsWith<InvalidCoordinateValueException>(
                message = "Expected InvalidCoordinateValueException to be thrown",
                block = {
                    encodeFromLngLatArray(arrayOf(doubleArrayOf(5.0, -91.0), doubleArrayOf(0.0, 0.0)))
                })

            // Latitude too high
            assertFailsWith<InvalidCoordinateValueException>(
                message = "Expected InvalidCoordinateValueException to be thrown",
                block = {
                    encodeFromLngLatArray(arrayOf(doubleArrayOf(5.0, 91.0), doubleArrayOf(0.0, 0.0)))
                })
        }
    }

    @Test
    fun testEncodingMixedDimensionalityThrowsError() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            // Mixing 2D and 3D throws error
            assertFailsWith<InconsistentCoordinateDimensionsException>(
                message = "Expected InconsistentCoordinateDimensionsException to be thrown",
                block = {
                    encodeFromLngLatArray(arrayOf(doubleArrayOf(5.0, 5.0), doubleArrayOf(10.0, 10.0, 10.0)))
                })

            // Mixing 3D and 2D throws error
            assertFailsWith<InconsistentCoordinateDimensionsException>(
                message = "Expected InconsistentCoordinateDimensionsException to be thrown",
                block = {
                    encodeFromLngLatArray(arrayOf(doubleArrayOf(5.0, 5.0, 5.0), doubleArrayOf(10.0, 10.0)))
                })
        }
    }

    @Test
    fun testEncodingUnsupportedDimensionsThrowsError() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            // 1D throws error
            assertFailsWith<InconsistentCoordinateDimensionsException>(
                message = "Expected InconsistentCoordinateDimensionsException to be thrown",
                block = {
                    encodeFromLngLatArray(arrayOf(doubleArrayOf(5.0), doubleArrayOf(10.0)))
                })

            // 4D throws error
            assertFailsWith<InconsistentCoordinateDimensionsException>(
                message = "Expected InconsistentCoordinateDimensionsException to be thrown",
                block = {
                    encodeFromLngLatArray(arrayOf(doubleArrayOf(5.0, 5.0, 5.0, 5.0), doubleArrayOf(10.0, 10.0, 10.0, 10.0)))
                })
        }
    }

    @Test
    fun testEncodingEmptyInputProducesEmptyResults() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)
            assertEquals("", encodeFromLngLatArray(emptyArray()))
        }
    }

    @Test
    fun testDecodeToLineStringWithOnePositionThrowsError() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            assertFailsWith<InvalidLineStringLengthException>(
                message = "Expected InvalidLineStringLengthException to be thrown",
                block = {
                    val encodedLine = encodeFromLngLatArray(arrayOf(doubleArrayOf(5.0, 5.0)))
                    decodeToLineString(encodedLine)
                })
        }
    }

    @Test
    fun testDecodeToPolygonWithUnderFourPositionsThrowsError() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            assertFailsWith<InvalidPolygonLengthException>(
                message = "Expected InvalidPolygonLengthException to be thrown",
                block = {
                    val encodedLine = encodeFromLngLatArray(
                        lngLatArray = arrayOf(
                            doubleArrayOf(5.0, 5.0),
                            doubleArrayOf(10.0, 10.0),
                            doubleArrayOf(5.0, 5.0)
                        )
                    )
                    decodeToPolygon(arrayOf(encodedLine))
                })
        }
    }

    @Test
    fun testDecodeToPolygonWithMismatchedStartEndThrowsError() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            assertFailsWith<InvalidPolygonClosureException>(
                message = "Expected InvalidPolygonClosureException to be thrown",
                block = {
                    val encodedLine = encodeFromLngLatArray(
                        lngLatArray = arrayOf(
                            doubleArrayOf(5.0, 5.0),
                            doubleArrayOf(10.0, 10.0),
                            doubleArrayOf(15.0, 15.0),
                            doubleArrayOf(20.0, 20.0)
                        )
                    )
                    decodeToPolygon(arrayOf(encodedLine))
                })
        }
    }

    @Test
    fun testDecodeToLineStringProducesValidResults() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            try {
                val coords = arrayOf(doubleArrayOf(132.0, -67.0), doubleArrayOf(38.0, 62.0))
                val encodedLine = encodeFromLngLatArray(coords)
                val geojson = decodeToLineString(encodedLine)

                validateLineString(geojson, coords)
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testDecodeToLineStringFeatureProducesValidResults() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            try {
                val coords = arrayOf(doubleArrayOf(132.0, -67.0), doubleArrayOf(38.0, 62.0))
                val encodedLine = encodeFromLngLatArray(lngLatArray = coords)
                val geojson = decodeToLineStringFeature(encodedLine)
                validateLineStringFeature(
                    geojson,
                    coords,
                    CompressionParameters(
                        precisionLngLat = if (algorithm == CompressionAlgorithm.Polyline5) 5 else DefaultPrecision
                    )
                )
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testDecodeToPolygonProducesValidResults() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            try {
                val coords = arrayOf(
                    doubleArrayOf(0.0, 0.0),
                    doubleArrayOf(10.0, 0.0),
                    doubleArrayOf(5.0, 10.0),
                    doubleArrayOf(0.0, 0.0)
                )
                val encodedRing = encodeFromLngLatArray(coords)
                val geojson = decodeToPolygon(arrayOf(encodedRing))
                validatePolygon(geojson, arrayOf(coords))
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testDecodeToPolygonFeatureProducesValidResults() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            try {
                val coords = arrayOf(
                    doubleArrayOf(0.0, 0.0),
                    doubleArrayOf(10.0, 0.0),
                    doubleArrayOf(5.0, 10.0),
                    doubleArrayOf(0.0, 0.0)
                )
                val encodedRing = encodeFromLngLatArray(coords)
                val geojson = decodeToPolygonFeature(arrayOf(encodedRing))
                validatePolygonFeature(
                    geojson,
                    arrayOf(coords),
                    CompressionParameters(
                        precisionLngLat = if (algorithm == CompressionAlgorithm.Polyline5) 5 else DefaultPrecision
                    )
                )
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testDecodeToPolygonWithCWOuterRingProducesCCWResult() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            try {
                val coords = arrayOf(
                    doubleArrayOf(0.0, 0.0),
                    doubleArrayOf(0.0, 10.0),
                    doubleArrayOf(10.0, 10.0),
                    doubleArrayOf(10.0, 0.0),
                    doubleArrayOf(0.0, 0.0)
                )
                val encodedRing = encodeFromLngLatArray(coords)
                val geojson = decodeToPolygon(arrayOf(encodedRing))
                val ccwCoords = arrayOf(
                    doubleArrayOf(0.0, 0.0),
                    doubleArrayOf(10.0, 0.0),
                    doubleArrayOf(10.0, 10.0),
                    doubleArrayOf(0.0, 10.0),
                    doubleArrayOf(0.0, 0.0)
                )
                validatePolygon(geojson, arrayOf(ccwCoords))
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testDecodeToPolygonWithCCWOuterRingProducesCCWResult() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            try {
                val coords = arrayOf(
                    doubleArrayOf(0.0, 0.0),
                    doubleArrayOf(10.0, 0.0),
                    doubleArrayOf(10.0, 10.0),
                    doubleArrayOf(0.0, 10.0),
                    doubleArrayOf(0.0, 0.0)
                )
                val encodedRing = encodeFromLngLatArray(coords)
                val geojson = decodeToPolygon(arrayOf(encodedRing))
                validatePolygon(geojson, arrayOf(coords))
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testDecodeToPolygonWithCWInnerRingsProducesCWResult() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            try {
                val clockwiseCoords = arrayOf(
                    arrayOf(
                        doubleArrayOf(0.0, 0.0),
                        doubleArrayOf(10.0, 0.0),
                        doubleArrayOf(10.0, 10.0),
                        doubleArrayOf(0.0, 10.0),
                        doubleArrayOf(0.0, 0.0)
                    ), // CCW outer ring
                    arrayOf(
                        doubleArrayOf(2.0, 2.0),
                        doubleArrayOf(2.0, 8.0),
                        doubleArrayOf(8.0, 8.0),
                        doubleArrayOf(8.0, 2.0),
                        doubleArrayOf(2.0, 2.0)
                    ), // CW inner ring
                    arrayOf(
                        doubleArrayOf(4.0, 4.0),
                        doubleArrayOf(4.0, 6.0),
                        doubleArrayOf(6.0, 6.0),
                        doubleArrayOf(6.0, 4.0),
                        doubleArrayOf(4.0, 4.0)
                    ) // CW inner ring
                )
                val encodedRings = mutableListOf<String>()
                for (ring in clockwiseCoords) {
                    encodedRings.add(encodeFromLngLatArray(ring))
                }
                val geojson = decodeToPolygon(encodedRings.toTypedArray())
                validatePolygon(geojson, clockwiseCoords)
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testDecodeToPolygonWithCCWInnerRingsProducesCWResult() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            try {
                val counterclockwiseCoords = arrayOf(
                    arrayOf(
                        doubleArrayOf(0.0, 0.0),
                        doubleArrayOf(10.0, 0.0),
                        doubleArrayOf(10.0, 10.0),
                        doubleArrayOf(0.0, 10.0),
                        doubleArrayOf(0.0, 0.0)
                    ), // CCW outer ring
                    arrayOf(
                        doubleArrayOf(2.0, 2.0),
                        doubleArrayOf(8.0, 2.0),
                        doubleArrayOf(8.0, 8.0),
                        doubleArrayOf(2.0, 8.0),
                        doubleArrayOf(2.0, 2.0)
                    ), // CCW inner ring
                    arrayOf(
                        doubleArrayOf(4.0, 4.0),
                        doubleArrayOf(6.0, 4.0),
                        doubleArrayOf(6.0, 6.0),
                        doubleArrayOf(4.0, 6.0),
                        doubleArrayOf(4.0, 4.0)
                    ) // CCW inner ring
                )
                val encodedRings = mutableListOf<String>()
                for (ring in counterclockwiseCoords) {
                    encodedRings.add(encodeFromLngLatArray(ring))
                }
                val geojson = decodeToPolygon(encodedRings.toTypedArray())
                val expectedCoords = arrayOf(
                    arrayOf(
                        doubleArrayOf(0.0, 0.0),
                        doubleArrayOf(10.0, 0.0),
                        doubleArrayOf(10.0, 10.0),
                        doubleArrayOf(0.0, 10.0),
                        doubleArrayOf(0.0, 0.0)
                    ), // CCW outer ring
                    arrayOf(
                        doubleArrayOf(2.0, 2.0),
                        doubleArrayOf(2.0, 8.0),
                        doubleArrayOf(8.0, 8.0),
                        doubleArrayOf(8.0, 2.0),
                        doubleArrayOf(2.0, 2.0)
                    ), // CW inner ring
                    arrayOf(
                        doubleArrayOf(4.0, 4.0),
                        doubleArrayOf(4.0, 6.0),
                        doubleArrayOf(6.0, 6.0),
                        doubleArrayOf(6.0, 4.0),
                        doubleArrayOf(4.0, 4.0)
                    ) // CW inner ring
                )
                validatePolygon(geojson, expectedCoords)
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testDecodeToLineStringWithRangesOfInputsProducesValidResults() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            try {
                val coords = arrayOf(
                    // A few different valid longitude values (positive, zero, negative)
                    doubleArrayOf(167.0, 5.0),
                    doubleArrayOf(0.0, 5.0),
                    doubleArrayOf(-167.0, 5.0),
                    // A few different valid latitude values (positive, zero, negative)
                    doubleArrayOf(5.0, 87.0),
                    doubleArrayOf(5.0, 0.0),
                    doubleArrayOf(5.0, -87.0),
                    // A few different high-precision values
                    doubleArrayOf(123.45678, 76.54321),
                    doubleArrayOf(-123.45678, -76.54321)
                )
                val encodedLine = encodeFromLngLatArray(coords)
                val geojson = decodeToLineString(encodedLine)

                validateLineString(geojson, coords)
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testDecodeToPolygonWithRangesOfInputsProducesValidResults() {
        for (algorithm in algorithms) {
            setCompressionAlgorithm(algorithm)

            try {
                val coords = arrayOf(
                    // A few different valid longitude values (positive, zero, negative)
                    doubleArrayOf(167.0, 5.0),
                    doubleArrayOf(0.0, 5.0),
                    doubleArrayOf(-167.0, 5.0),
                    // A few different valid latitude values (positive, zero, negative)
                    doubleArrayOf(5.0, 87.0),
                    doubleArrayOf(5.0, 0.0),
                    doubleArrayOf(5.0, -87.0),
                    // A few different high-precision values
                    doubleArrayOf(123.45678, 76.54321),
                    doubleArrayOf(-123.45678, -76.54321),
                    // Close the polygon ring
                    doubleArrayOf(167.0, 5.0)
                )
                val encodedLine = encodeFromLngLatArray(coords)
                val geojson = decodeToPolygon(arrayOf(encodedLine))
                validatePolygon(geojson, arrayOf(coords))
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testFlexiblePolylineDecodeInvalidHeaderThrowsError() {
        setCompressionAlgorithm(CompressionAlgorithm.FlexiblePolyline)
        val invalidStrings = arrayOf(
            "AGgsmytFg0lxJ_rmytF_zlxJ", // Header version = 0
            "CGgsmytFg0lxJ_rmytF_zlxJ"  // Header version = 2
        )
        for (invalidString in invalidStrings) {
            assertFailsWith<InvalidHeaderVersionException>(
                message = "Expected InvalidHeaderVersionException",
                block = {
                    decodeToLngLatArray(invalidString)

                })
        }
    }

    @Test
    fun testFlexiblePolylineDecodeInvalidValuesThrowsError() {
        setCompressionAlgorithm(CompressionAlgorithm.FlexiblePolyline)
        val invalidStrings = arrayOf(
            "BGg0lxJ_zrn5K_zlxJg0rn5K", // [[-181, 5], [0, 0]] - longitude too low
            "BGg0lxJg0rn5K_zlxJ_zrn5K", // [[181, 5], [0, 0]] - longitude too high
            "BG_rmytFg0lxJgsmytF_zlxJ", // [[5, -91], [0, 0]] - latitude too low
            "BGgsmytFg0lxJ_rmytF_zlxJ", // [[5, 91], [0, 0]] - latitude too high
        )
        for (invalidString in invalidStrings) {
            assertFailsWith<InvalidCoordinateValueException>(
                message = "Expected InvalidCoordinateValueException",
                block = {
                    decodeToLngLatArray(invalidString)

                })
        }
    }

    @Test
    fun testPolyline5DecodeInvalidValuesThrowsError() {
        setCompressionAlgorithm(CompressionAlgorithm.Polyline5)
        val invalidStrings = arrayOf(
            "_qo]~pvoa@~po]_qvoa@", // [[-181, 5], [0, 0]] - longitude too low
            "_qo]_qvoa@~po]~pvoa@", // [[181, 5], [0, 0]] - longitude too high
            "~lljP_qo]_mljP~po]", // [[5, -91], [0, 0]] - latitude too low
            "_mljP_qo]~lljP~po]", // [[5, 91], [0, 0]] - latitude too high
        )
        for (invalidString in invalidStrings) {
            assertFailsWith<InvalidCoordinateValueException>(
                message = "Expected InvalidCoordinateValueException",
                block = {
                    decodeToLngLatArray(invalidString)

                })
        }
    }

    @Test
    fun testPolyline6DecodeInvalidValuesThrowsError() {
        setCompressionAlgorithm(CompressionAlgorithm.Polyline6)
        val invalidStrings = arrayOf(
            "_sdpH~rjfxI~rdpH_sjfxI", // [[-181, 5], [0, 0]] - longitude too low
            "_sdpH_sjfxI~rdpH~rjfxI", // [[181, 5], [0, 0]] - longitude too high
            "~jeqlD_sdpH_keqlD~rdpH", // [[5, -91], [0, 0]] - latitude too low
            "_keqlD_sdpH~jeqlD~rdpH", // [[5, 91], [0, 0]] - latitude too high
        )
        for (invalidString in invalidStrings) {
            assertFailsWith<InvalidCoordinateValueException>(
                message = "Expected InvalidCoordinateValueException",
                block = {
                    decodeToLngLatArray(invalidString)

                })
        }
    }

    @Test
    fun testFlexiblePolylineLngLatArrayHandlesThirdDimensionTypes() {
        setCompressionAlgorithm(CompressionAlgorithm.FlexiblePolyline)
        val coords = arrayOf(
            doubleArrayOf(0.0, 0.0, 5.0),
            doubleArrayOf(10.0, 0.0, 0.0),
            doubleArrayOf(10.0, 10.0, -5.0),
            doubleArrayOf(0.0, 10.0, 0.0),
            doubleArrayOf(0.0, 0.0, 5.0)
        )
        for (thirdDimension in arrayOf(ThirdDimension.Level, ThirdDimension.Altitude, ThirdDimension.Elevation)) {
            try {
                val encodedLine = encodeFromLngLatArray(
                    lngLatArray = coords,
                    parameters = CompressionParameters(thirdDimension = thirdDimension)
                )
                val result = decodeToLngLatArray(encodedLine)
                assertTrue(result.contentDeepEquals(coords))
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testFlexiblePolylineLineStringHandlesThirdDimensionTypes() {
        setCompressionAlgorithm(CompressionAlgorithm.FlexiblePolyline)
        val coords = arrayOf(
            doubleArrayOf(0.0, 0.0, 5.0),
            doubleArrayOf(10.0, 0.0, 0.0),
            doubleArrayOf(10.0, 10.0, -5.0),
            doubleArrayOf(0.0, 10.0, 0.0),
            doubleArrayOf(0.0, 0.0, 5.0)
        )
        for (thirdDimension in arrayOf(ThirdDimension.Level, ThirdDimension.Altitude, ThirdDimension.Elevation)) {
            try {
                val encodedLine = encodeFromLngLatArray(
                    lngLatArray = coords,
                    parameters = CompressionParameters(thirdDimension = thirdDimension)
                )
                val geojson = decodeToLineString(encodedLine)
                validateLineString(geojson, coords)
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testFlexiblePolylineLineStringFeatureHandlesThirdDimensionTypes() {
        setCompressionAlgorithm(CompressionAlgorithm.FlexiblePolyline)
        val coords = arrayOf(
            doubleArrayOf(0.0, 0.0, 5.0),
            doubleArrayOf(10.0, 0.0, 0.0),
            doubleArrayOf(10.0, 10.0, -5.0),
            doubleArrayOf(0.0, 10.0, 0.0),
            doubleArrayOf(0.0, 0.0, 5.0)
        )
        for (thirdDimension in arrayOf(ThirdDimension.Level, ThirdDimension.Altitude, ThirdDimension.Elevation)) {
            try {
                val parameters = CompressionParameters(thirdDimension = thirdDimension)
                val encodedLine = encodeFromLngLatArray(coords, parameters)
                val geojson = decodeToLineStringFeature(encodedLine)
                validateLineStringFeature(geojson, coords, parameters)
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testFlexiblePolylinePolygonHandlesThirdDimensionTypes() {
        setCompressionAlgorithm(CompressionAlgorithm.FlexiblePolyline)
        val ringCoords = arrayOf(
            arrayOf(
                doubleArrayOf(0.0, 0.0, 5.0),
                doubleArrayOf(10.0, 0.0, 0.0),
                doubleArrayOf(10.0, 10.0, -5.0),
                doubleArrayOf(0.0, 10.0, 0.0),
                doubleArrayOf(0.0, 0.0, 5.0)
            ), // outer ring
            arrayOf(
                doubleArrayOf(2.0, 2.0, 5.0),
                doubleArrayOf(2.0, 8.0, 0.0),
                doubleArrayOf(8.0, 8.0, -5.0),
                doubleArrayOf(8.0, 2.0, 0.0),
                doubleArrayOf(2.0, 2.0, 5.0)
            ) // inner ring
        )
        for (thirdDimension in arrayOf(ThirdDimension.Level, ThirdDimension.Altitude, ThirdDimension.Elevation)) {
            try {
                val encodedRings = mutableListOf<String>()
                for (ring in ringCoords) {
                    encodedRings.add(
                        encodeFromLngLatArray(
                            ring,
                            CompressionParameters(thirdDimension = thirdDimension)
                        )
                    )
                }
                val geojson = decodeToPolygon(encodedRings.toTypedArray())
                validatePolygon(geojson, ringCoords)
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testFlexiblePolylinePolygonFeatureHandlesThirdDimensionTypes() {
        setCompressionAlgorithm(CompressionAlgorithm.FlexiblePolyline)
        val ringCoords = arrayOf(
            arrayOf(
                doubleArrayOf(0.0, 0.0, 5.0),
                doubleArrayOf(10.0, 0.0, 0.0),
                doubleArrayOf(10.0, 10.0, -5.0),
                doubleArrayOf(0.0, 10.0, 0.0),
                doubleArrayOf(0.0, 0.0, 5.0)
            ), // outer ring
            arrayOf(
                doubleArrayOf(2.0, 2.0, 5.0),
                doubleArrayOf(2.0, 8.0, 0.0),
                doubleArrayOf(8.0, 8.0, -5.0),
                doubleArrayOf(8.0, 2.0, 0.0),
                doubleArrayOf(2.0, 2.0, 5.0)
            ) // inner ring
        )
        for (thirdDimension in arrayOf(ThirdDimension.Level, ThirdDimension.Altitude, ThirdDimension.Elevation)) {
            try {
                val parameters = CompressionParameters(thirdDimension = thirdDimension)
                val encodedRings = mutableListOf<String>()
                for (ring in ringCoords) {
                    encodedRings.add(
                        encodeFromLngLatArray(ring, parameters)
                    )
                }
                val geojson = decodeToPolygonFeature(encodedRings.toTypedArray())
                validatePolygonFeature(geojson, ringCoords, parameters)
            } catch (e: Exception) {
                fail("Unexpected error")
            }
        }
    }

    @Test
    fun testPolylineErrorsOnThreeDimensions() {
        val coords = arrayOf(
            doubleArrayOf(0.0, 0.0, 5.0),
            doubleArrayOf(10.0, 0.0, 0.0),
            doubleArrayOf(10.0, 10.0, -5.0),
            doubleArrayOf(0.0, 10.0, 0.0),
            doubleArrayOf(0.0, 0.0, 5.0)
        )
        for (algorithm in arrayOf(CompressionAlgorithm.Polyline5, CompressionAlgorithm.Polyline6)) {
            setCompressionAlgorithm(algorithm)
            assertFailsWith<InconsistentCoordinateDimensionsException>(
                message = "Expected InconsistentCoordinateDimensionsException",
                block = {
                encodeFromLngLatArray(
                    coords,
                    CompressionParameters(thirdDimension = ThirdDimension.Altitude)
                )
                })
        }
    }

    @Test
    fun testFlexiblePolylineEncodeThrowsErrorWithNegative2DPrecision() {
        setCompressionAlgorithm(CompressionAlgorithm.FlexiblePolyline)

        val coords = arrayOf(doubleArrayOf(0.0, 0.0, 5.0), doubleArrayOf(10.0, 0.0, 0.0))
        assertFailsWith<InvalidPrecisionValueException>(
            message = "Expected InvalidPrecisionValueException",
            block = {
                encodeFromLngLatArray(
                    coords,
                    CompressionParameters(precisionLngLat = -5)
                )
            })
    }

    @Test
    fun testFlexiblePolylineEncodeThrowsErrorWithNegative3DPrecision() {
        setCompressionAlgorithm(CompressionAlgorithm.FlexiblePolyline)

        val coords = arrayOf(doubleArrayOf(0.0, 0.0, 5.0), doubleArrayOf(10.0, 0.0, 0.0))
        assertFailsWith<InvalidPrecisionValueException>(
            message = "Expected InvalidPrecisionValueException",
            block = {
                encodeFromLngLatArray(
                    coords,
                    CompressionParameters(precisionThirdDimension = -5)
                )
            })
    }
}