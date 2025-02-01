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
import androidx.lifecycle.lifecycleScope
import com.example.mapapp.R
import com.example.mapapp.ViewModels.LayerType
import com.example.mapapp.ViewModels.MapLayer
import com.example.mapapp.ViewModels.MapViewModel
import com.example.mapapp.ViewModels.MapViewModelFactory
import com.example.mapapp.databinding.ActivityMapBinding
import com.mapbox.geojson.Feature
import com.mapbox.maps.plugin.gestures.gestures
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorListener
import kotlinx.coroutines.cancel

class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var spinner: Spinner
    private lateinit var mBinding: ActivityMapBinding
    var isButtonPressed = false
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

        mapView.getMapboxMap().loadStyleUri(Style.STANDARD) {
            enableLocationComponent()
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

//        viewModel.loadAllMarkers()
//        viewModel.loadLayers()
//
//        lifecycleScope.launchWhenStarted {
//            viewModel.mapState.collect { state ->
//                val selectedLayer = state.selectedLayer
//                if (selectedLayer.isNotEmpty()) {
//                    Log.d("MapActivity", "Active layer updated to: $selectedLayer")
//                    viewModel.updateTableName(selectedLayer)
//                    viewModel.loadAllMarkers()
//                }
//            }
//        }

//        setupLayerSelection()

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
                        setupGeoJsonSource(style)
                        setupSymbolLayer(style)
                        //setupMapInteractions()
                        //setupLineLayer(style)

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
                    }
                    updateStyle(selectedStyle)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    mapView.getMapboxMap().loadStyleUri(Style.SATELLITE) { style ->
                        setupGeoJsonSource(style)
                        setupSymbolLayer(style)
                        //setupMapInteractions()
                        //setupLineLayer(style)
                    }
                }
            }

            mBinding.add.setOnClickListener {
                setVisibility(clicked)
                setAnimation(clicked)
                setClickable(clicked)
                clicked = !clicked
            }

            mBinding.point.setOnClickListener {
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
                        viewModel.createNewPointLayer(MapLayer(type = LayerType.POINT,color =selectedColor, isVisible = true,id= layerName ))
//                        viewModel.updateTableName(layerName)
                        Toast.makeText(this@MapActivity, "New Layer Created: $layerName", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MapActivity, "Layer name cannot be empty!", Toast.LENGTH_SHORT).show()
                    }
                }

                mDialogView.findViewById<Button>(R.id.buttonCancel).setOnClickListener {
                    mAlertDialog.dismiss()
                }
            }
//
//            mBinding.line.setOnClickListener {
//                isLineMode = true
//
//                val mDialogView = LayoutInflater.from(this@MapActivity)
//                    .inflate(R.layout.alert_box_2, null)
//
//                val colorPickerView = mDialogView.findViewById<ColorPickerView>(R.id.colorPickerView)
//                val layerNameInput = mDialogView.findViewById<EditText>(R.id.etLayerName)
//                var selectedColor = 0
//
//                colorPickerView.setColorListener(object : ColorListener {
//                    override fun onColorSelected(color: Int, fromUser: Boolean) {
//                        selectedColor = color
//                    }
//                })
//
//                val mBuilder = AlertDialog.Builder(this@MapActivity)
//                    .setView(mDialogView)
//                val mAlertDialog = mBuilder.show()
//
//                mDialogView.findViewById<Button>(R.id.buttonConfirm).setOnClickListener {
//                    isButtonPressed = !isButtonPressed
//
//                    val colorHex = String.format("#%08X", selectedColor)
//                    Toast.makeText(
//                        this@MapActivity,
//                        "Chosen color is: $colorHex",
//                        Toast.LENGTH_SHORT
//                    ).show()
//
//                    mAlertDialog.dismiss()
//
//                    MapLayer(LayerType.LINE, "A new line layer has been added")
//
//                    val layerName = layerNameInput.text.toString().trim()
//                    if (layerName.isNotEmpty()) {
//                        viewModel.createNewLineLayer(layerName)
//                        viewModel.updateTableName(layerName)
//                        Toast.makeText(
//                            this@MapActivity,
//                            "New Line Layer Created: $layerName",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                    } else {
//                        Toast.makeText(
//                            this@MapActivity,
//                            "Layer name cannot be empty!",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                    }
//                }
//
//                mDialogView.findViewById<Button>(R.id.buttonCancel).setOnClickListener {
//                    mAlertDialog.dismiss()
//                }
//            }


//            mBinding.btnDeleteLayer.setOnClickListener {
//                val layerNames = mapState.value.layers.map { it.data.toString() }
//                if (layerNames.isEmpty()) {
//                    Toast.makeText(this@MapActivity, "No layers available to delete", Toast.LENGTH_SHORT).show()
//                    return@setOnClickListener
//                }
//
//                AlertDialog.Builder(this@MapActivity)
//                    .setTitle("Delete Layer")
//                    .setItems(layerNames.toTypedArray()) { dialog, which ->
//                        val selectedLayer = layerNames[which]
//                        AlertDialog.Builder(this@MapActivity)
//                            .setTitle("Confirm Deletion")
//                            .setMessage("Are you sure you want to delete the layer '$selectedLayer'? This cannot be undone.")
//                            .setPositiveButton("Delete") { _, _ ->
//                                deleteLayer(selectedLayer)
//                                loadAllMarkers()
//                            }
//                            .setNegativeButton("Cancel", null)
//                            .show()
//                    }
//                    .show()
//            }
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
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(point)
                    .zoom(20.0)
                    .build()
            )
        }
    }

//    private fun setupMapInteractions() {
//        mapView.gestures.addOnMapClickListener { point ->
//            if(isLineMode){
//                viewModel.addPointForLine(point.latitude(),point.longitude())
//            }else{
//                if (isButtonPressed) {
//                    if (selectedMarker == null) {
//                        viewModel.addMarker(point.latitude(), point.longitude())
//                    }
//                }
//            }
//            true
//        }
//        mapView.gestures.addOnMapLongClickListener { point ->
//            if (isButtonPressed) {
//                viewModel.deleteMarker(point.latitude(), point.longitude())
//            }
//            true
//        }
//    }

//    private fun setupLayerSelection() {
//
//
//        mBinding.btnSelectLayers.setOnClickListener {
//            lifecycleScope.launchWhenStarted {
//                viewModel.mapState.collect { state ->
//                    val layerNames = state.layers.map { it.data.toString() }
//                    if (layerNames.isNotEmpty()) {
//                        showLayerSelectionDialog(layerNames)
//                    } else {
//                        Toast.makeText(this@MapActivity, "No layers available", Toast.LENGTH_SHORT).show()
//                    }
//                    cancel()
//                }
//            }
//        }
//
//    }

    private fun showLayerSelectionDialog() {
        val selectedLayers = viewModel.mapState.value.layers.values.filter { it.isVisible }.
        map { it.id }.toMutableList()
        val layerArray = viewModel.mapState.value.layers.keys.toTypedArray()
        val checkedItems = layerArray.map { it in selectedLayers }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Select Layers to View")
            .setMultiChoiceItems(layerArray, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    if(!selectedLayers.contains(layerArray[which])){
                        selectedLayers.add(layerArray[which])
                    }
                } else {
                    selectedLayers.remove(layerArray[which])
                }
            }
            .setPositiveButton("Apply") { _, _ ->
                viewModel.updateVisibleLayers(selectedLayers)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
