# @aws/polyline

This library simplifies the process of using compressed geometry with [maplibre-gl-js](https://github.com/maplibre/maplibre-gl-js) in JavaScript applications.

Location-based service providers sometimes use variations of the [Encoded Polyline Algorithm Format](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
to reduce the response size for APIs that can return large arrays of coordinates, such as routing and isoline APIs.
The utility methods in this library compresses the data and decompresses into GeoJSON that can be directly rendered in MapLibre.

## Installation

Install this library from NPM for usage with modules:

```console
npm install @aws/polyline
```

You can also import the Javascript file for usage directly in the browser.

```html
<script src="https://cdn.jsdelivr.net/npm/@aws/polyline/dist/polyline.min.js"></script>
```

## Usage

Import the library and call the utility functions in the top-level namespace as needed.
You can find more details about these functions in the [Documentation](#documentation) section.

### Usage with Modules

```javascript
import { decodeToLineStringFeature } from "@aws/polyline";

var decodedGeoJSON = decodeToLineStringFeature(response.EncodedPolyline);
map.addLayer({
  id: "route",
  type: "line",
  source: {
    type: "geojson",
    data: decodedGeoJSON,
  },
  layout: {
    "line-join": "round",
    "line-cap": "round",
  },
  paint: {
    "line-color": "#3887be",
    "line-width": 5,
    "line-opacity": 0.75,
  },
});
```

### Usage with a browser

```html
<!-- Import the Polyline library -->
<script src="https://cdn.jsdelivr.net/npm/@aws/polyline/dist/polyline.min.js"></script>
```

```javascript
var decodedGeoJSON = polyline.decodeToLineStringFeature(
  response.EncodedPolyline,
);
map.addLayer({
  id: "route",
  type: "line",
  source: {
    type: "geojson",
    data: decodedGeoJSON,
  },
  layout: {
    "line-join": "round",
    "line-cap": "round",
  },
  paint: {
    "line-color": "#3887be",
    "line-width": 5,
    "line-opacity": 0.75,
  },
});
```

## Documentation

Detailed documentation can be found under `/docs/index.html` after generating it by running:

```console
npm run typedoc
```

### getCompressionAlgorithm

Returns the currently-selected compression algorithm. This can be either
`FlexiblePolyline`, `Polyline5`, or `Polyline6`.

```js
const compressionType = polyline.getCompressionAlgorithm();
```

### setCompressionAlgorithm

Sets the compression algorithm to use for subsequent encode/decode calls. This can be either
`FlexiblePolyline`, `Polyline5`, or `Polyline6`.

```js
polyline.setCompressionAlgorithm(polyline.FlexiblePolyline);
```

### encodeFromLngLatArray

This encodes an array of coordinates in longitude, latitude order into compressed polyline data.
The data can include an optional 3rd dimension when encoding with the `FlexiblePolyline` algorithm.
While this isn't needed for MapLibre rendering, you might need it to compress data for a request
to a Location Service Provider when requesting route-related data.

```js
const polylineString = polyline.encodeFromLngLatArray([
  [5.0, 0.0],
  [10.0, 0.0],
]);
```

### decodeToLineStringFeature

This is the most common method to use. It decodes compressed polyline data into a GeoJSON
Feature containing a LineString that can directly be used as a MapLibre source for rendering.

```js
const geoJsonLineStringFeature =
  polyline.decodeToLineStringFeature(polylineString);
```

### decodeToPolygonFeature

Similar to `decodeToLineStringFeature` it decodes an array of compressed polyline rings into a GeoJSON
Feature containing a Polygon that can directly be used as a MapLibre source for rendering.
This should be used when the compressed data is meant to represent polygon rings, as it will
also generate the correct winding order of the rings for use with GeoJSON.

```js
const geoJsonPolygonFeature = polyline.decodeToPolygonFeature(polylineString);
```

### decodeToLineString

This decodes a compressed polyline into a GeoJSON LineString. The LineString can be embedded into the `geometry`
section of a GeoJSON Feature which can then be rendered with MapLibre.

```js
const geoJsonLineString = polyline.decodeToLineString(polylineString);
```

### decodeToPolygon

This decodes an array of compressed polyline rings into a GeoJSON Polygon. The Polygon can be embedded into the
`geometry` section of a GeoJSON Feature which can then be rendered with MapLibre.

```js
const geoJsonPolygon = polyline.decodeToPolygon(polylineString);
```

### decodeToLngLatArray

This decodes compressed polyline data into an array of coordinates in longitude, latitude order.
This method is helpful when you need to directly work with the coordinate data.

```js
const lngLatArray = polyline.decodeToLngLatArray(polylineString);
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
