package com.example.mapapp.ViewModels

import androidx.lifecycle.ViewModel
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.plugin.gestures.gestures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class MapViewModel : ViewModel() {
    private val _mapState = MutableStateFlow(MapState())
    val mapState: StateFlow<MapState> = _mapState
    private val markerFeatures = mutableListOf<Feature>()
    private var selectedMarker: Feature? = null
    private val geoJsonSource: GeoJsonSource by lazy {
        GeoJsonSource.Builder("marker-source")
            .featureCollection(FeatureCollection.fromFeatures(markerFeatures))
            .build()
    }

    fun updateStyle(styleUri: String) {
        _mapState.update { it.copy(currentStyle = styleUri) }
    }

//    val saveLatLngState: StateFlow<SaveLatLngState> = SaveLatLngStateManager.saveLatLngState

    fun setupGeoJsonSource(style: Style) {
        style.addSource(geoJsonSource)
    }

    fun setupSymbolLayer(style: Style) {
        val symbolLayer = SymbolLayer("marker-layer", "marker-source")
        symbolLayer.iconImage("{icon}")

        symbolLayer.iconAllowOverlap(true)
        symbolLayer.iconIgnorePlacement(true)

        symbolLayer.iconColor(Expression.get("color"))

        style.addLayer(symbolLayer)
    }

    fun addMarker(lat: Double, lng: Double) {
        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addStringProperty("icon", "ic_point2")
            addStringProperty("color", "#FF0000")
        }
        markerFeatures.add(feature)
        geoJsonSource.featureCollection(FeatureCollection.fromFeatures(markerFeatures))
    }

    fun deleteMarker(lat: Double, lng: Double) {
        val iterator = markerFeatures.iterator()
        while (iterator.hasNext()) {
            val feature = iterator.next()
            val point = feature.geometry() as? Point
            if (point != null && isNearLocation(point.latitude(), point.longitude(), lat, lng)) {
                iterator.remove()
                geoJsonSource.featureCollection(FeatureCollection.fromFeatures(markerFeatures))
                selectedMarker = null
                break
            }
        }
    }

    fun isNearLocation(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Boolean {
        val threshold = 0.0001
        return (Math.abs(lat1 - lat2) < threshold && Math.abs(lng1 - lng2) < threshold)
    }
}
