package com.narbase.narcore.main.models

data class DBTable(
    val tableName: String,
    val tablePackage: String,
    val properties: List<DBTableProperty>,
    val isDeletable: Boolean
)