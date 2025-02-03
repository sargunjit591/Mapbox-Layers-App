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
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MapViewModel(context: Context) : ViewModel() {
    private val _mapState = MutableStateFlow(MapState())
    val mapState: StateFlow<MapState> = _mapState

    private val markerFeatures = mutableListOf<Feature>()
    private val lineFeatures = mutableListOf<Feature>()
    private val linePointFeatures = mutableListOf<Feature>()
    private var tempLinePoint: Pair<Double, Double>? = null
    private val repo = GeoPackageRepository(context)

    fun createNewPointLayer(layer:MapLayer) {
        viewModelScope.launch {
            repo.createLayer(layer).collect { result ->
                when(result){
                    is Results.Error -> {}
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

    fun updateVisibleLayers(visibleLayerIds: MutableList<String>) {
        val updatedLayers = _mapState.value.layers.mapValues { (id, layer) ->
            layer.copy(isVisible = visibleLayerIds.contains(id))
        }.toMutableMap()

        val currentActive = _mapState.value.activeLayer
        val newActive = if (currentActive != null &&
            updatedLayers[currentActive.id]?.isVisible == true) {
            currentActive
        } else {
            updatedLayers.values.find { it.isVisible }
        }

        _mapState.value = _mapState.value.copy(layers = updatedLayers, activeLayer = newActive)
    }


    private val geoJsonSource: GeoJsonSource by lazy {
        GeoJsonSource.Builder("marker-source")
            .featureCollection(FeatureCollection.fromFeatures(markerFeatures))
            .build()
    }

    private val lineSource: GeoJsonSource by lazy {
        GeoJsonSource.Builder("line-source")
            .featureCollection(FeatureCollection.fromFeatures(emptyArray()))
            .build()
    }

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

    fun deleteMarker(lat: Double, lng: Double) {
        val iterator = markerFeatures.iterator()
        while (iterator.hasNext()) {
            val feature = iterator.next()
            val point = feature.geometry() as? Point
            if (point != null && isNearLocation(point.latitude(), point.longitude(), lat, lng)) {
                iterator.remove()
                geoJsonSource.featureCollection(FeatureCollection.fromFeatures(markerFeatures))
                break
            }
        }
    }

    private fun isNearLocation(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Boolean {
        val threshold = 0.0001
        return (Math.abs(lat1 - lat2) < threshold && Math.abs(lng1 - lng2) < threshold)
    }

    fun deleteLayer(layerName: String) {
        viewModelScope.launch {
            repo.deleteLayer(layerName).collect { result ->
                when (result) {
                    is Results.Success -> {
                        Log.d("GeoPackage", "Layer '$layerName' deleted successfully")
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

    private fun addLinePointMarker(lat: Double, lng: Double) {
        val markerFeature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addStringProperty("icon", "ic_point2")
        }
        linePointFeatures.add(markerFeature)
        updateLinesOnMap()
    }

    fun addPointForLine(lat: Double, lng: Double) {
        if (tempLinePoint == null) {
            tempLinePoint = lat to lng
            addLinePointMarker(lat, lng)
        } else {
            val lastPoint = tempLinePoint!!
            val newSegment = Pair(lastPoint, lat to lng)

            _mapState.update { currentState ->
                currentState.copy(lineSegments = currentState.lineSegments + newSegment)
            }

            tempLinePoint = lat to lng

            updateLinesOnMap()
            addLinePointMarker(lat, lng)

            viewModelScope.launch {
                repo.saveLineSegment(lastPoint, lat to lng, _mapState.value.activeLayer?.id ?: "")
                    .collect { result ->
                        when (result) {
                            is Results.Success -> {
                                _mapState.update { currentState ->
                                    val updatedLayers = currentState.layers.toMutableMap()
                                    updatedLayers[currentState.activeLayer?.id]?.let { activeLayer ->
                                        updatedLayers[currentState.activeLayer!!.id] = activeLayer.copy(isVisible = true)
                                    }
                                    currentState.copy(layers = updatedLayers)
                                }
                                loadAllLineSegments()
                            }
                            is Results.Error -> {}
                            is Results.Loading -> {}
                        }
                    }
            }
        }
    }

    fun updateLinesOnMap() {
        lineFeatures.clear()
        val computedLines = _mapState.value.lineSegments.map { segment ->
            val (start, end) = segment
            val startPoint = Point.fromLngLat(start.second, start.first)
            val endPoint   = Point.fromLngLat(end.second, end.first)
            Feature.fromGeometry(LineString.fromLngLats(listOf(startPoint, endPoint)))
        }
        lineFeatures.addAll(computedLines)

        val allFeatures = mutableListOf<Feature>()
        allFeatures.addAll(lineFeatures)
        allFeatures.addAll(linePointFeatures)

        lineSource.featureCollection(FeatureCollection.fromFeatures(allFeatures))
    }

    fun createNewLineLayer(layerName: MapLayer) {
        viewModelScope.launch {
            repo.createLayer(layerName).collect { result ->
                when(result) {
                    is Results.Success -> {
                        val newLayer = MapLayer(
                            type = LayerType.LINE,
                            color = 0xFF00FF00.toInt(),
                            id = layerName.id,
                            isVisible = false
                        )
                        _mapState.update { currentState ->
                            val updatedLayers = currentState.layers.toMutableMap()
                            updatedLayers[layerName.id] = newLayer
                            currentState.copy(
                                activeLayer = newLayer,
                                layers = updatedLayers
                            )
                        }
                        Log.d("MapViewModel", "Line layer '${layerName.id}' created successfully.")
                    }
                    is Results.Error -> {
                        Log.e("MapViewModel", "Error creating line layer: ${result.message}")
                    }
                    is Results.Loading -> {}
                }
            }
        }
    }

    fun setupLineLayer(style: Style) {
        if (style.getSource("line-source") == null) {
            style.addSource(lineSource)
        }

        if (style.getLayer("line-layer") == null) {
            val lineLayer = LineLayer("line-layer", "line-source").apply {
                lineColor("red")
                lineWidth(3.0)
                filter(Expression.eq(Expression.literal("\$type"), Expression.literal("LineString")))
            }
            style.addLayer(lineLayer)
        }

        if (style.getLayer("line-point-layer") == null) {
            val pointLayer = SymbolLayer("line-point-layer", "line-source").apply {
                iconImage("ic_point2")
                iconSize(0.5)
                iconAllowOverlap(true)
                iconIgnorePlacement(true)
                filter(Expression.eq(Expression.literal("\$type"), Expression.literal("Point")))
            }
            style.addLayer(pointLayer)
        }


    }

    fun loadAllLineSegments() {
        viewModelScope.launch {
            val visibleLineLayers = _mapState.value.layers.values
                .filter { it.isVisible && it.type == LayerType.LINE }

            val allSegments = mutableListOf<Pair<Pair<Double, Double>, Pair<Double, Double>>>()

            visibleLineLayers.forEach { layer ->
                repo.loadAllLineSegments(layer.id).collect { result ->
                    if (result is Results.Success) {
                        result.data?.let { allSegments.addAll(it) }
                    }
                }
            }

            _mapState.update { currentState ->
                currentState.copy(lineSegments = allSegments)
            }

            updateLinesOnMap()
        }
    }

    fun closeDatabase() {
        repo.closeDatabase()
    }
}
