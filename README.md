# @aws-geospatial/polyline

This library is used to simplify the process of using compressed geometry with [maplibre-gl-js](https://github.com/maplibre/maplibre-gl-js) in JavaScript Applications.

Location-based service providers sometimes use variations of the [Encoded Polyline Algorithm Format](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
to reduce the response size for APIs that return potentially large arrays of coordinates, such as routing and isoline APIs.
The utility methods in this library compresses the data and decompresses into GeoJSON that can be directly rendered in MapLibre.

## Installation

Install this library from NPM for usage with modules:

```console
npm install @aws-geospatial/polyline
```

You can also import the Javascript file for usage directly in the browser.

```html
<script src="https://www.unpkg.com/@aws-geospatial/polyline/dist/polyline.js"></script>
```

## Usage

Import the library and call the utility functions in the top-level namespace as needed.
You can find more details about these functions in the [Documentation](#documentation) section.

### Usage with Modules

```javascript
import { decodeToLineStringFeature } from "@aws-geospatial/polyline";

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
<!-- Import the Maplibre Polyline library -->
<script src="https://www.unpkg.com/@aws-geospatial/polyline"></script>
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

### encodeFromLngLatArray

This encodes an array of coordinates in longitude, latitude order into compressed polyline data.
While this isn't needed for MapLibre rendering, you might need it to compress data for a request
to a Location Service Provider when requesting route-related data.

### decodeToLineStringFeature

This is the most common method to use. It decodes compressed polyline data into a GeoJSON
Feature containing a LineString that can directly be used as a MapLibre source for rendering.

### decodeToPolygonFeature

Similar to `decodeToLineStringFeature` it decodes an array of compressed polyline rings into a GeoJSON
Feature containing a Polygon that can directly be used as a MapLibre source for rendering.
This should be used when the compressed data is meant to represent polygon rings, as it will
also generate the correct winding order of the rings for use with GeoJSON.

### decodeToLineString

This decodes a compressed polyline into a GeoJSON LineString. This can't directly be used
as a MapLibre source for rendering, but it is useful when trying to work directly with LineString
data.

### decodeToPolygon

This decodes an array of compressed polyline rings into a GeoJSON Polygon. This can't directly be used
as a MapLibre source for rendering, but it is useful when trying to work directly with Polygon
data.

### decodeToLngLatArray

This decodes compressed polyline data into an array of coordinates in longitude, latitude order.
This method is helpful when you need to directly work with the coordinate data.

## License

This library is licensed under the MIT-0 License. See the [LICENSE](LICENSE) file.
