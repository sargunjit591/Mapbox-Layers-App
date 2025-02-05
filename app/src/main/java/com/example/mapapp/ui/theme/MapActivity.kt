package com.example.mapapp.ui.theme

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.VectorDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.locationcomponent.location
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.mapapp.R
import com.example.mapapp.ViewModels.LayerType
import com.example.mapapp.ViewModels.MapLayer
import com.example.mapapp.ViewModels.MapViewModel
import com.example.mapapp.ViewModels.MapViewModelFactory
import com.example.mapapp.databinding.ActivityMapBinding
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.gestures.gestures
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorListener
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var spinner: Spinner
    private lateinit var mBinding: ActivityMapBinding
    private var isButtonPressed = false
    private var isSet = false
    private var isLineMode=false
    private var isEditing = false
    private val viewModel: MapViewModel by viewModels{
        MapViewModelFactory(this)
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission()
        } else {
            accessLocation()
        }

        mapView = findViewById(R.id.mapView)

        mapView.getMapboxMap().loadStyleUri(Style.STANDARD) { style ->
            enableLocationComponent()
            viewModel.mapState.value.layers.values.forEach { layer ->
                if (layer.isVisible) {
                    when (layer.type) {
                        LayerType.POINT -> viewModel.addPointLayerToStyle(layer, style)
                        LayerType.LINE -> viewModel.addLineLayerToStyle(layer, style)
                        else -> {}
                    }
                    val sourceId = "${layer.id}-source"
                    style.getSourceAs<GeoJsonSource>(sourceId)?.featureCollection(
                        FeatureCollection.fromFeatures(layer.markerFeatures)
                    )
                }
            }
            setupMapInteractions()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mapState.collect { state ->
                    mapView.getMapboxMap().getStyle { style ->
                        state.layers.values.forEach { layer ->
                            val sourceId = "${layer.id}-source"
                            style.getSourceAs<GeoJsonSource>(sourceId)?.featureCollection(
                                FeatureCollection.fromFeatures(layer.markerFeatures)
                            )
                            if (layer.isVisible) {
                                when (layer.type) {
                                    LayerType.POINT -> viewModel.addPointLayerToStyle(layer, style)
                                    LayerType.LINE -> viewModel.addLineLayerToStyle(layer, style)
                                    else -> {}
                                }
                            } else {
                                style.getLayer("${layer.id}-symbol-layer")?.visibility(Visibility.NONE)
                                style.getLayer("${layer.id}-line-layer")?.visibility(Visibility.NONE)
                            }
                        }
                    }
                }
            }
        }

        spinner = findViewById(R.id.spinner)

        val spinnerList = listOf(
            "Standard 🗺" to Style.STANDARD,
            "Satellite 🛰" to Style.SATELLITE,
            "Outdoors 🚏" to Style.OUTDOORS,
            "Street 🛣" to Style.MAPBOX_STREETS,
            "Traffic 🚦" to Style.TRAFFIC_DAY,
            "Dark 🌑" to Style.DARK
        )

        val arrayAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerList.map { it.first })
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = arrayAdapter


        mBinding.btnSelectLayers.setOnClickListener {
            showLayerSelectionDialog()
        }

        viewModel.apply {
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedStyle = spinnerList[position].second
                    mapView.getMapboxMap().loadStyleUri(selectedStyle) { style ->

                        val drawable = ContextCompat.getDrawable(
                            this@MapActivity,
                            R.drawable.ic_point2
                        )
                        if (drawable is VectorDrawable) {
                            val bitmap = Bitmap.createBitmap(
                                drawable.intrinsicWidth,
                                drawable.intrinsicHeight,
                                Bitmap.Config.ARGB_8888
                            )
                            val canvas = Canvas(bitmap)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            style.addImage("ic_point2", bitmap)
                        }

                        mapState.value.layers.values.forEach { layer ->
                            if (layer.type == LayerType.POINT) {
                                addPointLayerToStyle(layer, style)
                            } else if (layer.type == LayerType.LINE) {
                                addLineLayerToStyle(layer, style)
                            }
                        }

                        setupMapInteractions()
                    }
                    updateStyle(selectedStyle)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            mBinding.add.setOnClickListener {
                val mDialogView = LayoutInflater.from(this@MapActivity)
                    .inflate(R.layout.add_layer_box, null)

                val radioGroup = mDialogView.findViewById<RadioGroup>(R.id.radiogroup)
                val radioPoint = mDialogView.findViewById<RadioButton>(R.id.Point)
                val radioLine = mDialogView.findViewById<RadioButton>(R.id.Line)
                val radioPolygon = mDialogView.findViewById<RadioButton>(R.id.Polygon)

                val colorPickerView = mDialogView.findViewById<ColorPickerView>(R.id.colorPickerView)
                val layerNameInput = mDialogView.findViewById<EditText>(R.id.etLayerName)

                 val brightnessSlideBar = mDialogView.findViewById<BrightnessSlideBar>(R.id.brightnessSlideBar)
                 colorPickerView.attachBrightnessSlider(brightnessSlideBar)

                var selectedColor = 0
                colorPickerView.setColorListener(
                    object : com.skydoves.colorpickerview.listeners.ColorEnvelopeListener {
                        override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
                            selectedColor = envelope.color
                        }
                    }
                )

                val mBuilder = AlertDialog.Builder(this@MapActivity)
                    .setView(mDialogView)
                val mAlertDialog = mBuilder.show()

                mDialogView.findViewById<Button>(R.id.buttonConfirm).setOnClickListener {
                    isButtonPressed=!isButtonPressed
                    val layerName = layerNameInput.text.toString().trim()
                    if (layerName.isEmpty()) {
                        Toast.makeText(this@MapActivity, "Layer name cannot be empty!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val colorHex = String.format("#%08X", selectedColor)
                    Toast.makeText(this@MapActivity, "Chosen color: $colorHex", Toast.LENGTH_SHORT).show()

                    when (radioGroup.checkedRadioButtonId) {
                        R.id.Point -> {
                            val newLayer = MapLayer(
                                type = LayerType.POINT,
                                color = selectedColor,
                                isVisible = true,
                                id = layerName
                            )
                            viewModel.createNewPointLayer(newLayer)
                            Toast.makeText(
                                this@MapActivity,
                                "New Point Layer Created: $layerName",
                                Toast.LENGTH_SHORT
                            ).show()
                            mapView.getMapboxMap().getStyle { style ->
                                addPointLayerToStyle(newLayer, style)
                            }
                        }

                        R.id.Line -> {
                            isLineMode=true
                            val newLayer = MapLayer(
                                type = LayerType.LINE,
                                color = selectedColor,
                                isVisible = true,
                                id = layerName
                            )
                            viewModel.createNewLineLayer(newLayer)
                            Toast.makeText(
                                this@MapActivity,
                                "New Line Layer Created: $layerName",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        R.id.Polygon -> {
                        }

                        else -> {
                            Toast.makeText(this@MapActivity, "Please select a layer type!", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                    }
                    mAlertDialog.dismiss()
                }

                mDialogView.findViewById<Button>(R.id.buttonCancel).setOnClickListener {
                    mAlertDialog.dismiss()
                }
            }

            mBinding.edit.setOnClickListener {
                mBinding.Cancel.visibility=View.VISIBLE
                showEditLayerDialog()
            }

            mBinding.Cancel.setOnClickListener {
                mBinding.Cancel.visibility = View.GONE
                isEditing = false
                viewModel.setActiveLayer(null)
                Toast.makeText(this@MapActivity, "Editing cancelled", Toast.LENGTH_SHORT).show()
            }

            mBinding.btnDeleteLayer.setOnClickListener {
                val layerNames = viewModel.mapState.value.layers.keys.toList()
                if (layerNames.isEmpty()) {
                    Toast.makeText(this@MapActivity, "No layers available to delete", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                AlertDialog.Builder(this@MapActivity)
                    .setTitle("Delete Layer")
                    .setItems(layerNames.toTypedArray()) { _, which ->
                        val selectedLayer = layerNames[which]
                        AlertDialog.Builder(this@MapActivity)
                            .setTitle("Confirm Deletion")
                            .setMessage("Are you sure you want to delete the layer '$selectedLayer'? This cannot be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                viewModel.deleteLayer(selectedLayer)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    .show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.closeDatabase()
        Log.d("GeoPackage", "GeoPackage database closed")
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted && coarseLocationGranted) {
            accessLocation()
        } else {
            Toast.makeText(this, "Location Permission Denied", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestLocationPermission() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun accessLocation() {
        Toast.makeText(this, "Location Access Granted!!", Toast.LENGTH_LONG).show()
    }

    private fun enableLocationComponent() {
        val locationPlugin = mapView.location
        locationPlugin.updateSettings {
            enabled = true
            pulsingEnabled = true
        }
        locationPlugin.addOnIndicatorPositionChangedListener { point ->
            if(isSet) return@addOnIndicatorPositionChangedListener
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(point)
                    .zoom(20.0)
                    .build()
            )
            isSet = true
        }
    }

    private fun setupMapInteractions() {
        mapView.gestures.addOnMapClickListener { point ->
            if (isEditing) {
                val activeLayer = viewModel.mapState.value.activeLayer ?: return@addOnMapClickListener true
                if (isLineMode && activeLayer.type == LayerType.LINE) {
                    viewModel.addPointForLine(point.latitude(), point.longitude())
                } else if (activeLayer.type == LayerType.POINT) {
                    viewModel.addMarker(point.latitude(), point.longitude())
                }

                val sourceId = "${activeLayer.id}-source"
                mapView.getMapboxMap().getStyle { style ->
                    style.getSourceAs<GeoJsonSource>(sourceId)?.featureCollection(
                        FeatureCollection.fromFeatures(activeLayer.markerFeatures)
                    )
                }
            }
            true
        }

        mapView.gestures.addOnMapLongClickListener { point ->
            if (isEditing && isButtonPressed) {
                viewModel.deleteMarker(point.latitude(), point.longitude())
                val activeLayer = viewModel.mapState.value.activeLayer
                if (activeLayer != null && activeLayer.type == LayerType.POINT) {
                    mapView.getMapboxMap().getStyle { style ->
                        val sourceId = "${activeLayer.id}-source"
                        style.getSourceAs<GeoJsonSource>(sourceId)?.featureCollection(
                            FeatureCollection.fromFeatures(activeLayer.markerFeatures)
                        )
                    }
                }
            }
            true
        }
    }

    private fun showLayerSelectionDialog() {
        val allLayerIds = viewModel.mapState.value.layers.keys.toList()
        val checkedItems = allLayerIds.map { id ->
            viewModel.mapState.value.layers[id]?.isVisible == true
        }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Select Layers to View")
            .setMultiChoiceItems(allLayerIds.toTypedArray(), checkedItems) { _, which, isChecked ->
                val layerId = allLayerIds[which]
                viewModel.mapState.value.layers[layerId]?.isVisible = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                val selectedLayers = allLayerIds.filter { id ->
                    viewModel.mapState.value.layers[id]?.isVisible == true
                }
                viewModel.updateVisibleLayers(selectedLayers)

                mapView.getMapboxMap().getStyle { style ->
                    for ((layerId, layer) in viewModel.mapState.value.layers) {
                        if (layer.isVisible) {
                            if (layer.type == LayerType.POINT) {
                                viewModel.addPointLayerToStyle(layer, style)
                            } else if (layer.type == LayerType.LINE) {
                                viewModel.addLineLayerToStyle(layer, style)
                            }
                        } else {
                            if (layer.type == LayerType.POINT) {
                                style.getLayer("${layerId}-symbol-layer")
                                    ?.visibility(Visibility.NONE)
                          } else if (layer.type == LayerType.LINE) {
                                style.getLayer("${layerId}-line-layer")
                                    ?.visibility(Visibility.NONE)
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditLayerDialog() {
        val layerNames = viewModel.mapState.value.layers.keys.toList()

        if (layerNames.isEmpty()) {
            Toast.makeText(this, "No layers exist to edit", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select a layer to edit")
            .setItems(layerNames.toTypedArray()) { dialog, which ->
                val selectedLayerId = layerNames[which]
                val selectedLayer = viewModel.mapState.value.layers[selectedLayerId]

                if (selectedLayer != null) {
                    selectedLayer.isVisible = true

                    viewModel.setActiveLayer(selectedLayer)
                    isLineMode = (selectedLayer.type == LayerType.LINE)
                    isEditing = true

                    mapView.getMapboxMap().getStyle { style ->
                        when (selectedLayer.type) {
                            LayerType.POINT -> viewModel.addPointLayerToStyle(selectedLayer, style)
                            LayerType.LINE  -> viewModel.addLineLayerToStyle(selectedLayer, style)
                            else -> {}
                        }
                    }

                    Toast.makeText(this, "Editing layer: $selectedLayerId", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
