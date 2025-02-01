package com.example.mapapp.ViewModels

import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapapp.Database.GeoPackageRepository
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.literal
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerAbove
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MapViewModel(context: Context) : ViewModel() {
    private val _mapState = MutableStateFlow(MapState())
    val mapState: StateFlow<MapState> = _mapState

    private val markerFeatures = mutableListOf<Feature>()
    private var selectedMarker: Feature? = null
    private var tableName: String? = null

    private val repo = GeoPackageRepository(context)

    private val _latLngList = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val latLngList: StateFlow<List<Pair<Double, Double>>> = _latLngList

    private val _layers = MutableStateFlow<List<String>>(emptyList())
    val layers: StateFlow<List<String>> = _layers

    private val _selectedLayer = MutableStateFlow<String>("")
    val selectedLayer: StateFlow<String> = _selectedLayer

    private val _selectedLayers = MutableStateFlow<MutableSet<String>>(mutableSetOf())
    val selectedLayers: StateFlow<Set<String>> = _selectedLayers

    private val _activeLayer = MutableStateFlow<String?>(null)
    val activeLayer: StateFlow<String?> = _activeLayer

    fun getSelectedLayers(): Set<String> = _selectedLayers.value

    fun updateVisibleLayers(layers: Set<String>) {
        _selectedLayers.value = layers.toMutableSet()
        loadMarkersForVisibleLayers()
        loadAllMarkers()
    }

    fun loadMarkersForVisibleLayers() {
        viewModelScope.launch {
            val markers = mutableListOf<Feature>()
            for (layer in _selectedLayers.value) {
                repo.loadAllLatLng(layer).collect { result ->
                    if (result is Results.Success) {
                        result.data?.forEach { (lat, lng) ->
                            val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
                                addStringProperty("icon", "ic_point2")
                            }
                            markers.add(feature)
                        }
                    }
                }
            }
            geoJsonSource.featureCollection(FeatureCollection.fromFeatures(markers))
        }
    }

    private val geoJsonSource: GeoJsonSource by lazy {
        GeoJsonSource.Builder("marker-source")
            .featureCollection(FeatureCollection.fromFeatures(markerFeatures))
            .build()
    }

    init {
       loadAllMarkers()
    }


    fun updateTableName(layerName: String) {
        tableName = layerName
        Log.d("MapViewModel", "Initializing database with table: $layerName")
        repo.initializeDatabase(layerName)
        loadAllMarkers()
    }


    fun updateStyle(styleUri: String) {
        _mapState.update { it.copy(currentStyle = styleUri) }
    }

    fun setupGeoJsonSource(style: Style) {
        style.addSource(geoJsonSource)
    }

    fun setupSymbolLayer(style: Style) {
//        style.removeStyleLayer("text-layer")
//        style.removeStyleLayer("marker-layer")

        val symbolLayer = SymbolLayer("marker-layer", "marker-source").apply {
            iconImage("{icon}")
            iconAllowOverlap(true)
            iconIgnorePlacement(true)
        }

        val textLayer = SymbolLayer("text-layer", "marker-source").apply {
            textField(Expression.get("point_id"))
            textSize(literal(12.0))
            textColor(Color.BLACK)
            textHaloColor(Color.WHITE)
            textHaloWidth(literal(1.5))
            textAllowOverlap(true)
            textIgnorePlacement(true)
            textOffset(literal(listOf(0.0, -2.0)))
        }

        style.addLayer(symbolLayer)
        style.addLayerAbove(textLayer, "marker-layer")
    }

    fun addMarker(lat: Double, lng: Double) {
        val activeTable = _activeLayer.value ?: tableName
        if (activeTable.isNullOrEmpty()) {
            Log.e("GeoPackage", "❌ No active layer selected for adding points!")
            return
        }

        val pointId = System.currentTimeMillis()
        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addStringProperty("icon", "ic_point2")
            addStringProperty("point_id", "${tableName ?: "default"} - Point $pointId")        }

        markerFeatures.add(feature)
        geoJsonSource.featureCollection(FeatureCollection.fromFeatures(markerFeatures))

        Log.d("GeoPackage", "✅ Adding Marker to Map: ($lat, $lng) in $activeTable")

        viewModelScope.launch {
            tableName?.let {
                repo.saveLatLng(lat, lng, it).collect { result ->
                    when (result) {
                        is Results.Success -> {
                            loadAllMarkers()
                            Log.d("GeoPackage", "✔ Point Saved in DB: ($lat, $lng)")
                        }
                        is Results.Error -> Log.e("GeoPackage", "❌ Error saving point: ${result.message}")
                        is Results.Loading -> {}
                    }
                }
            }
        }
    }

    fun loadAllMarkers() {
        viewModelScope.launch {
            val selected = _selectedLayers.value
            val selectedMarkers = mutableListOf<Feature>()

            for (layer in selected) {
                repo.loadAllLatLng(layer).collect { result ->
                    if (result is Results.Success) {
                        result.data?.forEachIndexed { index, (lat, lng) ->
                            val pointId = repo.getPointId(lat, lng, layer)
                            val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
                                addStringProperty("icon", "ic_point2")
                                addStringProperty("point_id", "$layer - Point $pointId")                             }
                            selectedMarkers.add(feature)
                        }
                    }
                }
            }

            markerFeatures.clear()
            markerFeatures.addAll(selectedMarkers)
            geoJsonSource.featureCollection(FeatureCollection.fromFeatures(markerFeatures))
            Log.d("GeoPackage", "✅ Updated markers for selected layers: $selected")
        }
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

        viewModelScope.launch {
            tableName?.let {
                repo.deleteLatLng(lat, lng, it).collect { result ->
                    when (result) {
                        is Results.Loading -> Log.d("GeoPackage", "Deleting LatLng...")
                        is Results.Success -> {
                            Log.d("GeoPackage", "LatLng deleted successfully!")
                                loadAllMarkers()
                        }

                        is Results.Error -> Log.e("GeoPackage", "Error deleting LatLng: ${result.message}")
                    }
                }
            }
        }
    }

    fun isNearLocation(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Boolean {
        val threshold = 0.0001
        return (Math.abs(lat1 - lat2) < threshold && Math.abs(lng1 - lng2) < threshold)
    }

    fun createNewPointLayer(layerName: String) {
        viewModelScope.launch {
            repo.createPointLayer(layerName).collect { result ->
                if (result is Results.Success) {
                    loadLayers()
                    _selectedLayer.value = layerName
                    updateTableName(layerName)
                    loadAllMarkers()
                }
            }
        }
    }

    fun loadLayers() {
        viewModelScope.launch {
            repo.loadLayers().collect { result ->
                when (result) {
                    is Results.Loading -> Log.d("GeoPackage", "⌛ Loading layers...")
                    is Results.Success -> {
                        _layers.value = result.data ?: emptyList()
                        Log.d("GeoPackage", "Loaded layers: ${_layers.value}")
                    }
                    is Results.Error -> Log.e("GeoPackage", "Error loading layers: ${result.message}")
                }
            }
        }
    }

    fun closeDatabase() {
        repo.closeDatabase()
    }
}
