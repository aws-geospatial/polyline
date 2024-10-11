# Polyline for Kotlin

This library simplifies the process of using compressed geometry with [maplibre-native](https://github.com/maplibre/maplibre-native) in Swift applications.

Location-based service providers sometimes use variations of the [Encoded Polyline Algorithm Format](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
to reduce the response size for APIs that can return large arrays of coordinates, such as routing and isoline APIs.
The utility methods in this library compresses the data and decompresses into GeoJSON that can be directly rendered in MapLibre.

## Installation

1. Go to File -> Add Package Dependencies in your XCode project.
2. Type the package URL (https://github.com/aws-geospatial/polyline/) into the search bar and press the enter key.
3. Select the "polyline" package and click on "Add Package".
4. Select the "polyline" package product and click on "Add Package".

## Usage

Import the Polyline package in your code:
```swift
import Polyline
```

To decode a routePolyline and render it in MapLibre, you can use the following:

```swift
do {
    let decodedGeoJSON = try Polyline.decodeToLineStringFeature(routePolyline)
        
    let shapeFromGeoJSON = try MLNShape(data: decodedGeoJSON.data(using: .utf8)!, encoding: String.Encoding.utf8.rawValue)
 
    let source = MLNShapeSource(identifier: "polylineSource", shape: shapeFromGeoJSON, options: nil)
    mapView.style?.addSource(source)
            
    let lineLayer = MLNLineStyleLayer(identifier: "polyline", source: source)
    lineLayer.lineColor = NSExpression(forConstantValue: UIColor.red)
    lineLayer.lineWidth = NSExpression(forConstantValue: 2)
    lineLayer.lineJoin = NSExpression(forConstantValue: "round")
    lineLayer.lineCap = NSExpression(forConstantValue: "round")
    
    mapView.style?.addLayer(lineLayer)
} catch {
    print("Error: \(error)")
}
```

## Documentation

### getCompressionAlgorithm

Returns the currently-selected compression algorithm. This can be either
`FlexiblePolyline`, `Polyline5`, or `Polyline6`.

```swift
val compressionType = Polyline.getCompressionAlgorithm()
```

### setCompressionAlgorithm

Sets the compression algorithm to use for subsequent encode/decode calls. This can be either
`FlexiblePolyline`, `Polyline5`, or `Polyline6`.

```swift
Polyline.setCompressionAlgorithm(Polyline.CompressionAlgorithm.FlexiblePolyline)
```

### encodeFromLngLatArray

This encodes an array of coordinates in longitude, latitude order into compressed polyline data.
The data can include an optional 3rd dimension when encoding with the `FlexiblePolyline` algorithm.
While this isn't needed for MapLibre rendering, you might need it to compress data for a request
to a Location Service Provider when requesting route-related data.

```swift
guard let polylineString = try? Polyline.encodeFromLngLatArray(lngLatArray: [[0.0, 0.0], [5.0, 5.0]]) else {
    fatalError("error")
}
```

### decodeToLineStringFeature

This is the most common method to use. It decodes compressed polyline data into a GeoJSON
Feature containing a LineString that can directly be used as a MapLibre source for rendering.

```swift
guard let geoJsonLineStringFeature = try? Polyline.decodeToLineStringFeature(polylineString) else {
    fatalError("error")
}
```

### decodeToPolygonFeature

Similar to `decodeToLineStringFeature` it decodes an array of compressed polyline rings into a GeoJSON
Feature containing a Polygon that can directly be used as a MapLibre source for rendering.
This should be used when the compressed data is meant to represent polygon rings, as it will
also generate the correct winding order of the rings for use with GeoJSON.

```swift
guard let geoJsonPolygonFeature = try? Polyline.decodeToPolygonFeature([polylineString]) else {
    fatalError("error")
}
```

### decodeToLineString

This decodes a compressed polyline into a GeoJSON LineString. The LineString can be embedded into the `geometry`
section of a GeoJSON Feature which can then be rendered with MapLibre.

```swift
guard let geoJsonLineString = try? Polyline.decodeToLineString(polylineString) else {
    fatalError("error")
}
```

### decodeToPolygon

This decodes an array of compressed polyline rings into a GeoJSON Polygon. The Polygon can be embedded into the
`geometry` section of a GeoJSON Feature which can then be rendered with MapLibre.

```swift
guard let geoJsonPolygon = try? Polyline.decodeToPolygon([polylineString]) else {
    fatalError("error")
}
```

### decodeToLngLatArray

This decodes compressed polyline data into an array of coordinates in longitude, latitude order.
This method is helpful when you need to directly work with the coordinate data.

```swift
guard let lngLatArray = try? Polyline.decodeToLngLatArray(polylineString) else {
    fatalError("error")
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
