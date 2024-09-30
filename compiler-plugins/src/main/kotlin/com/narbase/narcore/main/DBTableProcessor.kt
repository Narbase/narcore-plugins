package com.narbase.narcore.main

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

class DBTableProcessor(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val uuidTableKsName = resolver.getKSNameFromString(UUID_TABLE_FULL_QUALIFIER)
        val symbols = resolver.getAllFiles().flatMap { it.declarations }
        val classDeclarations = symbols.filterIsInstance<KSClassDeclaration>()
        val tables = classDeclarations
            .filter { ksClassDeclaration ->
                ksClassDeclaration.superTypes
                    .map { it.resolve().declaration.qualifiedName }
                    .contains(uuidTableKsName)
            }
        tables.forEach { table ->
            val tableName = table.qualifiedName?.getShortName()
            val tablePackage = table.qualifiedName?.getQualifier()
            val dbTableProperties = table.getProperties()
            val dbTable = DBTable(
                tableName = tableName ?: throw IllegalArgumentException("table name cannot be null"),
                tablePackage = tablePackage ?: throw IllegalArgumentException("table package cannot be null"),
                properties = dbTableProperties.toList(),
                isDeletable = table.superTypes.map { it.toString() }.contains("DeletableTable")
            )
            logger.newLine()
            try {
                val commonImports = getTablesUtilsImports(classDeclarations, dbTable.isDeletable)
                dbTable.generateDaoFile(logger, codeGenerator, commonImports)
            } catch (_: Exception) {
            }
            logger.newLine()
        }
        return emptyList()
    }

    private fun KSClassDeclaration.getProperties(): Sequence<DBTableProperty> {
        return this.getDeclaredProperties()
            .map { property ->
                val propertyName = property.qualifiedName?.getShortName()
                val resolvedProperty = property.type.resolve()
                val resolvedPropertyType = resolvedProperty.arguments.first().type?.resolve()
                val resolvedPropertyTypeQualifier = resolvedPropertyType?.declaration?.qualifiedName?.getQualifier()
                logger.warn("$propertyName - $resolvedPropertyType - $resolvedPropertyTypeQualifier")
                DBTableProperty(
                    name = propertyName ?: throw IllegalArgumentException("property name cannot be null"),
                    ksType = resolvedPropertyType
                        ?: throw IllegalArgumentException("property ksType name cannot be null"),
                    qualifier = resolvedPropertyTypeQualifier
                        ?: throw IllegalArgumentException("property qualifier cannot be null")
                )
            }
    }

    private fun getTablesUtilsImports(
        classDeclarations: Sequence<KSClassDeclaration>,
        isTableDeletable: Boolean
    ): Set<String> {
        val tableUtilsClassesList = listOf(MODEL_WITH_ID_CLASS_NAME).let {
            if (isTableDeletable) it.plus(BASIC_DAO_CLASS_NAME) else it.plus(BASIC_DAO_WITHOUT_DELETE_CLASS_NAME)
        }
        val tableUtilsImports =
            classDeclarations
                .filter { declaration -> declaration.qualifiedName?.getShortName() in tableUtilsClassesList }
                .map { declaration ->
                    declaration.qualifiedName?.let { "import ${it.getQualifier()}.${it.getShortName()}" }
                        ?: throw IllegalArgumentException("class declaration couldn't be found")
                }
        return tableUtilsImports.toSet() + commonDaoImports
    }

    companion object {
        const val UUID_TABLE_FULL_QUALIFIER = "org.jetbrains.exposed.dao.id.UUIDTable"
        const val BASIC_DAO_WITHOUT_DELETE_CLASS_NAME = "BasicDaoWithoutDelete"
        const val BASIC_DAO_CLASS_NAME = "BasicDao"
        const val MODEL_WITH_ID_CLASS_NAME = "ModelWithId"
        val commonDaoImports: Set<String> = setOf(
            "import java.util.UUID",
            "import org.jetbrains.exposed.sql.statements.UpdateBuilder",
            "import org.jetbrains.exposed.sql.ResultRow",
            "import org.jetbrains.exposed.sql.Query",
        )
    }
}

class DBTableProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return DBTableProcessor(environment.codeGenerator, environment.logger)
    }
}