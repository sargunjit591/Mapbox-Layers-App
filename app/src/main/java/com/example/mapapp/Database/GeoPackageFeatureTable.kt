package com.example.mapapp.Database

import mil.nga.sf.GeometryType

open class GeoPackageFeatureTable(
    val database: String,
    val name: String,
    val geometryType: GeometryType,
    val count: Int
) {}