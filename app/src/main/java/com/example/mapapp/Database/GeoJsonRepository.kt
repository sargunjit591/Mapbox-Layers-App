package com.example.mapapp.Database

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GeoJsonRepository(private val context: Context) {

    private val fileName = "markers.geojson"
    private val tag = "GeoJsonRepository"

    fun initializeDatabase() {
        val file = getGeoJsonFile()
        if (!file.exists()) {
            val emptyFeatureCollection = JSONObject().apply {
                put("type", "FeatureCollection")
                put("features", JSONArray())
            }
            file.writeText(emptyFeatureCollection.toString())
            Log.d(tag, "GeoJSON file created: $fileName")
        } else {
            Log.d(tag, "GeoJSON file already exists.")
        }
    }

    fun saveLatLng(lat: Double, lng: Double) {
        try {
            val file = getGeoJsonFile()
            if (!file.exists()) {
                initializeDatabase()
            }

            val geoJsonString = file.readText()
            val geoJsonObject = JSONObject(geoJsonString)

            val featuresArray = geoJsonObject.optJSONArray("features") ?: JSONArray()

            val newFeature = JSONObject().apply {
                put("type", "Feature")

                val properties = JSONObject().apply {
                    put("latitude", lat)
                    put("longitude", lng)
                }
                put("properties", properties)

                val geometry = JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray(listOf(lng, lat)))
                }
                put("geometry", geometry)
            }

            featuresArray.put(newFeature)

            geoJsonObject.put("features", featuresArray)

            file.writeText(geoJsonObject.toString())

            Log.d(tag, "LatLng saved successfully: ($lat, $lng)")

        } catch (e: Exception) {
            Log.e(tag, "Error saving LatLng: ${e.message}")
        }
    }

    fun loadAllLatLng(): List<Pair<Double, Double>> {
        val file = getGeoJsonFile()
        if (!file.exists()) {
            Log.w(tag, "GeoJSON file not found. Returning empty list.")
            return emptyList()
        }

        return try {
            val geoJsonString = file.readText()
            val geoJsonObject = JSONObject(geoJsonString)
            val featuresArray = geoJsonObject.optJSONArray("features") ?: JSONArray()

            val results = mutableListOf<Pair<Double, Double>>()

            for (i in 0 until featuresArray.length()) {
                val feature = featuresArray.getJSONObject(i)
                val geometry = feature.optJSONObject("geometry")
                if (geometry != null && geometry.optString("type") == "Point") {
                    val coords = geometry.optJSONArray("coordinates")
                    if (coords != null && coords.length() == 2) {
                        val longitude = coords.getDouble(0)
                        val latitude = coords.getDouble(1)
                        results.add(Pair(latitude, longitude))
                    }
                }
            }

            results
        } catch (e: Exception) {
            Log.e(tag, "Error loading LatLng: ${e.message}")
            emptyList()
        }
    }

    private fun getGeoJsonFile(): File {
        return File(context.filesDir, fileName)
    }
}
