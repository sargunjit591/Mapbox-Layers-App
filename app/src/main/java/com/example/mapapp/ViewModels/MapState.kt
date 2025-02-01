package com.example.mapapp.ViewModels

import com.mapbox.maps.Style

data class MapState(
    val currentStyle: String = Style.MAPBOX_STREETS,
    val layers: List<MapLayer> = emptyList(),
    val latLngList: List<Pair<Double, Double>> = emptyList(),
    val selectedLayer: String = "",
    val selectedLayers: Set<String> = emptySet(),
    val activeLayer: String? = null
)

data class MapLayer(val type: LayerType, val data: Any)

enum class LayerType {
    POINT,
    LINE,
    CIRCLE,
    POLYGON
}

sealed class Results<T>(val data: T? = null, val message: String? = null) {
    class Success<T>(data: T?) : Results<T>(data)
    class Error<T>(message: String, data: T? = null) : Results<T>(data, message)
    class Loading<T>(val isLoading: Boolean = true) : Results<T>(null)
}
