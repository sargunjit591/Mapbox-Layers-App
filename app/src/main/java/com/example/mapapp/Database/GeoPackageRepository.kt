package com.example.mapapp.Database

import android.content.Context
import android.util.Log
import com.example.mapapp.ViewModels.Results
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mil.nga.geopackage.BoundingBox
import mil.nga.geopackage.GeoPackage
import mil.nga.geopackage.GeoPackageFactory
import mil.nga.geopackage.GeoPackageManager
import mil.nga.geopackage.db.TableColumnKey
import mil.nga.geopackage.features.columns.GeometryColumns
import mil.nga.geopackage.features.user.FeatureRow
import mil.nga.geopackage.features.user.FeatureTable
import mil.nga.geopackage.features.user.FeatureTableMetadata
import mil.nga.geopackage.geom.GeoPackageGeometryData
import mil.nga.proj.ProjectionConstants
import mil.nga.sf.GeometryType
import mil.nga.sf.Point
import java.lang.Math.abs
import java.sql.SQLException

class GeoPackageRepository(private val context: Context) {
    private val gpkgName = "markers"
    private var manager : GeoPackageManager? = null
    private var geoPackage : GeoPackage? = null
    private val layers = mutableListOf<String>()

    fun initializeDatabase(tableName: String) {
        manager = getGeoPackageManager(context)
        if (geoPackage == null) {
            geoPackage = manager!!.open(gpkgName)
        }

        geoPackage?.let {
            CoroutineScope(Dispatchers.IO).launch {
                createTableIfNeeded(it, tableName).collect { result ->
                    when (result) {
                        is Results.Loading -> Log.d("GeoPackage", "Creating table... Loading: ${result.isLoading}")
                        is Results.Success -> Log.d("GeoPackage", "Feature table created successfully!")
                        is Results.Error -> Log.e("GeoPackage", "Error creating feature table: ${result.message}")
                    }
                }
            }
        } ?: run {
            throw IllegalStateException("Failed to initialize GeoPackage.")
        }
    }

    private fun getOrCreateGeoPackage(): GeoPackage {
        if (geoPackage == null) {
            manager = GeoPackageFactory.getManager(context)
            if (!manager!!.exists(gpkgName)) {
                manager!!.create(gpkgName)
            }
            geoPackage = manager!!.open(gpkgName)
        }
        return geoPackage!!
    }

    private fun getGeoPackageManager(context: Context): GeoPackageManager {
        manager = GeoPackageFactory.getManager(context)

        if (!manager!!.exists(gpkgName)) {
            manager!!.create(gpkgName)
        }
        geoPackage = manager!!.open(gpkgName)
        return manager!!
    }

    fun saveLatLng(lat: Double, lng: Double,tableName: String): Flow<Results<Boolean>> = flow {
        emit(Results.Loading())
        val gpkg = getOrCreateGeoPackage()
        try {
            Log.d("GeoPackage", "Saving LatLng ($lat, $lng) to table: $tableName")

            geoPackage?.getFeatureDao(tableName)?.let { featureDao ->
                featureDao.newRow().apply {
                    geometry = GeoPackageGeometryData().apply {
                        setGeometry(Point(lng, lat))
                    }
                    featureDao.create(this)
                }

                Log.d("GeoPackage", "LatLng ($lat, $lng) saved successfully.")
                emit(Results.Success(true))

            } ?: run {
                Log.e("GeoPackage", "Feature table '$tableName' does not exist")
                emit(Results.Error("Feature table '$tableName' does not exist"))
            }

        } catch (e: Exception) {
            Log.e("GeoPackage", "Error saving LatLng: ${e.message}", e)
            emit(Results.Error("Error saving LatLng: ${e.message}"))
        } finally {
            emit(Results.Loading(false))
        }
    }

    fun loadAllLatLng(tableName: String): Flow<Results<List<Pair<Double, Double>>>> = flow<Results<List<Pair<Double, Double>>>> {
        emit(Results.Loading())
        val gpkg = getOrCreateGeoPackage()
        val coordinates = mutableListOf<Pair<Double, Double>>()

        try {
            Log.d("GeoPackage", "Loading all LatLng points from table: $tableName")

            geoPackage?.getFeatureDao(tableName)?.let { featureDao ->
                val rows = featureDao.queryForAll()
                for (row in rows) {
                    val geometryData = row.geometry
                    if (geometryData != null) {
                        val geometry = geometryData.geometry
                        if (geometry is Point) {
                            val lat = geometry.y
                            val lng = geometry.x
                            coordinates.add(lat to lng)
                        }
                    }
                }

                Log.d("GeoPackage", "Loaded ${coordinates.size} coordinates.")
                emit(Results.Success(coordinates))

            } ?: run {
                Log.e("GeoPackage", "Feature table '$tableName' does not exist")
                emit(Results.Error("Feature table '$tableName' does not exist"))
            }

        } catch (e: Exception) {
            Log.e("GeoPackage", "Error loading LatLng: ${e.message}", e)
            emit(Results.Error("Error loading LatLng: ${e.message}"))
        } finally {
            emit(Results.Loading(false))
        }
    }.flowOn(Dispatchers.IO)

