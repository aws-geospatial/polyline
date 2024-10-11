# Polyline for Kotlin

This library simplifies the process of using compressed geometry with [maplibre-native](https://github.com/maplibre/maplibre-native) in Kotlin applications.

Location-based service providers sometimes use variations of the [Encoded Polyline Algorithm Format](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
to reduce the response size for APIs that can return large arrays of coordinates, such as routing and isoline APIs.
The utility methods in this library compresses the data and decompresses into GeoJSON that can be directly rendered in MapLibre.

## Installation

Add the following line to the dependencies section of your build.gradle or build.gradle.kts file in Android Studio:

```console
implementation("software.amazon.location:polyline:0.1.0")
```

## Usage

Import the Polyline class in your code:
```kotlin
import software.amazon.location.polyline.Polyline
```

To decode a routePolyline and render it in MapLibre, you can use the following:

```kotlin
val decodedGeoJSON = when (val result = Polyline.decodeToLineStringFeature(routePolyline)) {
    is Polyline.DecodeToGeoJsonResult.Success -> result.geojson
    is Polyline.DecodeToGeoJsonResult.Error -> ""
}

style.addSource(
    GeoJsonSource(
        "polylineSource",
        decodedGeoJSON,
        GeoJsonOptions().withLineMetrics(true)
    )
)

style.addLayer(
    LineLayer("polyline", "polylineSource")
        .withProperties(
            PropertyFactory.lineColor(Color.RED),
            PropertyFactory.lineWidth(2.0f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
)
```

## Documentation

### getCompressionAlgorithm

Returns the currently-selected compression algorithm. This can be either
`FlexiblePolyline`, `Polyline5`, or `Polyline6`.

```kotlin
val compressionType = Polyline.getCompressionAlgorithm()
```

### setCompressionAlgorithm

Sets the compression algorithm to use for subsequent encode/decode calls. This can be either
`FlexiblePolyline`, `Polyline5`, or `Polyline6`.

```kotlin
Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.FlexiblePolyline)
```

### encodeFromLngLatArray

This encodes an array of coordinates in longitude, latitude order into compressed polyline data.
The data can include an optional 3rd dimension when encoding with the `FlexiblePolyline` algorithm.
While this isn't needed for MapLibre rendering, you might need it to compress data for a request
to a Location Service Provider when requesting route-related data.

```kotlin
val polylineString = when (val result = Polyline.encodeFromLngLatArray(arrayOf(doubleArrayOf(5.0, 0.0), doubleArrayOf(10.0, 0.0)))) {
    is Polyline.EncodeResult.Success -> result.encodedData
    is Polyline.EncodeResult.Error -> ""
}
```

### decodeToLineStringFeature

This is the most common method to use. It decodes compressed polyline data into a GeoJSON
Feature containing a LineString that can directly be used as a MapLibre source for rendering.

```kotlin
val geoJsonLineStringFeature = when (val result = Polyline.decodeToLineStringFeature(polylineString)) {
    is Polyline.DecodeToGeoJsonResult.Success -> result.geojson
    is Polyline.DecodeToGeoJsonResult.Error -> ""
}
```

### decodeToPolygonFeature

Similar to `decodeToLineStringFeature` it decodes an array of compressed polyline rings into a GeoJSON
Feature containing a Polygon that can directly be used as a MapLibre source for rendering.
This should be used when the compressed data is meant to represent polygon rings, as it will
also generate the correct winding order of the rings for use with GeoJSON.

```kotlin
val geoJsonPolygonFeature = when (val result = Polyline.decodeToPolygonFeature(polylineString)) {
    is Polyline.DecodeToGeoJsonResult.Success -> result.geojson
    is Polyline.DecodeToGeoJsonResult.Error -> ""
}
```

### decodeToLineString

This decodes a compressed polyline into a GeoJSON LineString. The LineString can be embedded into the `geometry`
section of a GeoJSON Feature which can then be rendered with MapLibre.

```kotlin
val geoJsonLineString = when (val result = Polyline.decodeToLineString(polylineString)) {
    is Polyline.DecodeToGeoJsonResult.Success -> result.geojson
    is Polyline.DecodeToGeoJsonResult.Error -> ""
}
```

### decodeToPolygon

This decodes an array of compressed polyline rings into a GeoJSON Polygon. The Polygon can be embedded into the
`geometry` section of a GeoJSON Feature which can then be rendered with MapLibre.

```kotlin
val geoJsonPolygon = when (val result = Polyline.decodeToPolygon(polylineString)) {
    is Polyline.DecodeToGeoJsonResult.Success -> result.geojson
    is Polyline.DecodeToGeoJsonResult.Error -> ""
}
```

### decodeToLngLatArray

This decodes compressed polyline data into an array of coordinates in longitude, latitude order.
This method is helpful when you need to directly work with the coordinate data.

```kotlin
val lngLatArray = when (val result = Polyline.decodeToLngLatArray(polylineString)) {
    is Polyline.DecodeToArrayResult.Success -> result.lngLatArray
    is Polyline.DecodeToArrayResult.Error -> null
}
```

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## Getting Help

The best way to interact with our team is through GitHub.
You can [open an issue](https://github.com/aws-geospatial/polyline/issues/new/choose) and choose from one of our templates for
[bug reports](https://github.com/aws-geospatial/polyline/issues/new?assignees=&labels=bug%2C+needs-triage&template=---bug-report.md&title=) or
[feature requests](https://github.com/aws-geospatial/polyline/issues/new?assignees=&labels=feature-request&template=---feature-request.md&title=).
If you have a support plan with [AWS Support](https://aws.amazon.com/premiumsupport/), you can also create a new support case.

## Contributing

We welcome community contributions and pull requests. See [CONTRIBUTING](CONTRIBUTING.md) for information on how to set up a development environment and submit code.

## License

This library is licensed under the MIT-0 License. See the [LICENSE](LICENSE) file.
