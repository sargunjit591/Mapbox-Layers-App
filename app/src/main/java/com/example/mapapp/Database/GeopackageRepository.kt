package com.example.mapapp.Database

import android.content.Context
import android.util.Log
import mil.nga.geopackage.GeoPackage
import mil.nga.geopackage.GeoPackageFactory
import mil.nga.geopackage.GeoPackageManager
import mil.nga.geopackage.db.GeoPackageDataType
import mil.nga.geopackage.features.columns.GeometryColumns
import mil.nga.geopackage.features.user.FeatureColumn
import mil.nga.geopackage.features.user.FeatureDao
import mil.nga.geopackage.features.user.FeatureRow
import mil.nga.geopackage.features.user.FeatureTable
import mil.nga.geopackage.geom.GeoPackageGeometryData
import mil.nga.sf.GeometryType
import mil.nga.sf.Point


class GeoPackageRepository(private val context: Context) {

    private val gpkgName = "markers"
    private val tableName = "latlng_points_12"

    fun initializeDatabase() {
        val manager = getGeoPackageManager(context)
        manager.open(gpkgName).use { geoPackage ->
            createTableIfNeeded(geoPackage)
            Log.d("GeoPackage", "Database initialized successfully")
        }
    }

    private fun getGeoPackageManager(context: Context): GeoPackageManager {
        val manager = GeoPackageFactory.getManager(context)
        if (!manager.exists(gpkgName)) {
            manager.create(gpkgName)
        }
        return manager
    }

    fun saveLatLng(lat: Double, lng: Double) {
        val manager = getGeoPackageManager(context)

        try {
            manager.open(gpkgName).use { geoPackage ->
                val featureDao: FeatureDao = geoPackage.getFeatureDao(tableName) ?: return
                val newRow: FeatureRow = featureDao.newRow()

                val geometryData = GeoPackageGeometryData()
                geometryData.setGeometry(Point(lng, lat))

                newRow.geometry = geometryData

                newRow.setValue("latitude", lat)
                newRow.setValue("longitude", lng)

                featureDao.create(newRow)
                Log.d("GeoPackage", "LatLng saved successfully")
            }
        } catch (e: Exception) {
            Log.e("GeoPackage", "Error saving LatLng: ${e.message}")
        }
    }

    fun loadAllLatLng(): List<Pair<Double, Double>> {
        val manager = getGeoPackageManager(context)
        if (!manager.exists(gpkgName)) return emptyList()

        manager.open(gpkgName).use { geoPackage ->
            val featureDao = geoPackage.getFeatureDao(tableName) ?: return emptyList()
            val results = mutableListOf<Pair<Double, Double>>()

            val cursor = featureDao.queryForAll()
            cursor?.use { c ->
                val latIndex = c.getColumnIndex("latitude")
                val lngIndex = c.getColumnIndex("longitude")
                if (latIndex < 0 || lngIndex < 0) return@use

                while (c.moveToNext()) {
                    val latVal = c.getDouble(latIndex)
                    val lngVal = c.getDouble(lngIndex)
                    results.add(Pair(latVal, lngVal))
                }
            }
            return results
        }
    }

    private fun createTableIfNeeded(geoPackage: GeoPackage) {
        Log.d("GeoPackage", "Checking if feature table exists: $tableName")
        if (geoPackage.isTable(tableName)) {
            Log.d("GeoPackage", "Table already exists, skipping creation.")
            return
        }

        Log.d("GeoPackage", "Creating feature table: $tableName")
        val columns = mutableListOf<FeatureColumn>()
        columns.add(FeatureColumn.createPrimaryKeyColumn(0, "id"))
        columns.add(
            FeatureColumn.createGeometryColumn(
                1,
                "geom",
                GeometryType.POINT,
                false,
                null
            )
        )
        columns.add(FeatureColumn.createColumn(2, "latitude", GeoPackageDataType.REAL, false))
        columns.add(FeatureColumn.createColumn(3, "longitude", GeoPackageDataType.REAL, false))

        val featureTable = FeatureTable(tableName, columns)

        geoPackage.createFeatureTable(featureTable)
        Log.d("GeoPackage", "Feature table created successfully")
        populateGeometryColumns(geoPackage)
    }

    private fun populateGeometryColumns(geoPackage: GeoPackage) {
        val geometryColumnsDao = geoPackage.geometryColumnsDao

        if (!geometryColumnsDao.isTableExists) {
            geoPackage.createGeometryColumnsTable()
        }

        val geometryColumns = GeometryColumns()
        geometryColumns.tableName = tableName
        geometryColumns.columnName = "geom"
        geometryColumns.geometryType = GeometryType.POINT
        geometryColumns.z = 0
        geometryColumns.m = 0

        geometryColumnsDao.create(geometryColumns)
    }
}
