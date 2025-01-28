package com.example.mapapp.ViewModels

import com.mapbox.maps.Style
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MapState(
    val currentStyle: String = Style.MAPBOX_STREETS,
    val layers: List<MapLayer> = emptyList()
)

data class MapLayer(val type: LayerType, val data: Any)

enum class LayerType {
    POINT,
    LINE,
    CIRCLE,
    POLYGON
}