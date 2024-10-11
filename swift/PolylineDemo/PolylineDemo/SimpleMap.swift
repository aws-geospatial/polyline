//
//  ContentView.swift
//  PolylineDemo
//
//  Created by Balfour, Mike on 10/3/24.
//

import SwiftUI
import MapLibre
import Polyline

class Coordinator: NSObject, MLNMapViewDelegate {
    var parent: SimpleMap

    init(_ parent: SimpleMap) {
        self.parent = parent
    }

    func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
        addPolyline(to: mapView)
    }

    func addPolyline(to mapView: MLNMapView) {
        let lngLatArray: [[Double]] = [
            [-28.193, -61.38823],
            [-26.78675, -45.01442],
            [-9.20863, -43.2583],
            [-9.20863, -52.20348],
            [-26.78675, -53.26775],
            [-28.193, -61.38823],
            [-20.10706, -61.21942],
            [-19.05238, -57.07888],
            [-8.85706, -57.07888],
            [-9.20863, -61.21942],
            [-20.10706, -61.21942],
            [-0.068, -60.70753],
            [2.7445, -43.75829],
            [-0.068, -60.70753],
            [11.182, -60.53506],
            [6.96325, -55.11851],
            [11.182, -60.53506],
            [16.807, -54.51079],
            [3.47762, -65.61471],
            [11.182, -60.53506],
            [22.432, -60.18734],
            [25.59606, -42.99168],
            [22.432, -60.18734],
            [31.22106, -59.83591],
            [32.62731, -53.05697],
            [31.22106, -59.83591],
            [38.25231, -59.65879],
            [40.36169, -53.05697],
            [40.01012, -54.71438],
            [44.22887, -53.26775],
            [47.39294, -55.5186],
            [46.68981, -59.65879],
            [53.72106, -59.30172],
            [51.26012, -56.11118],
            [56.182, -53.89389],
            [60.40075, -56.69477],
            [51.26012, -56.11118],
            [53.72106, -59.30172],
            [58.64294, -59.48073]
        ]

        do {
            // Encode and decode the route polyline to demonstrate how the APIs are used.
            let routePolyline = try Polyline.encodeFromLngLatArray(lngLatArray: lngLatArray)
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
        }
        catch {
            print("Error: \(error)")
        }
    
    }
}

struct SimpleMap: UIViewRepresentable {

     func makeCoordinator() -> Coordinator {
         Coordinator(self)
     }

     func updateUIView(_ uiView: MLNMapView, context: Context) {}

     func makeUIView(context: Context) -> MLNMapView {
         let mapView = MLNMapView()
         mapView.delegate = context.coordinator
         mapView.styleURL = URL(string: "https://demotiles.maplibre.org/style.json")
         return mapView
     }
}

#Preview {
    SimpleMap()
}