    fun deleteLatLng(lat: Double, lng: Double,tableName: String): Flow<Results<Boolean>> = flow<Results<Boolean>> {
        emit(Results.Loading())

        try {
            Log.d("GeoPackage", "Deleting point at ($lat, $lng) from table: $tableName")

            geoPackage?.getFeatureDao(tableName)?.let { featureDao ->
                val rowsToDelete = mutableListOf<FeatureRow>()
                val epsilon = 0.0001

                val cursor = featureDao.queryForAll()
                cursor.use {
                    while (cursor.moveToNext()) {
                        val geometryColumnIndex = cursor.getColumnIndex("geom")
                        if (geometryColumnIndex == -1) continue

                        val geometryBlob = cursor.getBlob(geometryColumnIndex)
                        val geometryData = GeoPackageGeometryData.create(geometryBlob)

                        geometryData?.geometry?.let { geometry ->
                            if (geometry is Point &&
                                abs(geometry.y - lat) < epsilon &&
                                abs(geometry.x - lng) < epsilon) {

                                val row = featureDao.newRow()
                                row.geometry = geometryData
                                rowsToDelete.add(row)
                            }
                        }
                    }
                }

                if (rowsToDelete.isNotEmpty()) {
                    rowsToDelete.forEach { featureDao.delete(it) }
                    Log.d("GeoPackage", "Deleted ${rowsToDelete.size} points at ($lat, $lng)")
                    emit(Results.Success(true))
                } else {
                    Log.d("GeoPackage", "No matching points found at ($lat, $lng)")
                    emit(Results.Error("No matching points found"))
                }

            } ?: run {
                Log.e("GeoPackage", "Feature table '$tableName' does not exist")
                emit(Results.Error("Feature table '$tableName' does not exist"))
            }

        } catch (e: Exception) {
            Log.e("GeoPackage", "Error deleting point: ${e.message}", e)
            emit(Results.Error("Error deleting LatLng: ${e.message}"))
        } finally {
            emit(Results.Loading(false))
        }
    }.flowOn(Dispatchers.IO)

    fun createTableIfNeeded(geoPackage: GeoPackage, tableName: String): Flow<Results<Boolean>> = flow {
        emit(Results.Loading())

        try {
            Log.d("GeoPackage", "Checking if feature table exists: $tableName")

            if (geoPackage.isFeatureTable(tableName)) {
                Log.d("GeoPackage", "Table already exists, skipping creation.")
                emit(Results.Success(true))
                return@flow
            }

            val GeometryColumnss = GeometryColumns().apply {
                val column = TableColumnKey(tableName, "geom")
                id = column
                geometryType = GeometryType.POINT
                z = 0.toByte()
                m = 0.toByte()
            }

            val srs = geoPackage.spatialReferenceSystemDao
                .getOrCreateFromEpsg(ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM.toLong())

            GeometryColumnss.srs = srs

            val created: FeatureTable? = geoPackage.createFeatureTable(
                FeatureTableMetadata.create(
                    GeometryColumnss,
                    BoundingBox.worldWGS84()
                )
            )

            if (created != null) {
                emit(Results.Success(true))
            } else {
                emit(Results.Error("Failed to create table"))
            }

        } catch (e: SQLException) {
            Log.e("GeoPackageRepository", "Error creating table", e)
            emit(Results.Error("SQL Exception: ${e.message}"))
        } finally {
            emit(Results.Loading(false))
        }
    }

    fun createPointLayer(layerName: String): Flow<Results<Boolean>> = flow {
        emit(Results.Loading())

        try {
            if (geoPackage?.isFeatureTable(layerName) == true) {
                emit(Results.Success(true))
                return@flow
            }

            val geometryColumns = GeometryColumns().apply {
                val column = TableColumnKey(layerName, "geom")
                id = column
                geometryType = GeometryType.POINT
            }

            val srs = geoPackage?.spatialReferenceSystemDao
                ?.getOrCreateFromEpsg(ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM.toLong())

            geometryColumns.srs = srs

            val created = geoPackage?.createFeatureTable(
                FeatureTableMetadata.create(geometryColumns, BoundingBox.worldWGS84())
            )

            if (created != null) {
                layers.add(layerName)
                emit(Results.Success(true))
            } else {
                emit(Results.Error("Failed to create point layer"))
            }

        } catch (e: SQLException) {
            emit(Results.Error("SQL Exception: ${e.message}"))
        } finally {
            emit(Results.Loading(false))
        }
    }

    fun loadLayers(): Flow<Results<List<String>>> = flow {
        emit(Results.Loading<List<String>>())

        try {
            manager = getGeoPackageManager(context)
            geoPackage = manager!!.open(gpkgName)
            val tables = geoPackage!!.featureTables
            layers.clear()
            layers.addAll(tables)
            Log.d("GeoPackage", "Available layers: $tables")
            emit(Results.Success(tables))
        } catch (e: Exception) {
            Log.e("GeoPackage", "Error loading layers: ${e.message}", e)
            emit(Results.Error("Error loading layers: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getPointId(lat: Double, lng: Double, tableName: String): Long {
        return withContext(Dispatchers.IO) {
            var id = -1L

            geoPackage?.getFeatureDao(tableName)?.let { featureDao ->
                val allRows = featureDao.queryForAll()

                for (row in allRows) {
                    val geometryData = row.geometry
                    if (geometryData != null) {
                        val geometry = geometryData.geometry
                        if (geometry is Point && geometry.y == lat && geometry.x == lng) {
                            id = row.id
                            break
                        }
                    }
                }
            }
            id
        }
    }

    fun closeDatabase() {
        geoPackage?.close()
        geoPackage = null
    }
}
