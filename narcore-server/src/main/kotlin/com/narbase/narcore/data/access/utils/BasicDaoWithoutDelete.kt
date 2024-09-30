/*
 * NARBASE TECHNOLOGIES CONFIDENTIAL
 * ______________________________
 * [2017] - [2024] Narbase Technologies
 * All Rights Reserved.
 * Created by shalaga44
 * On: 23/Jun/2024.
 */


package com.narbase.narcore.data.access.utils

import com.narbase.narcore.data.models.utils.ListAndTotal
import com.narbase.narcore.data.tables.LoggedTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.util.logging.Logger

abstract class BasicDaoWithoutDelete<Table, TableKey : Comparable<TableKey>, ModelType : ModelWithId<TableKey>>
constructor(
    val table: Table
) : ModelDBConverter<ModelType> where Table : IdTable<TableKey> {
    val logger = Logger.getLogger(this::class.java.simpleName)

    context (Transaction)
    fun createAndGet(model: ModelType): ModelType {
        val id = create(model)
        return get(id)
    }

    context (Transaction)
    fun upsertAndGet(model: ModelType): ModelType {
        val id = upsert(model)
        return get(id)
    }

    abstract override fun toStatement(model: ModelType, row: UpdateBuilder<Int>)
    abstract override fun toModel(row: ResultRow): ModelType
    abstract fun filterWithSearchTerm(query: Query, searchTerm: String)


    context(Transaction)
    open fun create(item: ModelType): TableKey {
        logger.info("create() called with: item = $item")
        val id = table.insertAndGetId {
            toStatement(item, it)
        }
        logger.info("create() returned: $id")
        return id.value
    }

    context(Transaction)
    open fun createBatch(items: List<ModelType>): List<TableKey> {
        logger.info("create() called with: item = $items")
        val ids = table.batchInsert(items) {
            toStatement(it, this)
        }.map { it[table.id].value }
        logger.info("create() returned: $ids")
        return ids
    }
    context(Transaction)
    open fun createBatchWithIgnoreError(items: List<ModelType>): List<TableKey> {
        logger.info("create() called with: item = $items")
        val ids = table.batchInsert(items, ignore = true) {
            toStatement(it, this)
        }.map { it[table.id].value }
        logger.info("create() returned: $ids")
        return ids
    }

    context(Transaction)
    open fun update(item: ModelType): TableKey {
        logger.info("update() called with: item = $item")
        val updateCode = table.update({ table.id eq item.id }) {
            toStatement(item, it)
        }
        logger.info("update() returned: $updateCode")
        return item.id!!
    }

    context(Transaction)
    open fun updateAndGet(item: ModelType): ModelType {
        val id = update(item)
        return get(id)
    }

    context (Transaction)
    fun upsert(model: ModelType): TableKey {
        val id = model.id
        return if (id == null) {
            create(model)
        } else {
            update(model); id
        }
    }

    context(Transaction)
    fun get(id: TableKey) = table
        .select { table.id eq id }
        .map { toModel(it) }.first()

    context(Transaction)
    fun get(ids: List<TableKey>) = table
        .select { table.id inList ids.map { EntityID<TableKey>(it, table) } }
        .map(::toModel)

    context(Transaction)
    fun getOrNull(id: TableKey) = table
        .select { table.id eq id }
        .map { toModel(it) }.firstOrNull()


    context(Transaction)
    fun getAll() = table
        .selectAll()
        .map { toModel(it) }

    context (Transaction)
    fun getList(pageNo: Long, pageSize: Int, searchTerm: String): ListAndTotal<ModelType> {
        val query = table.selectAll()
        if (searchTerm.isNotBlank()) {
            filterWithSearchTerm(query, searchTerm)
        }

        val count = query.count()
        val list = if (table is LoggedTable) {
            query
                .orderBy(table.createdOn to SortOrder.DESC)
                .limit(pageSize, pageNo * pageSize)
                .map(::toModel)
        } else {
            query
                .orderBy(table.id to SortOrder.DESC)
                .limit(pageSize, pageNo * pageSize)
                .map(::toModel)
        }
        return ListAndTotal(list, count)
    }


}