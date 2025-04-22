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
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MapViewModel(context: Context) : ViewModel() {
    private val _mapState = MutableStateFlow(MapState())
    val mapState: StateFlow<MapState> = _mapState
    private val repo = GeoPackageRepository(context)
    private var tempLinePoint: Pair<Double, Double>? = null

    fun createNewPointLayer(newLayer: MapLayer) {
        viewModelScope.launch {
            repo.createLayer(newLayer).collect { result ->
                if (result is Results.Success && result.data == true) {
                    _mapState.value.layers[newLayer.id] = newLayer
                    _mapState.value = _mapState.value.copy(activeLayer = newLayer)
                }
            }
        }
    }

    init {
        repo.initializeDatabase()
        viewModelScope.launch {
            repo.loadLayers().collect { result ->
                when (result) {
                    is Results.Loading -> {}
                    is Results.Success -> {
                        val dbLayers = result.data ?: emptyList()
                        val layerMap = dbLayers.associateBy { it.id }.toMutableMap()
                        _mapState.value = _mapState.value.copy(layers = layerMap)

                        dbLayers.forEach { layer ->
                            when (layer.type) {
                                LayerType.POINT -> {
                                    repo.loadAllLatLng(layer.id).collect { pointsResult ->
                                        if (pointsResult is Results.Success) {
                                            pointsResult.data?.forEachIndexed { index, (lat, lng) ->
                                                val feature = Feature.fromGeometry(
                                                    Point.fromLngLat(lng, lat)
                                                ).apply {
                                                    addStringProperty("icon", "ic_point2")
                                                    addStringProperty(
                                                        "point_id",
                                                        "${layer.id}-Point${index+1}"
                                                    )
                                                }
                                                layer.markerFeatures.add(feature)
                                            }
                                        }
                                    }
                                }
                                LayerType.LINE -> {
                                    repo.loadAllLineSegments(layer.id).collect { segResult ->
                                        if (segResult is Results.Success) {
                                            segResult.data?.forEach { (start, end) ->
                                                val (lat1, lng1) = start
                                                val (lat2, lng2) = end
                                                val line = Feature.fromGeometry(
                                                    LineString.fromLngLats(
                                                        listOf(
                                                            Point.fromLngLat(lng1, lat1),
                                                            Point.fromLngLat(lng2, lat2)
                                                        )
                                                    )
                                                )
                                                layer.markerFeatures.add(line)

                                                val startPt = Feature.fromGeometry(
                                                    Point.fromLngLat(lng1, lat1)
                                                ).apply {
                                                    addStringProperty("icon", "ic_point2")
                                                }
                                                val endPt = Feature.fromGeometry(
                                                    Point.fromLngLat(lng2, lat2)
                                                ).apply {
                                                    addStringProperty("icon", "ic_point2")
                                                }
                                                layer.markerFeatures.add(startPt)
                                                layer.markerFeatures.add(endPt)
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                    is Results.Error -> {
                        Log.e("MapViewModel", "Error loading layers: ${result.message}")
                    }
                }
            }
        }
    }

    fun setActiveLayer(layer: MapLayer?) {
        _mapState.update { currentState ->
            currentState.copy(activeLayer = layer)
        }
    }

    fun updateVisibleLayers(visibleLayerIds: List<String>) {
        _mapState.value.layers.forEach { (id, layer) ->
            layer.isVisible = visibleLayerIds.contains(id)
        }
        val newActive = _mapState.value.layers.values.firstOrNull { it.isVisible }
        _mapState.value = _mapState.value.copy(activeLayer = newActive)
    }

    fun updateStyle(styleUri: String) {
        _mapState.update { it.copy(currentStyle = styleUri) }
    }

    // POINT LAYER

    fun addPointLayerToStyle(layer: MapLayer, style: Style) {
        val sourceId = "${layer.id}-source"

        if (style.getSource(sourceId) == null) {
            GeoJsonSource.Builder(sourceId)
                .featureCollection(FeatureCollection.fromFeatures(layer.markerFeatures))
                .build().also { style.addSource(it) }
        } else {
            style.getSourceAs<GeoJsonSource>(sourceId)?.apply {
                featureCollection(FeatureCollection.fromFeatures(layer.markerFeatures))
            }
        }

        val symbolLayerId = "${layer.id}-symbol-layer"
        if (style.getLayer(symbolLayerId) == null) {
            val symbolLayer = SymbolLayer(symbolLayerId, sourceId).apply {
                iconImage("{icon}")
                iconSize(1.0)
                iconAllowOverlap(true)
                iconIgnorePlacement(true)
                textField(Expression.get("point_id"))
                textSize(literal(12.0))
                textColor(Color.BLACK)
                textHaloColor(Color.WHITE)
                textHaloWidth(literal(1.5))
                textAllowOverlap(true)
                textIgnorePlacement(true)
                textOffset(literal(listOf(0.0, -2.0)))
                visibility(
                    if (layer.isVisible) Visibility.VISIBLE
                    else Visibility.NONE
                )
            }
            style.addLayer(symbolLayer)
        } else {
            style.getLayer(symbolLayerId)?.visibility(
                if (layer.isVisible) Visibility.VISIBLE
                else Visibility.NONE
            )
        }
    }

    fun addMarker(lat: Double, lng: Double) {
        val layer = _mapState.value.activeLayer
        if (layer == null || layer.type != LayerType.POINT) {
            Log.e("GeoPackage", "❌ No active POINT layer selected for adding a marker!")
            return
        }

        val pointId = System.currentTimeMillis()
        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addStringProperty("icon", "ic_point2")
            addStringProperty("point_id", "${layer.id} - Point ${pointId}")
        }

        layer.markerFeatures.add(feature)
        viewModelScope.launch {
            repo.saveLatLng(lat, lng, layer.id).collect{

            }
        }
    }

    fun deleteMarker(lat: Double, lng: Double) {
        val layer = _mapState.value.activeLayer ?: return
        if (layer.type != LayerType.POINT) return

        val iterator = layer.markerFeatures.iterator()
        while (iterator.hasNext()) {
            val feature = iterator.next()
            val point = feature.geometry() as? Point ?: continue
            if (isNearLocation(point.latitude(), point.longitude(), lat, lng)) {
                iterator.remove()
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
                if (result is Results.Success && result.data == true) {
                    _mapState.update { state ->
                        val updated = state.layers.toMutableMap()
                        updated.remove(layerName)
                        val newActive = if (state.activeLayer?.id == layerName) null else state.activeLayer
                        state.copy(layers = updated, activeLayer = newActive)
                    }
                }
            }
        }
    }

    //LINE LAYER

    fun addLineLayerToStyle(layer: MapLayer, style: Style) {
        val sourceId = "${layer.id}-source"
        val lineLayerId = "${layer.id}-line-layer"
        val endpointLayerId = "${layer.id}-line-point-layer"

        if (style.getSource(sourceId) == null) {
            style.addSource(
                GeoJsonSource.Builder(sourceId)
                    .featureCollection(FeatureCollection.fromFeatures(layer.markerFeatures))
                    .build()
            )
        } else {
            style.getSourceAs<GeoJsonSource>(sourceId)?.featureCollection(
                FeatureCollection.fromFeatures(layer.markerFeatures)
            )
        }

        if (style.getLayer(lineLayerId) == null) {
            val lineLayer = LineLayer(lineLayerId, sourceId).apply {
                lineColor("red")
                lineWidth(3.0)
                filter(Expression.eq(Expression.literal("\$type"), Expression.literal("LineString")))
                visibility(if (layer.isVisible) Visibility.VISIBLE else Visibility.NONE)
            }
            style.addLayer(lineLayer)
        } else {
            style.getLayer(lineLayerId)?.visibility(
                if (layer.isVisible) Visibility.VISIBLE else Visibility.NONE
            )
        }

        if (style.getLayer(endpointLayerId) == null) {
            val endpointSymbolLayer = SymbolLayer(endpointLayerId, sourceId).apply {
                iconImage("ic_point2")
                iconSize(0.5)
                iconAllowOverlap(true)
                iconIgnorePlacement(true)
                filter(Expression.eq(Expression.literal("\$type"), Expression.literal("Point")))
                visibility(if (layer.isVisible) Visibility.VISIBLE else Visibility.NONE)
            }
            style.addLayerAbove(endpointSymbolLayer, lineLayerId)
        } else {
            style.getLayer(endpointLayerId)?.visibility(
                if (layer.isVisible) Visibility.VISIBLE else Visibility.NONE
            )
        }
    }

    fun addPointForLine(lat: Double, lng: Double) {
        val layer = _mapState.value.activeLayer ?: return
        if (layer.type != LayerType.LINE) {
            Log.e("GeoPackage", "❌ Active layer is not a LINE layer!")
            return
        }

        if (tempLinePoint == null) {
            tempLinePoint = lat to lng
            val startFeature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
                addStringProperty("icon", "ic_point2")
            }
            layer.markerFeatures.add(startFeature)

        } else {
            val (oldLat, oldLng) = tempLinePoint!!
            val lineFeature = Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(
                        Point.fromLngLat(oldLng, oldLat),
                        Point.fromLngLat(lng, lat)
                    )
                )
            )
            layer.markerFeatures.add(lineFeature)

            val endFeature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
                addStringProperty("icon", "ic_point2")
            }
            layer.markerFeatures.add(endFeature)

            viewModelScope.launch {
                repo.saveLineSegment(
                    (oldLat to oldLng),
                    (lat to lng),
                    (layer.id)
                ).collect {}
            }

            tempLinePoint = lat to lng
        }
    }

    fun createNewLineLayer(layerName: MapLayer) {
        viewModelScope.launch {
            repo.createLayer(layerName).collect { result ->
                when(result) {
                    is Results.Success -> {
                        val newLayer = layerName.copy(isVisible = true)
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

    fun closeDatabase() {
        repo.closeDatabase()
    }
}
