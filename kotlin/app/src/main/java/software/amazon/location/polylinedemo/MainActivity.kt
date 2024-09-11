package software.amazon.location.polylinedemo

import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource

import software.amazon.location.polyline.R
import software.amazon.location.polyline.Polyline

class MainActivity : AppCompatActivity() {

    // Declare a variable for MapView
    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init MapLibre
        MapLibre.getInstance(this)

        // Init layout view
        val inflater = LayoutInflater.from(this)
        val rootView = inflater.inflate(R.layout.activity_main, null)
        setContentView(rootView)

        // Init the MapView
        mapView = rootView.findViewById(R.id.mapView)
        mapView.getMapAsync { map ->
            map.setStyle("https://demotiles.maplibre.org/style.json") { style ->

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

                // Encode and decode the route polyline to demonstrate how the APIs are used.
                val routePolyline = when (val result = Polyline.encodeFromLngLatArray(lngLatArray)) {
                    is Polyline.EncodeResult.Success -> result.encodedData
                    is Polyline.EncodeResult.Error -> ""
                }
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
            }

            map.cameraPosition = CameraPosition.Builder().target(LatLng(0.0,0.0)).zoom(1.0).build()
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}