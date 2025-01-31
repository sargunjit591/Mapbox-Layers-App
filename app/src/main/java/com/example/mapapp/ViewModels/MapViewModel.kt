package com.example.mapapp.ViewModels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapapp.Database.GeoPackageRepository
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
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

    private val _selectedLayer = MutableStateFlow<String>("default_layer")
    val selectedLayer: StateFlow<String> = _selectedLayer

    private val geoJsonSource: GeoJsonSource by lazy {
        GeoJsonSource.Builder("marker-source")
            .featureCollection(FeatureCollection.fromFeatures(markerFeatures))
            .build()
    }

    init {
        val sharedPref = context.getSharedPreferences("MapPreferences", Context.MODE_PRIVATE)
        tableName = sharedPref.getString("LAST_TABLE_NAME", null)

        if (tableName != null) {
            Log.d("MapViewModel", "Last used table name: $tableName")
            repo.initializeDatabase(tableName!!)
            loadAllMarkers()
        } else {
            Log.d("MapViewModel", "No previously used table found")
        }
    }


    fun updateTableName(layerName: String) {
        tableName = layerName
        Log.d("MapViewModel", "Table name updated: $tableName")

        repo.initializeDatabase(tableName!!)
        loadAllMarkers()
    }


    fun updateStyle(styleUri: String) {
        _mapState.update { it.copy(currentStyle = styleUri) }
    }

    fun setupGeoJsonSource(style: Style) {
        style.addSource(geoJsonSource)
    }

    fun setupSymbolLayer(style: Style) {
        val symbolLayer = SymbolLayer("marker-layer", "marker-source")
        symbolLayer.iconImage("{icon}")
        symbolLayer.iconAllowOverlap(true)
        symbolLayer.iconIgnorePlacement(true)
        //symbolLayer.iconColor("{color}")

        style.addLayer(symbolLayer)
    }

    fun addMarker(lat: Double, lng: Double) {
        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addStringProperty("icon", "ic_point2")
            //addStringProperty("color", "#FF0000")
        }

        markerFeatures.add(feature)
        geoJsonSource.featureCollection(FeatureCollection.fromFeatures(markerFeatures))

        viewModelScope.launch {
            tableName?.let {
                repo.saveLatLng(lat, lng, it).collect { result ->
                    when (result) {
                        is Results.Loading -> Log.d("GeoPackage", "Saving LatLng... ${result.isLoading}")
                        is Results.Success -> {
                            Log.d("GeoPackage", "LatLng saved successfully!")
        //                        loadAllMarkers()
                        }

                        is Results.Error -> Log.e("GeoPackage", "Error saving LatLng: ${result.message}")
                    }
                }
            }
        }
    }

    fun loadAllMarkers() {
        viewModelScope.launch {
            val table = tableName
            if (table.isNullOrEmpty()) {
                Log.e("GeoPackage", "Table name is not set! Cannot load markers.")
                return@launch
            }

            Log.d("GeoPackage", "Loading markers from table: $table")

            repo.loadAllLatLng(table).collect { result ->
                when (result) {
                    is Results.Loading -> Log.d("GeoPackage", "⌛ Loading LatLng...")
                    is Results.Success -> {
                        Log.d("GeoPackage", "Loaded ${result.data?.size ?: 0} markers")

                        _latLngList.value = result.data ?: emptyList()

                        markerFeatures.clear()

                        for ((lat, lng) in _latLngList.value) {
                            val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
                                addStringProperty("icon", "ic_point2")
                            }
                            markerFeatures.add(feature)
                        }
                        geoJsonSource.featureCollection(FeatureCollection.fromFeatures(markerFeatures))

                        Log.d("GeoPackage", "Markers successfully updated on the map")
                    }

                    is Results.Error -> Log.e("GeoPackage", "Error loading LatLng: ${result.message}")
                }
            }
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
        //                        loadAllMarkers()
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
                }
            }
        }
    }

    fun setSelectedLayer(layerName: String) {
        _selectedLayer.value = layerName
    }

    fun loadLayers() {
        viewModelScope.launch {
            repo.loadLayers().collect { result ->
                when (result) {
                    is Results.Loading -> Log.d("GeoPackage", "⌛ Loading layers...")
                    is Results.Success -> {
                        _layers.value = result.data ?: emptyList()
                        Log.d("GeoPackage", "Loaded layers: ${_layers.value}")

                        if (_layers.value.isNotEmpty()) {
                            setSelectedLayer(_layers.value.first())
                            updateTableName(_layers.value.first())
                            loadAllMarkers()
                        }
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
