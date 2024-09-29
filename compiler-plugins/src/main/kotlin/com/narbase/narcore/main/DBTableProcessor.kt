package com.narbase.narcore.main

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

class DBTableProcessor(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {
    private val dbTables = mutableListOf<DBTable>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val uuidTableKsName = resolver.getKSNameFromString("org.jetbrains.exposed.dao.id.UUIDTable")
        val symbols = resolver.getAllFiles().flatMap { it.declarations }
        val tables = symbols
            .filterIsInstance<KSClassDeclaration>()
            .filter { ksClassDeclaration ->
                ksClassDeclaration.superTypes
                    .map { it.resolve().declaration.qualifiedName }
                    .contains(uuidTableKsName)
        }
        tables.forEach { table ->
            val tableName = table.qualifiedName?.getShortName()
            val daoName = tableName?.replace("Table", "Dao")
            logger.warn("$tableName -> $daoName")
            val properties = table.getDeclaredProperties()
            val dbTableProperties = properties
                .map { property ->
                    val propertyName = property.qualifiedName?.getShortName()
                    val resolvedProperty = property.type.resolve()
                    val resolvedPropertyType = resolvedProperty.arguments.first().type?.resolve()
                    val resolvedPropertyTypeQualifier = resolvedPropertyType?.declaration?.qualifiedName?.getQualifier()
                    logger.warn("$propertyName - $resolvedPropertyType - $resolvedPropertyTypeQualifier")
                    DBTableProperty(
                        name = propertyName ?: throw IllegalArgumentException("property name cannot be null"),
                        ksType = resolvedPropertyType ?: throw IllegalArgumentException("property ksType name cannot be null"),
                        qualifier = resolvedPropertyTypeQualifier ?: throw IllegalArgumentException("property qualifier cannot be null")
                    )
            }
            val dbTable = DBTable(
                tableName = tableName ?: throw IllegalArgumentException("table name cannot be null"),
                properties = dbTableProperties.toList(),
                isDeletable = table.superTypes.map { it.toString() }.contains("DeletableTable")
            )
            logger.newLine()
            dbTables.add(dbTable)
            dbTable.generateDaoFile(logger)
            logger.newLine()
        }
        return emptyList()
    }
}

class DBTableProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return DBTableProcessor(environment.codeGenerator, environment.logger)
    }
}