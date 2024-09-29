package com.narbase.narcore.main

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSType

data class DBTable(
    val tableName: String,
    val properties: List<DBTableProperty>,
    val isDeletable: Boolean
) {
    val modelName = tableName.replace("Table", "Model")
    val daoName = tableName.replace("Table", "Dao")
    fun generateDaoFile(logger: KSPLogger) {
        val imports = mutableSetOf<String>()
        properties.forEach {
            generateImport(it.qualifier, it.ksType, logger).forEach { import ->
                imports.add(import)
            }
        }
        imports.forEach { logger.warn(it) }
        logger.newLine()
        generateModel(modelName, properties, logger)
        logger.newLine()
        generateDao(daoName, properties, isDeletable, logger)
    }

    private fun generateModel(modelName: String, properties: List<DBTableProperty>, logger: KSPLogger): String {
        logger.warn("data class $modelName(")
        logger.withIndent("override val id: UUID?,")
        properties.forEach {
            if (it.name == "isDeleted") {
            } else if (it.name == "createdOn") {
                logger.withIndent("val ${it.name}: ${it.ksType}?,")
            } else if (it.ksType.declaration.qualifiedName?.getShortName() == "EntityID") {
                logger.withIndent("val ${it.name}: ${it.ksType.getTypeArgument()},")
            } else {
                logger.withIndent("val ${it.name}: ${it.ksType},")
            }
        }
        logger.warn(") : ModelWithId<UUID>")
        return ""
    }

    private fun generateDao(
        daoName: String,
        properties: List<DBTableProperty>,
        isDeletable: Boolean,
        logger: KSPLogger
    ): String {
        logger.warn("object $daoName:")
        if (isDeletable) {
            logger.withIndent("BasicDao<$tableName, UUID, $modelName>($tableName) {")
        } else {
            logger.withIndent("BasicDaoWithoutDelete<$tableName, UUID, $modelName>($tableName) {")
        }
        logger.withIndent("override fun toStatement(model: $modelName, row: UpdateBuilder<Int>) {")
        properties.forEach { property ->
            if (property.name != "createdOn" && property.name != "isDeleted")
                logger.withIndent("row[table.${property.name}] = model.${property.name}", 2)
        }
        logger.withIndent("}")
        logger.newLine()
        logger.withIndent("override fun toModel(row: ResultRow): $modelName {")
        logger.withIndent("return $modelName(", 2)
        logger.withIndent("id = row[table.id].value,", 3)
        properties.forEach { property ->
            if (property.name == "isDeleted") {
            } else if (property.ksType.declaration.qualifiedName?.getShortName() == "EntityID") {
                logger.withIndent("${property.name} = row[table.${property.name}].value,", 3)
            } else {
                logger.withIndent("${property.name} = row[table.${property.name}],", 3)
            }
        }
        logger.withIndent(")", 2)
        logger.withIndent("}")
        logger.newLine()
        logger.withIndent("override fun filterWithSearchTerm(query: Query, searchTerm: String) {")
        logger.withIndent("TODO(\"Not yet implemented\")", 2)
        logger.withIndent("}")
        return ""
    }
}

data class DBTableProperty(
    val name: String,
    val ksType: KSType,
    val qualifier: String
)

fun generateImport(qualifier: String, ksType: KSType, logger: KSPLogger): Set<String> {
    val importsSet = mutableSetOf<String>()
    val typeArguments = ksType.arguments
    if (typeArguments.isNotEmpty()) {
        typeArguments.map {
            val resolvedKsType =
                it.type?.resolve() ?: throw IllegalArgumentException("property ksType name cannot be null")
            val qualifierName = resolvedKsType.declaration.qualifiedName?.getQualifier()
                ?: throw IllegalArgumentException("property qualifier cannot be null")
            val imports = generateImport(qualifierName, resolvedKsType, logger)
            imports.forEach { import ->
                importsSet.add(import)
            }
        }
    }
    val import = generateImport(qualifier, ksType)
    importsSet.add(import)

    return importsSet
}

private fun generateImport(qualifier: String, ksType: KSType): String {
    return "import $qualifier.${ksType.makeNotNullable().declaration}"
}

fun KSType.getTypeArgument(): String {
    return this.toString().substringAfter("<").substringBeforeLast(">")
        .plus(this.toString().substringAfterLast(">"))
}

fun KSPLogger.withIndent(message: String, levels: Int = 1) = warn("\t".repeat(levels) + message)

fun KSPLogger.newLine() = warn("\n")