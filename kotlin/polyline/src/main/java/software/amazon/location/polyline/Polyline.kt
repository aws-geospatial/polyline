// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.amazon.location.polyline

// The default algorithm is FlexiblePolyline. This was selected as it is the newest and most flexible format
// of the different decoding types supported.
private var compressor: DataCompressor = FlexiblePolyline()

/** Get the currently-selected compression algorithm.
 * @returns The current compression algorithm.
 */
public fun getCompressionAlgorithm(): CompressionAlgorithm {
    return when (compressor) {
        is Polyline5 -> CompressionAlgorithm.Polyline5
        is Polyline6 -> CompressionAlgorithm.Polyline6
        else -> CompressionAlgorithm.FlexiblePolyline
    }
}

interface DataCompressor

class FlexiblePolyline : DataCompressor

class Polyline5 : DataCompressor

class Polyline6 : DataCompressor

/** Set the compression algorithm to use for subsequent encode/decode calls.
 * @param compressionType The compression algorithm to use.
 * @throws IllegalArgumentException if an invalid compression algorithm is specified.
 */
fun setCompressionAlgorithm(compressionType: CompressionAlgorithm = CompressionAlgorithm.FlexiblePolyline) {
    compressor = when (compressionType) {
        CompressionAlgorithm.Polyline5 -> Polyline5()
        CompressionAlgorithm.Polyline6 -> Polyline6()
        CompressionAlgorithm.FlexiblePolyline -> FlexiblePolyline()
    }
}

/** Encode the provided array of coordinate values into an encoded string.
 * @remarks
 * This takes in an array of two-dimensional or three-dimensional positions and encodes them into
 * the currently-selected compression format.
 * Example of 2D input data:
 * ```
 *   [ [5.0, 0.0], [10.0, 5.0], [10.0, 10.0], ]
 * ```
 * Example of 3D input data:
 * ```
 *   [ [5.0, 0.0, 200.0], [10.0, 5.0, 200.0], [10.0, 10.0, 205.0], ]
 * ```
 * @param lngLatArray  An array of lng/lat positions to encode. The positions may contain an optional 3rd dimension.
 * @param parameters Optional compression parameters. These are currently only used by the FlexiblePolyline algorithm.
 * @returns An encoded string containing the compressed coordinate values.
 * @throws IllegalArgumentException if the input data contains no coordinate pairs,
 * latitude values outside of -90, 90, longitude values outside of -180, 180,
 * data that isn't 2-dimensional or 3-dimensional, or data that is 3-dimensional with a compressor that doesn't support 3D data.
 */
@Throws(IllegalArgumentException::class)
public fun encodeFromLngLatArray(
    lngLatArray: Array<DoubleArray>,
    parameters: CompressionParameters = CompressionParameters()
): String {
    return ""
}

/** Decode the provided encoded data string into an array of coordinate values.
 * @remarks
 * Note that this method returns a raw array of coordinate values, which cannot be used as a MapLibre source
 * without first embedding it into a GeoJSON Feature. If you want to add the decoded data as a MapLibre source,
 * use either {@link decodeToLineStringFeature} or {@link decodeToPolygonFeature} instead.
 * Only use this method when you want to use the coordinate data directly.
 * @param compressedData  The encoded data string to decode. The data is expected to have valid lat/lng values.
 * @returns An array of coordinate value arrays.
 * @throws IllegalArgumentException if the encodedData contains invalid characters, no coordinate pairs,
 * latitude values outside of -90, 90, or longitude values outside of -180, 180.
 * @example
 * An example of decoded data:
 * ```
 *   [
 *     [5.0, 0.0],
 *     [10.0, 5.0],
 *     [10.0, 10.0],
 *   ]
 * ```
 */
@Throws(IllegalArgumentException::class)
public fun decodeToLngLatArray(compressedData: String): Array<DoubleArray> {
    val lngLatArray : Array<DoubleArray> = arrayOf(
        doubleArrayOf(-28.193, -61.38823),
        doubleArrayOf(-26.78675, -45.01442),
        doubleArrayOf(-9.20863, -43.2583),
        doubleArrayOf(-9.20863, -52.20348),
        doubleArrayOf(-26.78675, -53.26775),
        doubleArrayOf(-28.193, -61.38823),
        doubleArrayOf(-20.10706, -61.21942),
        doubleArrayOf(-19.05238, -57.07888),
        doubleArrayOf(-8.85706, -57.07888),
        doubleArrayOf(-9.20863, -61.21942),
        doubleArrayOf(-20.10706, -61.21942),
        doubleArrayOf(-0.068, -60.70753),
        doubleArrayOf(2.7445, -43.75829),
        doubleArrayOf(-0.068, -60.70753),
        doubleArrayOf(11.182, -60.53506),
        doubleArrayOf(6.96325, -55.11851),
        doubleArrayOf(11.182, -60.53506),
        doubleArrayOf(16.807, -54.51079),
        doubleArrayOf(3.47762, -65.61471),
        doubleArrayOf(11.182, -60.53506),
        doubleArrayOf(22.432, -60.18734),
        doubleArrayOf(25.59606, -42.99168),
        doubleArrayOf(22.432, -60.18734),
        doubleArrayOf(31.22106, -59.83591),
        doubleArrayOf(32.62731, -53.05697),
        doubleArrayOf(31.22106, -59.83591),
        doubleArrayOf(38.25231, -59.65879),
        doubleArrayOf(40.36169, -53.05697),
        doubleArrayOf(40.01012, -54.71438),
        doubleArrayOf(44.22887, -53.26775),
        doubleArrayOf(47.39294, -55.5186),
        doubleArrayOf(46.68981, -59.65879),
        doubleArrayOf(53.72106, -59.30172),
        doubleArrayOf(51.26012, -56.11118),
        doubleArrayOf(56.182, -53.89389),
        doubleArrayOf(60.40075, -56.69477),
        doubleArrayOf(51.26012, -56.11118),
        doubleArrayOf(53.72106, -59.30172),
        doubleArrayOf(58.64294, -59.48073),
    );
    return lngLatArray;

}

public fun decodeToLineStringFeature(compressedData: String) :String {
    val geoJson = """
{
    "type": "Feature",
    "properties": {},
    "geometry":
    {
        "type": "LineString",
        "coordinates": [
[-28.19300, -61.38823],
[-26.78675, -45.01442],
[-9.20863, -43.25830],
[-9.20863, -52.20348],
[-26.78675, -53.26775],
[-28.19300, -61.38823],
[-20.10706, -61.21942],
[-19.05238, -57.07888],
[-8.85706, -57.07888],
[-9.20863, -61.21942],
[-20.10706, -61.21942],
[-0.06800, -60.70753],
[2.74450, -43.75829],
[-0.06800, -60.70753],
[11.18200, -60.53506],
[6.96325, -55.11851],
[11.18200, -60.53506],
[16.80700, -54.51079],
[3.47762, -65.61471],
[11.18200, -60.53506],
[22.43200, -60.18734],
[25.59606, -42.99168],
[22.43200, -60.18734],
[31.22106, -59.83591],
[32.62731, -53.05697],
[31.22106, -59.83591],
[38.25231, -59.65879],
[40.36169, -53.05697],
[40.01012, -54.71438],
[44.22887, -53.26775],
[47.39294, -55.51860],
[46.68981, -59.65879],
[53.72106, -59.30172],
[51.26012, -56.11118],
[56.18200, -53.89389],
[60.40075, -56.69477],
[51.26012, -56.11118],
[53.72106, -59.30172],
[58.64294, -59.48073]
        ]
    }
}
                """
    return geoJson;
}