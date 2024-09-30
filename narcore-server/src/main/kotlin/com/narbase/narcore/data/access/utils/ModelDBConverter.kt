package com.narbase.narcore.data.access.utils

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.UpdateBuilder

interface ModelDBConverter<ModelType> {
    fun toModel(row: ResultRow): ModelType
    fun toStatement(model: ModelType, row: UpdateBuilder<Int>)
}