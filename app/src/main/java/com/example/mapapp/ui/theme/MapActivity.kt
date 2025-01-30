package com.example.mapapp.ui.theme

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
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
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.mapapp.Database.GeoJsonRepository
import com.example.mapapp.Database.GeoPackageRepository
import com.example.mapapp.R
import com.example.mapapp.ViewModels.LayerType
import com.example.mapapp.ViewModels.MapLayer
import com.example.mapapp.ViewModels.MapViewModel
import com.example.mapapp.ViewModels.Results
import com.example.mapapp.databinding.ActivityMapBinding
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.extension.style.layers.properties.generated.*
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.listeners.ColorListener
import kotlinx.coroutines.launch

class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var spinner: Spinner
    private lateinit var mBinding: ActivityMapBinding
    private var selectedMarker: Feature? = null
    private val viewModel: MapViewModel by viewModels()
    var isButtonPressed = false
    val repo = GeoPackageRepository(this)

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


        repo.initializeDatabase()


        lifecycleScope.launch {
            repo.loadAllLatLng().collect { result ->
                when (result) {
                    is Results.Loading -> {
                        if (result.isLoading) {
                            Log.d("GeoPackage", "Loading coordinates from database...")
                        } else {
                            Log.d("GeoPackage", "Loading completed.")
                        }
                    }
                    is Results.Success -> {
                        val latLngList = result.data ?: emptyList()

                        if (latLngList.isNotEmpty()) {
                            for ((lat, lng) in latLngList) {
                                Log.d("GeoPackage", "Latitude: $lat, Longitude: $lng")
                                viewModel.addMarker(lat, lng)
                            }
                        } else {
                            Log.d("GeoPackage", "No coordinates found in the database.")
                        }
                    }
                    is Results.Error -> {
                        Log.e("GeoPackage", "Error loading LatLng: ${result.message}")
                    }
                }
            }
        }

//        lifecycleScope.launch{
//            viewModel.saveLatLngState.collect { state ->
//                when (state) {
//                    SaveLatLngState.IDLE -> {
//                        Log.d("MapActivity", "Waiting for save operation...")
//                    }
//                    SaveLatLngState.IN_PROGRESS -> {
//                        Log.d("MapActivity", "Saving LatLng...")
//                    }
//                    SaveLatLngState.SUCCESS -> {
//                        Log.d("MapActivity", "LatLng saved successfully!")
//                    }
//                    SaveLatLngState.FAILURE -> {
//                        Log.e("MapActivity", "Failed to save LatLng.")
//                    }
//                }
//            }
//        }

        mapView = findViewById(R.id.mapView)

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {
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
                        setupMapInteractions()

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
                        setupMapInteractions()
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
                    .inflate(R.layout.alert_box1, null)

                val colorPickerView = mDialogView.findViewById<ColorPickerView>(R.id.colorPickerView)
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
                    MapLayer(LayerType.POINT,"A new point layer has been added")
                }

                mDialogView.findViewById<Button>(R.id.buttonCancel).setOnClickListener {
                    mAlertDialog.dismiss()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        repo.closeDatabase()
        Log.d("GeoPackage", "GeoPackage database closed")
    }


    private fun setVisibility(clicked: Boolean) {
        if(!clicked){
            mBinding.point.visibility = View.VISIBLE
            mBinding.line.visibility = View.VISIBLE
            mBinding.circle.visibility = View.VISIBLE
            mBinding.polygon.visibility = View.VISIBLE
            mBinding.save.visibility = View.VISIBLE
            mBinding.load.visibility = View.VISIBLE
        } else {
            mBinding.point.visibility = View.INVISIBLE
            mBinding.line.visibility = View.INVISIBLE
            mBinding.circle.visibility = View.INVISIBLE
            mBinding.polygon.visibility = View.INVISIBLE
            mBinding.save.visibility = View.INVISIBLE
        }
    }

    private fun setAnimation(clicked: Boolean) {
        if(!clicked){
            mBinding.point.startAnimation(fromBottom)
            mBinding.line.startAnimation(fromBottom)
            mBinding.circle.startAnimation(fromBottom)
            mBinding.polygon.startAnimation(fromBottom)
            mBinding.save.startAnimation(fromBottom)
            mBinding.load.startAnimation(fromBottom)
            mBinding.add.startAnimation(rotateOpen)
        } else {
            mBinding.point.startAnimation(toBottom)
            mBinding.line.startAnimation(toBottom)
            mBinding.circle.startAnimation(toBottom)
            mBinding.polygon.startAnimation(toBottom)
            mBinding.save.startAnimation(toBottom)
            mBinding.load.startAnimation(toBottom)
            mBinding.add.startAnimation(rotateClose)
        }
    }

    private fun setClickable(clicked: Boolean) {
        if (clicked) {
            mBinding.point.isClickable = false
            mBinding.line.isClickable = false
            mBinding.circle.isClickable = false
            mBinding.polygon.isClickable = false
            mBinding.save.isClickable = false
            mBinding.load.isClickable = false
        } else {
            mBinding.point.isClickable = true
            mBinding.line.isClickable = true
            mBinding.circle.isClickable = true
            mBinding.polygon.isClickable = true
            mBinding.save.isClickable = true
            mBinding.load.isClickable = true
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

    private fun setupMapInteractions() {
        mapView.gestures.addOnMapClickListener { point ->
            if (isButtonPressed) {
                if (selectedMarker == null) {
                    viewModel.addMarker(point.latitude(), point.longitude())
                    lifecycleScope.launch {
                        repo.saveLatLng(point.latitude(), point.longitude())
                            .collect { result ->
                                when (result) {
                                    is Results.Loading -> {
                                        Log.d("GeoPackage", "Saving LatLng... Loading: ${result.isLoading}")
                                    }
                                    is Results.Success -> {
                                        Log.d("GeoPackage", "LatLng saved successfully!")
                                    }
                                    is Results.Error -> {
                                        Log.e("GeoPackage", "Error saving LatLng: ${result.message}")
                                    }
                                }
                            }
                    }
                }
            }
            true
        }
        mapView.gestures.addOnMapLongClickListener { point ->
            lifecycleScope.launch {
                Log.d("GeoPackage", "Deleting point at: ${point.latitude()}, ${point.longitude()}")

                repo.deleteLatLng(point.latitude(), point.longitude()).collect { result ->
                    when (result) {
                        is Results.Loading -> {
                            if (result.isLoading) {
                                Log.d("GeoPackage", "Deleting marker...")
                            } else {
                                Log.d("GeoPackage", "Deletion process completed.")
                            }
                        }
                        is Results.Success -> {
                            Log.d("GeoPackage", "LatLng deleted successfully!")
                            viewModel.deleteMarker(point.latitude(), point.longitude())
                        }
                        is Results.Error -> {
                            Log.e("GeoPackage", "Error deleting LatLng: ${result.message}")
                        }
                    }
                }
            }
            true
        }
    }
}
