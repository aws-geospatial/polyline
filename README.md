# @aws/polyline

This library simplifies the process of using compressed geometry with [maplibre-gl-js](https://github.com/maplibre/maplibre-gl-js) in JavaScript applications 
and with [maplibre-native](https://github.com/maplibre/maplibre-native) in Kotlin and Swift applications.

Location-based service providers sometimes use variations of the [Encoded Polyline Algorithm Format](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
to reduce the response size for APIs that can return large arrays of coordinates, such as routing and isoline APIs.
The utility methods in this library compresses the data and decompresses into GeoJSON that can be directly rendered in MapLibre.

## Usage

Documentation for each supported language can be found here:
* [JavaScript](./javascript/README.md)
* [Kotlin](./kotlin/README.md)
* [Swift](./swift/README.md)

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
