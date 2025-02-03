package com.example.mapapp.Database

import android.content.Context
import android.util.Log
import com.example.mapapp.ViewModels.LayerType
import com.example.mapapp.ViewModels.MapLayer
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
import mil.nga.proj.ProjectionConstants
import mil.nga.sf.GeometryType
import java.lang.Math.abs
import java.sql.SQLException
import mil.nga.sf.Point
import mil.nga.sf.LineString
import mil.nga.geopackage.geom.GeoPackageGeometryData


class GeoPackageRepository(private val context: Context) {
    private val gpkgName = "markers"
    private var manager : GeoPackageManager? = null
    private var geoPackage : GeoPackage? = null
    private val layers = mutableListOf<String>()

    fun initializeDatabase() {
        getOrCreateGeoPackage()
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

    //POINT LAYER

    fun saveLatLng(lat: Double, lng: Double,tableName: String): Flow<Results<Boolean>> = flow {
        emit(Results.Loading())
        getOrCreateGeoPackage()
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
        getOrCreateGeoPackage()
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

    fun createLayer(layer: MapLayer): Flow<Results<Boolean>> = flow {
        emit(Results.Loading())

        try {
            if (geoPackage?.isFeatureTable(layer.id) == true) {
                emit(Results.Success(false))
                return@flow
            }

            val geometryColumns = GeometryColumns().apply {
                val column = TableColumnKey(layer.id, "geom")
                id = column
                geometryType = when(layer.type){
                    LayerType.POINT ->GeometryType.POINT
                    LayerType.LINE -> GeometryType.LINESTRING
                    LayerType.CIRCLE -> GeometryType.CIRCULARSTRING
                    LayerType.POLYGON -> GeometryType.POLYGON
                }

            }

            val srs = geoPackage?.spatialReferenceSystemDao
                ?.getOrCreateFromEpsg(ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM.toLong())

            geometryColumns.srs = srs

            val created = geoPackage?.createFeatureTable(
                FeatureTableMetadata.create(geometryColumns, BoundingBox.worldWGS84())
            )

            if (created != null) {
                emit(Results.Success(true))
            } else {
                emit(Results.Error("Failed to create new layer"))
            }

        } catch (e: SQLException) {
            emit(Results.Error("SQL Exception: ${e.message}"))
        } finally {
            emit(Results.Loading(false))
        }
    }

    fun loadLayers(): Flow<Results<MutableList<MapLayer>>> = flow {
        emit(Results.Loading())
        try {
            val tables: List<String> = geoPackage!!.featureTables
            Log.d("GeoPackage", "Available layers: $tables")

            val mapLayers = mutableListOf<MapLayer>()

            for (tableName in tables) {
                try {
                    val featureDao = geoPackage!!.getFeatureDao(tableName)
                    val geometryType = featureDao.geometryType

                    val layerType = when (geometryType) {
                        GeometryType.POINT,
                        GeometryType.MULTIPOINT -> {
                            LayerType.POINT
                        }

                        GeometryType.LINESTRING,
                        GeometryType.MULTILINESTRING,
                        GeometryType.CIRCULARSTRING -> {
                            LayerType.LINE
                        }

                        GeometryType.POLYGON,
                        GeometryType.MULTIPOLYGON -> {
                            LayerType.POLYGON
                        }

                        else -> {
                            LayerType.POINT
                        }
                    }

                    val layer = MapLayer(
                        type = layerType,
                        color = 0xFF000000.toInt(),
                        id = tableName,
                        isVisible = false
                    )

                    mapLayers.add(layer)

                } catch (e: Exception) {
                    Log.e("GeoPackage", "Could not read geometry type for $tableName", e)
                }
            }

            emit(Results.Success(mapLayers))
        } catch (e: Exception) {
            Log.e("GeoPackage", "Error loading layers: ${e.message}", e)
            emit(Results.Error("Error loading layers: ${e.message}"))
        } finally {
            emit(Results.Loading(isLoading = false))
        }
    }.flowOn(Dispatchers.IO)


    fun deleteLayer(layerName: String): Flow<Results<Boolean>> = flow {
        emit(Results.Loading())
        try {
            val gpkg = getOrCreateGeoPackage()
            if (gpkg.isFeatureTable(layerName)) {
                gpkg.deleteTable(layerName)
                layers.remove(layerName)
                emit(Results.Success(true))
            } else {
                emit(Results.Error("Layer '$layerName' does not exist"))
            }
        } catch (e: Exception) {
            Log.e("GeoPackageRepository", "Error deleting layer: ${e.message}", e)
            emit(Results.Error("Error deleting layer: ${e.message}"))
        } finally {
            emit(Results.Loading(false))
        }
    }.flowOn(Dispatchers.IO)

    //LINE LAYER

    fun saveLineSegment(
        start: Pair<Double, Double>,
        end: Pair<Double, Double>,
        tableName: String
    ): Flow<Results<Boolean>> = flow {
        emit(Results.Loading())
        getOrCreateGeoPackage()
        try {
            Log.d("GeoPackage", "Saving line segment from $start to $end in table: $tableName")

            val featureDao = geoPackage?.getFeatureDao(tableName)
            if (featureDao == null) {
                Log.e("GeoPackage", "Feature table '$tableName' does not exist")
                emit(Results.Error("Feature table '$tableName' does not exist"))
                return@flow
            }

            val newRow = featureDao.newRow()

            newRow.geometry = GeoPackageGeometryData().apply {
                val startPoint = Point(start.second, start.first)
                val endPoint = Point(end.second, end.first)
                val lineString = LineString(listOf(startPoint, endPoint))
                setGeometry(lineString)
            }

            featureDao.create(newRow)

            Log.d("GeoPackage", "Line segment saved successfully.")
            emit(Results.Success(true))
        } catch (e: Exception) {
            Log.e("GeoPackage", "Error saving line segment: ${e.message}", e)
            emit(Results.Error("Error saving line segment: ${e.message}"))
        } finally {
            emit(Results.Loading(false))
        }
    }.flowOn(Dispatchers.IO)

    fun loadAllLineSegments(tableName: String): Flow<Results<out List<Pair<Pair<Double, Double>, Pair<Double, Double>>>>> = flow {
        emit(Results.Loading())
        val segments = mutableListOf<Pair<Pair<Double, Double>, Pair<Double, Double>>>()
        try {
            geoPackage?.getFeatureDao(tableName)?.let { featureDao ->
                val rows = featureDao.queryForAll()
                for (row in rows) {
                    val geometryData = row.geometry
                    if (geometryData != null) {
                        val geometry = geometryData.geometry
                        if (geometry is LineString) {
                            val points: List<Point> = geometry.getPoints()
                            if (points.size >= 2) {
                                val startPoint = points[0]
                                val endPoint = points[1]
                                segments.add((startPoint.y to startPoint.x) to (endPoint.y to endPoint.x))
                            }
                        }
                    }
                }
                Log.d("GeoPackage", "Loaded ${segments.size} line segments from table: $tableName")
                emit(Results.Success(segments))
            } ?: run {
                Log.e("GeoPackage", "Feature table '$tableName' does not exist")
                emit(Results.Error<List<Pair<Pair<Double, Double>, Pair<Double, Double>>>>("Feature table '$tableName' does not exist"))
            }
        } catch (e: Exception) {
            Log.e("GeoPackage", "Error loading line segments: ${e.message}", e)
            emit(Results.Error("Error loading line segments: ${e.message}"))
        } finally {
            emit(Results.Loading(false))
        }
    }.flowOn(Dispatchers.IO)

    fun closeDatabase() {
        geoPackage?.close()
        geoPackage = null
    }
}
