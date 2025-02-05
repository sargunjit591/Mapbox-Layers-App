package com.example.mapapp.ui.theme

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
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
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorListener
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var spinner: Spinner
    private lateinit var mBinding: ActivityMapBinding
    var isButtonPressed = false
    var isSet = false
    var isLineMode=false
    private val viewModel: MapViewModel by viewModels{
        MapViewModelFactory(this)
    }

    private val rotateOpen: Animation by lazy {
        AnimationUtils.loadAnimation(this, R.anim.rotate_open_anim)
    }
    private val rotateClose: Animation by lazy {
        AnimationUtils.loadAnimation(this, R.anim.rotate_close_anim)
    }
    private val fromBottom: Animation by lazy {
        AnimationUtils.loadAnimation(this, R.anim.from_bottom_anim)
    }
    private val toBottom: Animation by lazy {
        AnimationUtils.loadAnimation(this, R.anim.to_bottom_anim)
    }
    private var clicked = false

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

        mBinding.apply {
            btnSelectLayers.setOnClickListener {
                showLayerSelectionDialog()
            }
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
                setVisibility(clicked)
                setAnimation(clicked)
                setClickable(clicked)
                clicked = !clicked
            }

            mBinding.point.setOnClickListener {
                isLineMode=false
                val mDialogView = LayoutInflater.from(this@MapActivity)
                    .inflate(R.layout.alert_box_1, null)

                val colorPickerView = mDialogView.findViewById<ColorPickerView>(R.id.colorPickerView)
                val layerNameInput = mDialogView.findViewById<EditText>(R.id.etLayerName)
                var selectedColor = 0

                colorPickerView.setColorListener(object : ColorListener {
                    override fun onColorSelected(color: Int, fromUser: Boolean) {
                        selectedColor = color
                    }
                })

                val mBuilder = AlertDialog.Builder(this@MapActivity)
                    .setView(mDialogView)
                val mAlertDialog = mBuilder.show()

                mDialogView.findViewById<Button>(R.id.buttonConfirm).setOnClickListener {
                    isButtonPressed = !isButtonPressed

                    val colorHex = String.format("#%08X", selectedColor)
                    Toast.makeText(
                        this@MapActivity,
                        "Chosen color is: $colorHex",
                        Toast.LENGTH_SHORT
                    ).show()
                    mAlertDialog.dismiss()

                    val layerName = layerNameInput.text.toString().trim()
                    if (layerName.isNotEmpty()) {
                        val newLayer = MapLayer(
                            type = LayerType.POINT,
                            color = selectedColor,
                            isVisible = true,
                            id = layerName
                        )
                        viewModel.createNewPointLayer(newLayer)
                        Toast.makeText(this@MapActivity, "New Layer Created: $layerName", Toast.LENGTH_SHORT).show()
                        mapView.getMapboxMap().getStyle { style ->
                            addPointLayerToStyle(newLayer, style)}
                    } else {
                        Toast.makeText(this@MapActivity, "Layer name cannot be empty!", Toast.LENGTH_SHORT).show()
                    }
                }

                mDialogView.findViewById<Button>(R.id.buttonCancel).setOnClickListener {
                    mAlertDialog.dismiss()
                }
            }

            mBinding.line.setOnClickListener {
                isLineMode = true

                val mDialogView = LayoutInflater.from(this@MapActivity)
                    .inflate(R.layout.alert_box_2, null)

                val colorPickerView = mDialogView.findViewById<ColorPickerView>(R.id.colorPickerView)
                val layerNameInput = mDialogView.findViewById<EditText>(R.id.etLayerName)
                var selectedColor = 0

                colorPickerView.setColorListener(object : ColorListener {
                    override fun onColorSelected(color: Int, fromUser: Boolean) {
                        selectedColor = color
                    }
                })

                val mBuilder = AlertDialog.Builder(this@MapActivity)
                    .setView(mDialogView)
                val mAlertDialog = mBuilder.show()

                mDialogView.findViewById<Button>(R.id.buttonConfirm).setOnClickListener {
                    isButtonPressed = !isButtonPressed

                    val colorHex = String.format("#%08X", selectedColor)
                    Toast.makeText(
                        this@MapActivity,
                        "Chosen color is: $colorHex",
                        Toast.LENGTH_SHORT
                    ).show()

                    mAlertDialog.dismiss()

                    val layerName = layerNameInput.text.toString().trim()
                    if (layerName.isNotEmpty()) {
                        MapLayer(
                            LayerType.LINE,
                            color =  0xFF00FF00.toInt(),
                            id = layerName,
                            isVisible = true
                        )
                        viewModel.createNewLineLayer(MapLayer(type = LayerType.LINE,color =selectedColor, isVisible = true,id= layerName ))
                        Toast.makeText(
                            this@MapActivity,
                            "New Line Layer Created: $layerName",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MapActivity,
                            "Layer name cannot be empty!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                mDialogView.findViewById<Button>(R.id.buttonCancel).setOnClickListener {
                    mAlertDialog.dismiss()
                }
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

    private fun setVisibility(clicked: Boolean) {
        mBinding.apply {
            if(!clicked){
                point.visibility = View.VISIBLE
                line.visibility = View.VISIBLE
                circle.visibility = View.VISIBLE
                polygon.visibility = View.VISIBLE
                save.visibility = View.VISIBLE
                load.visibility = View.VISIBLE
            } else {
                point.visibility = View.INVISIBLE
                line.visibility = View.INVISIBLE
                circle.visibility = View.INVISIBLE
                polygon.visibility = View.INVISIBLE
                save.visibility = View.INVISIBLE
            }
        }
    }

    private fun setAnimation(clicked: Boolean) {
        mBinding.apply {
            if(!clicked){
                point.startAnimation(fromBottom)
                line.startAnimation(fromBottom)
                circle.startAnimation(fromBottom)
                polygon.startAnimation(fromBottom)
                save.startAnimation(fromBottom)
                load.startAnimation(fromBottom)
                add.startAnimation(rotateOpen)
            } else {
                point.startAnimation(toBottom)
                line.startAnimation(toBottom)
                circle.startAnimation(toBottom)
                polygon.startAnimation(toBottom)
                save.startAnimation(toBottom)
                load.startAnimation(toBottom)
                add.startAnimation(rotateClose)
            }
        }
    }

    private fun setClickable(clicked: Boolean) {
        mBinding.apply {
            if (clicked) {
                point.isClickable = false
                line.isClickable = false
                circle.isClickable = false
                polygon.isClickable = false
                save.isClickable = false
                load.isClickable = false
            } else {
                point.isClickable = true
                line.isClickable = true
                circle.isClickable = true
                polygon.isClickable = true
                save.isClickable = true
                load.isClickable = true
            }
        }
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
            if (isLineMode) {
                viewModel.addPointForLine(point.latitude(), point.longitude())
            } else {
                viewModel.addMarker(point.latitude(), point.longitude())
            }
            val activeLayer = viewModel.mapState.value.activeLayer ?: return@addOnMapClickListener true
            val sourceId = "${activeLayer.id}-source"
            mapView.getMapboxMap().getStyle { style ->
                style.getSourceAs<GeoJsonSource>(sourceId)?.featureCollection(
                    FeatureCollection.fromFeatures(activeLayer.markerFeatures)
                )
            }
            true
        }

        mapView.gestures.addOnMapLongClickListener { point ->
            if (isButtonPressed) {
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
}
