package com.example.mapapp.ViewModels

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapapp.Database.GeoPackageRepository
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.color
import com.mapbox.maps.extension.style.expressions.dsl.generated.literal
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerAbove
import com.mapbox.maps.extension.style.layers.generated.LineLayer
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
    private var tempLinePoint: Pair<Double, Double>? = null
    private lateinit var markerSource: GeoJsonSource

    private val repo = GeoPackageRepository(context)

    fun createNewPointLayer(layer:MapLayer) {
        viewModelScope.launch {
            repo.createLayer(layer).collect { result ->
                when(result){
                    is Results.Error -> {

                    }
                    is Results.Loading -> {}
                    is Results.Success -> {
                        if(result.data == true){
                            _mapState.value.layers[layer.id] = layer
                            _mapState.value = _mapState.value.copy(activeLayer = layer)
                        }
                    }
                }
            }
        }
    }
    init {
        repo.initializeDatabase()
        viewModelScope.launch {
            repo.loadLayers().collect { result ->
                when (result) {
                    is Results.Loading -> Log.d("GeoPackage", "⌛ Loading layers...")
                    is Results.Success -> {
                        val loadedLayers = result.data ?: emptyList()
                        _mapState.value = mapState.value.copy(layers = loadedLayers.associateBy { it.id }
                            .toMutableMap())
                        Log.d("GeoPackage", "Loaded layers: $loadedLayers")
                    }
                    is Results.Error -> Log.e("GeoPackage", "Error loading layers: ${result.message}")
                }
            }
        }
    }

    fun updateVisibleLayers(layers: MutableList<String>) {
        for (layer in mapState.value.layers){
            if(layers.contains(layer.key)){
                mapState.value.layers[layer.key]?.let { it.isVisible=true }
            }else{
                mapState.value.layers[layer.key]?.let { it.isVisible=false }
            }
        }
    }

    fun loadMarkersForVisibleLayers() {
        viewModelScope.launch {
            val markers = mutableListOf<Feature>()
            val visibleLayers = _mapState.value.layers.values.filter { it.isVisible }
            for (layer in visibleLayers) {
                repo.loadAllLatLng(layer.id).collect { result ->
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

//    private val lineSource: GeoJsonSource by lazy {
//        GeoJsonSource.Builder("line-source")
//            .featureCollection(FeatureCollection.fromFeatures(emptyArray()))
//            .build()
//    }

    fun updateStyle(styleUri: String) {
        _mapState.update { it.copy(currentStyle = styleUri) }
    }

    fun setupGeoJsonSource(style: Style) {
        style.addSource(geoJsonSource)
    }

    // POINT LAYER

    fun setupSymbolLayer(style: Style) {
        val symbolLayer = SymbolLayer("marker-layer", "marker-source").apply {
            iconImage("{icon}")
            iconColor(mapState.value.layers["layer1"]?.color ?: 0xFF000000.toInt())
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
        val activeLayer = _mapState.value.activeLayer
        if (activeLayer == null || activeLayer.id.isEmpty()) {
            Log.e("GeoPackage", "❌ No active layer selected for adding points!")
            return
        }
        val tableName = activeLayer.id

        val pointId = System.currentTimeMillis()
        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addStringProperty("icon", "ic_point2")
            addStringProperty("point_id", "$tableName - Point $pointId")
        }

        markerFeatures.add(feature)
        geoJsonSource.featureCollection(FeatureCollection.fromFeatures(markerFeatures))

        Log.d("GeoPackage", "✅ Adding Marker to Map: ($lat, $lng) in $tableName")

        viewModelScope.launch {
            repo.saveLatLng(lat, lng, tableName).collect { result ->
                when (result) {
                    is Results.Success -> {
                        loadAllMarkers()
                        Log.d("GeoPackage", "✔ Point Saved in DB: ($lat, $lng)")
                    }
                    is Results.Error -> Log.e("GeoPackage", "❌ Error saving point: ${result.message}")
                    is Results.Loading -> { }
                }
            }
        }
    }

    fun loadAllMarkers() {
        viewModelScope.launch {
            val selectedMarkers = mutableListOf<Feature>()
            val visibleLayers = _mapState.value.layers.values.filter { it.isVisible }
            for (layer in visibleLayers) {
                repo.loadAllLatLng(layer.id).collect { result ->
                    if (result is Results.Success) {
                        result.data?.forEachIndexed { index, (lat, lng) ->
                            val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
                                addStringProperty("icon", "ic_point2")
                                addStringProperty("point_id", "${layer.id} - Point ${index + 1}")
                            }
                            selectedMarkers.add(feature)
                        }
                    }
                }
            }
            markerFeatures.clear()
            markerFeatures.addAll(selectedMarkers)
            geoJsonSource.featureCollection(FeatureCollection.fromFeatures(markerFeatures))
            Log.d("GeoPackage", "✅ Updated markers for visible layers: ${visibleLayers.map { it.id }}")
        }
    }

//    fun deleteMarker(lat: Double, lng: Double) {
//        val iterator = markerFeatures.iterator()
//        while (iterator.hasNext()) {
//            val feature = iterator.next()
//            val point = feature.geometry() as? Point
//            if (point != null && isNearLocation(point.latitude(), point.longitude(), lat, lng)) {
//                iterator.remove()
//                geoJsonSource.featureCollection(FeatureCollection.fromFeatures(markerFeatures))
//                selectedMarker = null
//                break
//            }
//        }

//        viewModelScope.launch {
//            tableName?.let {
//                repo.deleteLatLng(lat, lng, it).collect { result ->
//                    when (result) {
//                        is Results.Loading -> Log.d("GeoPackage", "Deleting LatLng...")
//                        is Results.Success -> {
//                            Log.d("GeoPackage", "LatLng deleted successfully!")
//                                loadAllMarkers()
//                        }
//
//                        is Results.Error -> Log.e("GeoPackage", "Error deleting LatLng: ${result.message}")
//                    }
//                }
//            }
//        }
//    }

//    fun isNearLocation(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Boolean {
//        val threshold = 0.0001
//        return (Math.abs(lat1 - lat2) < threshold && Math.abs(lng1 - lng2) < threshold)
//    }

    fun deleteLayer(layerName: String) {
        viewModelScope.launch {
            repo.deleteLayer(layerName).collect { result ->
                when (result) {
                    is Results.Success -> {
                        Log.d("MapViewModel", "Layer '$layerName' deleted successfully")
                        //loadLayers()
                        _mapState.update { currentState ->
                            val updatedLayers = currentState.layers.toMutableMap().apply {
                                remove(layerName)
                            }
                            val updatedActiveLayer = if (currentState.activeLayer?.id == layerName) null else currentState.activeLayer
                            currentState.copy(
                                layers = updatedLayers,
                                activeLayer = updatedActiveLayer
                            )
                        }
                    }
                    is Results.Error -> Log.e("MapViewModel", "Error deleting layer: ${result.message}")
                    is Results.Loading -> {}
                }
            }
        }
    }

      //LINE LAYER

//    fun addPointForLine(lat: Double, lng: Double) {
//        if (tempLinePoint == null) {
//            tempLinePoint = lat to lng
//        } else {
//            val firstPoint = tempLinePoint!!
//            val newSegment = Pair(firstPoint, lat to lng)
//            _mapState.update { currentState ->
//                currentState.copy(lineSegments = currentState.lineSegments + newSegment)
//            }
//            tempLinePoint = null
//            updateLinesOnMap()
//        }
//    }

//    fun updateLinesOnMap() {
//        val lines = _mapState.value.lineSegments.map { segment ->
//            val (start, end) = segment
//            val startPoint = Point.fromLngLat(start.second, start.first)
//            val endPoint = Point.fromLngLat(end.second, end.first)
//            val lineString = LineString.fromLngLats(listOf(startPoint, endPoint))
//            Feature.fromGeometry(lineString)
//        }
//        lineSource.featureCollection(FeatureCollection.fromFeatures(lines))
//    }

//    fun createNewLineLayer(layerName: String) {
//        viewModelScope.launch {
//            repo.createLineLayer(layerName).collect { result ->
//                when(result) {
//                    is Results.Success -> {
//                        loadLayers()
//                        _mapState.update { currentState ->
//                            currentState.copy(selectedLayer = layerName)
//                        }
//                        updateTableName(layerName)
//                        Log.d("MapViewModel", "Line layer '$layerName' created successfully.")
//                    }
//                    is Results.Error -> {
//                        Log.e("MapViewModel", "Error creating line layer: ${result.message}")
//                    }
//                    is Results.Loading -> {}
//                }
//            }
//        }
//    }

//    fun setupLineLayer(style: Style) {
//        style.addSource(lineSource)
//        val lineLayer = LineLayer("line-layer", "line-source").apply {
//            lineColor("red")
//            lineWidth(3.0)
//        }
//        style.addLayer(lineLayer)
//    }


    fun closeDatabase() {
        repo.closeDatabase()
    }
}
