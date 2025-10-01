package software.amazon.location.polyline

import com.google.gson.Gson
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private val algorithms: List<Polyline.CompressionAlgorithm> = listOf(
        Polyline.CompressionAlgorithm.FlexiblePolyline,
        Polyline.CompressionAlgorithm.Polyline5,
        Polyline.CompressionAlgorithm.Polyline6)

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

    private fun validateProperties(properties: Properties, parameters: Polyline.CompressionParameters) {
        assertEquals(properties.precision, parameters.precisionLngLat)
        assertEquals(properties.thirdDimensionPrecision != null, parameters.thirdDimension != Polyline.ThirdDimension.None)
        if (properties.thirdDimensionPrecision != null) {
            assertEquals(properties.thirdDimensionPrecision, parameters.precisionThirdDimension)
        }
        assertEquals(properties.thirdDimensionType != null, parameters.thirdDimension != Polyline.ThirdDimension.None)
        if (properties.thirdDimensionType != null) {
            when (properties.thirdDimensionType) {
                "level" -> assertEquals(parameters.thirdDimension, Polyline.ThirdDimension.Level)
                "altitude" -> assertEquals(parameters.thirdDimension, Polyline.ThirdDimension.Altitude)
                "elevation" -> assertEquals(parameters.thirdDimension, Polyline.ThirdDimension.Elevation)
                else -> fail("Unknown third dimension type")
            }
            assertEquals(properties.thirdDimensionPrecision, parameters.precisionThirdDimension)
        }
    }

    private fun validateLineStringFeature(geojson: String, coords: Array<DoubleArray>, parameters: Polyline.CompressionParameters) {
        val lineStringFeature = Gson().fromJson(geojson, LineStringFeature::class.java)
        assertEquals(lineStringFeature.type, "Feature")
        assertEquals(lineStringFeature.geometry.type, "LineString")
        assertTrue(lineStringFeature.geometry.coordinates.contentDeepEquals(coords))
        validateProperties(lineStringFeature.properties, parameters)
    }

    private fun validatePolygonFeature(geojson: String, coords: Array<Array<DoubleArray>>, parameters: Polyline.CompressionParameters) {
        val polygonFeature = Gson().fromJson(geojson, PolygonFeature::class.java)
        assertEquals(polygonFeature.type, "Feature")
        assertEquals(polygonFeature.geometry.type, "Polygon")
        assertTrue(polygonFeature.geometry.coordinates.contentDeepEquals(coords))
        validateProperties(polygonFeature.properties, parameters)
    }

    @BeforeEach
    fun setup() {
        // Reset the compression algorithm back to the default for each unit test.
        Polyline.setCompressionAlgorithm()
    }

    @Test
    fun testDefaultAlgorithmIsFlexiblePolyline() {
        assertEquals(Polyline.getCompressionAlgorithm(), Polyline.CompressionAlgorithm.FlexiblePolyline)
    }

    @Test
    fun testSettingAlgorithmToFlexiblePolyline() {
        // Since we default to FlexiblePolyline first set to something other than FlexiblePolyline
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.Polyline5)
        // Now set back to FlexiblePolyline
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.FlexiblePolyline)
        assertEquals(Polyline.getCompressionAlgorithm(), Polyline.CompressionAlgorithm.FlexiblePolyline)
    }

    @Test
    fun testSettingToNonDefaultAlgorithm() {
        val nonDefaultAlgorithms = listOf(Polyline.CompressionAlgorithm.Polyline5, Polyline.CompressionAlgorithm.Polyline6)

        for (algorithm in nonDefaultAlgorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            assertEquals(Polyline.getCompressionAlgorithm(), algorithm)
        }
    }

    @Test
    fun testDecodingEmptyDataThrowsError() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            when (val result = Polyline.decodeToLineString("")) {
                is Polyline.DecodeToGeoJsonResult.Success -> fail("Expected error result")
                is Polyline.DecodeToGeoJsonResult.Error -> assertEquals(result.error, Polyline.DecodeError.EmptyInput)
            }
        }
    }

    @Test
    fun testDecodingBadDataThrowsError() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            // The characters in the string below are invalid for each of the decoding algorithms.
            // For polyline5/polyline6, only ?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~ are valid.
            // For flexiblePolyline, only ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_ are valid.
            when (val result = Polyline.decodeToLineString("!#$%(*)&")) {
                is Polyline.DecodeToGeoJsonResult.Success -> fail("Expected error result")
                is Polyline.DecodeToGeoJsonResult.Error -> assertEquals(
                    result.error,
                    Polyline.DecodeError.InvalidEncodedCharacter
                )
            }
        }
    }

    @Test
    fun testEncodingInputPointValuesAreValidated() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)

            // Longitude too low
            when (val result = Polyline.encodeFromLngLatArray(
                arrayOf(doubleArrayOf(-181.0, 5.0), doubleArrayOf(0.0, 0.0)))) {
                is Polyline.EncodeResult.Success -> fail("Expected error result")
                is Polyline.EncodeResult.Error -> assertEquals(
                    result.error,
                    Polyline.EncodeError.InvalidCoordinateValue
                )
            }

            // Longitude too high
            when (val result = Polyline.encodeFromLngLatArray(
                arrayOf(doubleArrayOf(181.0, 5.0), doubleArrayOf(0.0, 0.0)))) {
                is Polyline.EncodeResult.Success -> fail("Expected error result")
                is Polyline.EncodeResult.Error -> assertEquals(
                    result.error,
                    Polyline.EncodeError.InvalidCoordinateValue
                )
            }

            // Latitude too low
            when (val result = Polyline.encodeFromLngLatArray(
                arrayOf(doubleArrayOf(5.0, -91.0), doubleArrayOf(0.0, 0.0)))) {
                is Polyline.EncodeResult.Success -> fail("Expected error result")
                is Polyline.EncodeResult.Error -> assertEquals(
                    result.error,
                    Polyline.EncodeError.InvalidCoordinateValue
                )
            }

            // Latitude too high
            when (val result = Polyline.encodeFromLngLatArray(
                arrayOf(doubleArrayOf(5.0, 91.0), doubleArrayOf(0.0, 0.0)))) {
                is Polyline.EncodeResult.Success -> fail("Expected error result")
                is Polyline.EncodeResult.Error -> assertEquals(
                    result.error,
                    Polyline.EncodeError.InvalidCoordinateValue
                )
            }
        }
    }

    @Test
    fun testEncodingMixedDimensionalityThrowsError() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)

            // Mixing 2D and 3D throws error
            when (val result = Polyline.encodeFromLngLatArray(
                arrayOf(doubleArrayOf(5.0, 5.0), doubleArrayOf(10.0, 10.0, 10.0)))) {
                is Polyline.EncodeResult.Success -> fail("Expected error result")
                is Polyline.EncodeResult.Error -> assertEquals(
                    result.error,
                    Polyline.EncodeError.InconsistentCoordinateDimensions
                )
            }

            // Mixing 3D and 2D throws error
            when (val result = Polyline.encodeFromLngLatArray(
                arrayOf(doubleArrayOf(5.0, 5.0, 5.0), doubleArrayOf(10.0, 10.0)))) {
                is Polyline.EncodeResult.Success -> fail("Expected error result")
                is Polyline.EncodeResult.Error -> assertEquals(
                    result.error,
                    Polyline.EncodeError.InconsistentCoordinateDimensions
                )
            }
        }
    }

    @Test
    fun testEncodingUnsupportedDimensionsThrowsError() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)

            // 1D throws error
            when (val result = Polyline.encodeFromLngLatArray(
                arrayOf(doubleArrayOf(5.0), doubleArrayOf(10.0)))) {
                is Polyline.EncodeResult.Success -> fail("Expected error result")
                is Polyline.EncodeResult.Error -> assertEquals(
                    result.error,
                    Polyline.EncodeError.InconsistentCoordinateDimensions
                )
            }

            // 4D throws error
            when (val result = Polyline.encodeFromLngLatArray(
                arrayOf(doubleArrayOf(5.0, 5.0, 5.0), doubleArrayOf(10.0, 10.0, 10.0, 10.0)))) {
                is Polyline.EncodeResult.Success -> fail("Expected error result")
                is Polyline.EncodeResult.Error -> assertEquals(
                    result.error,
                    Polyline.EncodeError.InconsistentCoordinateDimensions
                )
            }
        }
    }

    @Test
    fun testEncodingEmptyInputProducesEmptyResults() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            when (val result = Polyline.encodeFromLngLatArray(emptyArray())) {
                is Polyline.EncodeResult.Success -> assertEquals("", result.encodedData)
                is Polyline.EncodeResult.Error -> fail("Expected success")
            }
        }
    }

    @Test
    fun testDecodeToLineStringWithOnePositionThrowsError() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            val encodedLine = when (val result = Polyline.encodeFromLngLatArray(arrayOf(doubleArrayOf(5.0, 5.0)))) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToLineString(encodedLine)) {
                is Polyline.DecodeToGeoJsonResult.Success -> fail("Expected decode failure")
                is Polyline.DecodeToGeoJsonResult.Error -> assertEquals(decodeResult.error, Polyline.DecodeError.InvalidLineStringLength)
            }
        }
    }

    @Test
    fun testDecodeToPolygonWithUnderFourPositionsThrowsError() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            val encodedPolygon = when (val result = Polyline.encodeFromLngLatArray(arrayOf(
                doubleArrayOf(5.0, 5.0),
                doubleArrayOf(10.0, 10.0),
                doubleArrayOf(5.0, 5.0)))) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToPolygon(arrayOf(encodedPolygon))) {
                is Polyline.DecodeToGeoJsonResult.Success -> fail("Expected decode failure")
                is Polyline.DecodeToGeoJsonResult.Error -> assertEquals(decodeResult.error, Polyline.DecodeError.InvalidPolygonLength)
            }
        }
    }

    @Test
    fun testDecodeToPolygonWithMismatchedStartEndThrowsError() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            val encodedPolygon = when (val result = Polyline.encodeFromLngLatArray(arrayOf(
                doubleArrayOf(5.0, 5.0),
                doubleArrayOf(10.0, 10.0),
                doubleArrayOf(15.0, 15.0),
                doubleArrayOf(20.0, 20.0)
                ))) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToPolygon(arrayOf(encodedPolygon))) {
                is Polyline.DecodeToGeoJsonResult.Success -> fail("Expected decode failure")
                is Polyline.DecodeToGeoJsonResult.Error -> assertEquals(decodeResult.error, Polyline.DecodeError.InvalidPolygonClosure)
            }
        }
    }

    @Test
    fun testDecodeToLineStringProducesValidResults() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            val coords = arrayOf(doubleArrayOf(132.0, -67.0), doubleArrayOf(38.0, 62.0))
            val encodedLine = when (val result = Polyline.encodeFromLngLatArray(coords)) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToLineString(encodedLine)) {
                is Polyline.DecodeToGeoJsonResult.Success -> validateLineString(decodeResult.geojson, coords)
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testDecodeToLineStringFeatureProducesValidResults() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            val coords = arrayOf(doubleArrayOf(132.0, -67.0), doubleArrayOf(38.0, 62.0))
            val encodedLine = when (val result = Polyline.encodeFromLngLatArray(coords)) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToLineStringFeature(encodedLine)) {
                is Polyline.DecodeToGeoJsonResult.Success -> {
                    validateLineStringFeature(
                        decodeResult.geojson,
                        coords,
                        Polyline.CompressionParameters(
                            precisionLngLat = if (algorithm == Polyline.CompressionAlgorithm.Polyline5) 5 else 6
                        )
                    )
                }

                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testDecodeToPolygonProducesValidResults() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            val coords = arrayOf(
                doubleArrayOf(0.0, 0.0),
                doubleArrayOf(10.0, 0.0),
                doubleArrayOf(5.0, 10.0),
                doubleArrayOf(0.0, 0.0)
            )
            val encodedPolygon = when (val result = Polyline.encodeFromLngLatArray(coords)) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToPolygon(arrayOf(encodedPolygon))) {
                is Polyline.DecodeToGeoJsonResult.Success -> validatePolygon(decodeResult.geojson, arrayOf(coords))
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testDecodeToPolygonFeatureProducesValidResults() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            val coords = arrayOf(
                doubleArrayOf(0.0, 0.0),
                doubleArrayOf(10.0, 0.0),
                doubleArrayOf(5.0, 10.0),
                doubleArrayOf(0.0, 0.0)
            )
            val encodedPolygon = when (val result = Polyline.encodeFromLngLatArray(coords)) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToPolygonFeature(arrayOf(encodedPolygon))) {
                is Polyline.DecodeToGeoJsonResult.Success -> {
                    validatePolygonFeature(
                        decodeResult.geojson,
                        arrayOf(coords),
                        Polyline.CompressionParameters(
                            precisionLngLat = if (algorithm == Polyline.CompressionAlgorithm.Polyline5) 5 else 6
                        )
                    )
                }
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testDecodeToPolygonWithCWOuterRingProducesCCWResult() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            val coords = arrayOf(
                doubleArrayOf(0.0, 0.0),
                doubleArrayOf(0.0, 10.0),
                doubleArrayOf(10.0, 10.0),
                doubleArrayOf(10.0, 0.0),
                doubleArrayOf(0.0, 0.0)
            )
            val ccwCoords = arrayOf(
                doubleArrayOf(0.0, 0.0),
                doubleArrayOf(10.0, 0.0),
                doubleArrayOf(10.0, 10.0),
                doubleArrayOf(0.0, 10.0),
                doubleArrayOf(0.0, 0.0)
            )
            val encodedPolygon = when (val result = Polyline.encodeFromLngLatArray(coords)) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToPolygon(arrayOf(encodedPolygon))) {
                is Polyline.DecodeToGeoJsonResult.Success -> {
                    validatePolygon(
                        decodeResult.geojson,
                        arrayOf(ccwCoords)
                    )
                }
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testDecodeToPolygonWithCCWOuterRingProducesCCWResult() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
            val coords = arrayOf(
                doubleArrayOf(0.0, 0.0),
                doubleArrayOf(10.0, 0.0),
                doubleArrayOf(10.0, 10.0),
                doubleArrayOf(0.0, 10.0),
                doubleArrayOf(0.0, 0.0)
            )
            val encodedRing = when (val result = Polyline.encodeFromLngLatArray(coords)) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToPolygon((arrayOf(encodedRing)))) {
                is Polyline.DecodeToGeoJsonResult.Success -> validatePolygon(
                    decodeResult.geojson,
                    arrayOf(coords)
                )
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testDecodeToPolygonWithCWInnerRingsProducesCWResult() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
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
                when (val result = Polyline.encodeFromLngLatArray((ring))) {
                    is Polyline.EncodeResult.Success -> encodedRings.add(result.encodedData)
                    is Polyline.EncodeResult.Error -> fail("Expected encode success")
                }
            }
            when (val decodeResult = Polyline.decodeToPolygon(encodedRings.toTypedArray())) {
                is Polyline.DecodeToGeoJsonResult.Success -> validatePolygon(decodeResult.geojson, clockwiseCoords)
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testDecodeToPolygonWithCCWInnerRingsProducesCWResult() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
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
                when (val result = Polyline.encodeFromLngLatArray((ring))) {
                    is Polyline.EncodeResult.Success -> encodedRings.add(result.encodedData)
                    is Polyline.EncodeResult.Error -> fail("Expected encode success")
                }
            }
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
            when (val decodeResult = Polyline.decodeToPolygon(encodedRings.toTypedArray())) {
                is Polyline.DecodeToGeoJsonResult.Success -> validatePolygon(decodeResult.geojson, expectedCoords)
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testDecodeToLineStringWithRangesOfInputsProducesValidResults() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
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
            val encodedLine = when (val result = Polyline.encodeFromLngLatArray(coords)) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToLineString(encodedLine)) {
                is Polyline.DecodeToGeoJsonResult.Success -> validateLineString(decodeResult.geojson, coords)
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testDecodeToPolygonWithRangesOfInputsProducesValidResults() {
        for (algorithm in algorithms) {
            Polyline.setCompressionAlgorithm(algorithm)
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
            val encodedLine = when (val result = Polyline.encodeFromLngLatArray(coords)) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToPolygon(arrayOf(encodedLine))) {
                is Polyline.DecodeToGeoJsonResult.Success -> validatePolygon(decodeResult.geojson, arrayOf(coords))
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testFlexiblePolylineDecodeInvalidHeaderThrowsError() {
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.FlexiblePolyline)
        val invalidStrings = arrayOf(
            "AGgsmytFg0lxJ_rmytF_zlxJ", // Header version = 0
            "CGgsmytFg0lxJ_rmytF_zlxJ"  // Header version = 2
        )
        for (invalidString in invalidStrings) {
            when (val result = Polyline.decodeToLngLatArray(invalidString)) {
                is Polyline.DecodeToArrayResult.Success -> fail("Expected decode error")
                is Polyline.DecodeToArrayResult.Error -> assertEquals(Polyline.DecodeError.InvalidHeaderVersion, result.error)
            }
        }
    }

    @Test
    fun testFlexiblePolylineDecodeInvalidValuesThrowsError() {
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.FlexiblePolyline)
        val invalidStrings = arrayOf(
            "BGg0lxJ_zrn5K_zlxJg0rn5K", // [[-181, 5], [0, 0]] - longitude too low
            "BGg0lxJg0rn5K_zlxJ_zrn5K", // [[181, 5], [0, 0]] - longitude too high
            "BG_rmytFg0lxJgsmytF_zlxJ", // [[5, -91], [0, 0]] - latitude too low
            "BGgsmytFg0lxJ_rmytF_zlxJ", // [[5, 91], [0, 0]] - latitude too high
        )
        for (invalidString in invalidStrings) {
            when (val result = Polyline.decodeToLngLatArray(invalidString)) {
                is Polyline.DecodeToArrayResult.Success -> fail("Expected decode error")
                is Polyline.DecodeToArrayResult.Error -> assertEquals(Polyline.DecodeError.InvalidCoordinateValue, result.error)
            }
        }
    }

    @Test
    fun testPolyline5DecodeInvalidValuesThrowsError() {
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.Polyline5)
        val invalidStrings = arrayOf(
            "_qo]~pvoa@~po]_qvoa@", // [[-181, 5], [0, 0]] - longitude too low
            "_qo]_qvoa@~po]~pvoa@", // [[181, 5], [0, 0]] - longitude too high
            "~lljP_qo]_mljP~po]", // [[5, -91], [0, 0]] - latitude too low
            "_mljP_qo]~lljP~po]", // [[5, 91], [0, 0]] - latitude too high
        )
        for (invalidString in invalidStrings) {
            when (val result = Polyline.decodeToLngLatArray(invalidString)) {
                is Polyline.DecodeToArrayResult.Success -> fail("Expected decode error")
                is Polyline.DecodeToArrayResult.Error -> assertEquals(Polyline.DecodeError.InvalidCoordinateValue, result.error)
            }
        }
    }

    @Test
    fun testPolyline6DecodeInvalidValuesThrowsError() {
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.Polyline6)
        val invalidStrings = arrayOf(
            "_sdpH~rjfxI~rdpH_sjfxI", // [[-181, 5], [0, 0]] - longitude too low
            "_sdpH_sjfxI~rdpH~rjfxI", // [[181, 5], [0, 0]] - longitude too high
            "~jeqlD_sdpH_keqlD~rdpH", // [[5, -91], [0, 0]] - latitude too low
            "_keqlD_sdpH~jeqlD~rdpH", // [[5, 91], [0, 0]] - latitude too high
        )
        for (invalidString in invalidStrings) {
            when (val result = Polyline.decodeToLngLatArray(invalidString)) {
                is Polyline.DecodeToArrayResult.Success -> fail("Expected decode error")
                is Polyline.DecodeToArrayResult.Error -> assertEquals(Polyline.DecodeError.InvalidCoordinateValue, result.error)
            }
        }
    }

    @Test
    fun testFlexiblePolylineLngLatArrayHandlesThirdDimensionTypes() {
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.FlexiblePolyline)
        val coords = arrayOf(
            doubleArrayOf(0.0, 0.0, 5.0),
            doubleArrayOf(10.0, 0.0, 0.0),
            doubleArrayOf(10.0, 10.0, -5.0),
            doubleArrayOf(0.0, 10.0, 0.0),
            doubleArrayOf(0.0, 0.0, 5.0)
        )
        for (thirdDimension in arrayOf(Polyline.ThirdDimension.Level, Polyline.ThirdDimension.Altitude, Polyline.ThirdDimension.Elevation)) {
            val encodedLine = when (val result = Polyline.encodeFromLngLatArray(
                    lngLatArray = coords,
                    parameters = Polyline.CompressionParameters(thirdDimension = thirdDimension)
                )) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToLngLatArray(encodedLine)) {
                is Polyline.DecodeToArrayResult.Success -> assertTrue(decodeResult.lngLatArray.contentDeepEquals(coords))
                is Polyline.DecodeToArrayResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testFlexiblePolylineLineStringHandlesThirdDimensionTypes() {
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.FlexiblePolyline)
        val coords = arrayOf(
            doubleArrayOf(0.0, 0.0, 5.0),
            doubleArrayOf(10.0, 0.0, 0.0),
            doubleArrayOf(10.0, 10.0, -5.0),
            doubleArrayOf(0.0, 10.0, 0.0),
            doubleArrayOf(0.0, 0.0, 5.0)
        )
        for (thirdDimension in arrayOf(Polyline.ThirdDimension.Level, Polyline.ThirdDimension.Altitude, Polyline.ThirdDimension.Elevation)) {
            val encodedLine = when (val result = Polyline.encodeFromLngLatArray(
                lngLatArray = coords,
                parameters = Polyline.CompressionParameters(thirdDimension = thirdDimension)
            )) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToLineString(encodedLine)) {
                is Polyline.DecodeToGeoJsonResult.Success -> validateLineString(decodeResult.geojson, coords)
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testFlexiblePolylineLineStringFeatureHandlesThirdDimensionTypes() {
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.FlexiblePolyline)
        val coords = arrayOf(
            doubleArrayOf(0.0, 0.0, 5.0),
            doubleArrayOf(10.0, 0.0, 0.0),
            doubleArrayOf(10.0, 10.0, -5.0),
            doubleArrayOf(0.0, 10.0, 0.0),
            doubleArrayOf(0.0, 0.0, 5.0)
        )
        for (thirdDimension in arrayOf(Polyline.ThirdDimension.Level, Polyline.ThirdDimension.Altitude, Polyline.ThirdDimension.Elevation)) {
            val parameters = Polyline.CompressionParameters(thirdDimension = thirdDimension)
            val encodedLine = when (val result = Polyline.encodeFromLngLatArray(coords, parameters)) {
                is Polyline.EncodeResult.Success -> result.encodedData
                is Polyline.EncodeResult.Error -> fail("Expected encode success")
            }
            when (val decodeResult = Polyline.decodeToLineStringFeature(encodedLine)) {
                is Polyline.DecodeToGeoJsonResult.Success -> validateLineStringFeature(decodeResult.geojson, coords, parameters)
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testFlexiblePolylinePolygonHandlesThirdDimensionTypes() {
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.FlexiblePolyline)
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
        for (thirdDimension in arrayOf(Polyline.ThirdDimension.Level, Polyline.ThirdDimension.Altitude, Polyline.ThirdDimension.Elevation)) {
            val encodedRings = mutableListOf<String>()
            for (ring in ringCoords) {
                val encodedRing = when (val result = Polyline.encodeFromLngLatArray(
                        ring,
                        Polyline.CompressionParameters(thirdDimension = thirdDimension)
                    )
                ) {
                    is Polyline.EncodeResult.Success -> result.encodedData
                    is Polyline.EncodeResult.Error -> fail("Expected encode success")
                }
                encodedRings.add(encodedRing)
            }
            when (val decodeResult = Polyline.decodeToPolygon(encodedRings.toTypedArray())) {
                is Polyline.DecodeToGeoJsonResult.Success -> validatePolygon(
                    decodeResult.geojson,
                    ringCoords
                )
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
            }
        }
    }

    @Test
    fun testFlexiblePolylinePolygonFeatureHandlesThirdDimensionTypes() {
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.FlexiblePolyline)
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
        for (thirdDimension in arrayOf(Polyline.ThirdDimension.Level, Polyline.ThirdDimension.Altitude, Polyline.ThirdDimension.Elevation)) {
            val parameters = Polyline.CompressionParameters(thirdDimension = thirdDimension)
            val encodedRings = mutableListOf<String>()
            for (ring in ringCoords) {
                val encodedRing = when (val result = Polyline.encodeFromLngLatArray(ring, parameters)) {
                    is Polyline.EncodeResult.Success -> result.encodedData
                    is Polyline.EncodeResult.Error -> fail("Expected encode success")
                }
                encodedRings.add(encodedRing)
            }
            when (val decodeResult = Polyline.decodeToPolygonFeature(encodedRings.toTypedArray())) {
                is Polyline.DecodeToGeoJsonResult.Success -> validatePolygonFeature(
                    decodeResult.geojson,
                    ringCoords,
                    parameters
                )
                is Polyline.DecodeToGeoJsonResult.Error -> fail("Expected decode success")
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
        for (algorithm in arrayOf(Polyline.CompressionAlgorithm.Polyline5, Polyline.CompressionAlgorithm.Polyline6)) {
            Polyline.setCompressionAlgorithm(algorithm)
            when (val result = Polyline.encodeFromLngLatArray(
                    coords,
                        Polyline.CompressionParameters(thirdDimension = Polyline.ThirdDimension.Altitude)
                )) {
                is Polyline.EncodeResult.Success -> fail("Expected encode error")
                is Polyline.EncodeResult.Error -> assertEquals(result.error, Polyline.EncodeError.InconsistentCoordinateDimensions)
            }
        }
    }

    @Test
    fun testFlexiblePolylineEncodeThrowsErrorWithNegative2DPrecision() {
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.FlexiblePolyline)

        val coords = arrayOf(doubleArrayOf(0.0, 0.0, 5.0), doubleArrayOf(10.0, 0.0, 0.0))
        when (val result = Polyline.encodeFromLngLatArray(
            coords,
            Polyline.CompressionParameters(precisionLngLat = -5)
        )) {
            is Polyline.EncodeResult.Success -> fail("Expected encode error")
            is Polyline.EncodeResult.Error -> assertEquals(result.error, Polyline.EncodeError.InvalidPrecisionValue)
        }
    }

    @Test
    fun testFlexiblePolylineEncodeThrowsErrorWithNegative3DPrecision() {
        Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.FlexiblePolyline)

        val coords = arrayOf(doubleArrayOf(0.0, 0.0, 5.0), doubleArrayOf(10.0, 0.0, 0.0))
        when (val result = Polyline.encodeFromLngLatArray(
            coords,
            Polyline.CompressionParameters(precisionThirdDimension = -5)
        )) {
            is Polyline.EncodeResult.Success -> fail("Expected encode error")
            is Polyline.EncodeResult.Error -> assertEquals(result.error, Polyline.EncodeError.InvalidPrecisionValue)
        }
    }
}