package com.narbase.narcore.main.models

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSType
import com.narbase.narcore.main.*
import java.io.OutputStream

data class DaoFile(
    val imports: Set<String>,
    val model: DBTableModel
)

fun generateDaoFile(
    table: DBTable,
    logger: KSPLogger,
    codeGenerator: CodeGenerator,
    commonImports: Set<String>
): DaoFile {
    val tableName = table.tableName
    val tablePackage = table.tablePackage
    val modelName = tableName.getModelName()
    val daoName = tableName.replace("Table", "Dao")
    val packageName = getDaoPackageName(tableName, tablePackage)

    codeGenerator.createNewFile(
        dependencies = Dependencies(false),
        packageName = "daos/$packageName",
        fileName = daoName
    ).use { os ->
        logger.warn("Writing dao: $daoName")
        val imports = mutableSetOf<String>()
        imports.addAll(commonImports)
        imports.add("import $tablePackage.$tableName")
        table.properties.forEach {
            if (it.name != "isDeleted") {
                generateDaoImport(it.qualifier, it.ksType, logger).forEach { import ->
                    imports.add(import)
                }
            }
        }
        os.appendLine("package ${CodeGenerationSettings.getDestinationDaosPackage()}$packageName".replace("/", "."))
        logger.warn("package ${CodeGenerationSettings.getDestinationDaosPackage()}$packageName".replace("/", "."))
        os.appendLine()
        logger.warn("")
        imports.forEach {
            os.appendLine(it)
            logger.warn(it)
        }
        val model = DBTableModel(modelName, packageName, table.properties)
        CodeGenerationSettings.addGeneratedModel(model)
        os.appendLine()
        logger.newLine()
        generateModel(model, logger, os)
        os.appendLine()
        logger.newLine()
        generateDao(tableName, daoName, modelName, table.properties, table.isDeletable, logger, os)
        os.appendLine()
        logger.newLine()
        return DaoFile(
            imports = imports,
            model = model
        )
    }
}

private fun generateDao(
    tableName: String,
    daoName: String,
    modelName: String,
    properties: List<DBTableProperty>,
    isDeletable: Boolean,
    logger: KSPLogger,
    os: OutputStream
): String {
    os.appendLine("object $daoName:")
    logger.warn("object $daoName:")
    if (isDeletable) {
        os.appendLineWithIndent("BasicDao<$tableName, UUID, $modelName>($tableName) {")
        logger.withIndent("BasicDao<$tableName, UUID, $modelName>($tableName) {")
    } else {
        os.appendLineWithIndent("BasicDaoWithoutDelete<$tableName, UUID, $modelName>($tableName) {")
        logger.withIndent("BasicDaoWithoutDelete<$tableName, UUID, $modelName>($tableName) {")
    }
    os.appendLineWithIndent("override fun toStatement(model: $modelName, row: UpdateBuilder<Int>) {")
    logger.withIndent("override fun toStatement(model: $modelName, row: UpdateBuilder<Int>) {")
    properties.forEach { property ->
        if (property.name != "createdOn" && property.name != "isDeleted")
            os.appendLineWithIndent("row[table.${property.name}] = model.${property.name}", 2)
        logger.withIndent("row[table.${property.name}] = model.${property.name}", 2)
    }
    os.appendLineWithIndent("}")
    logger.withIndent("}")
    os.appendLine()
    logger.newLine()
    os.appendLineWithIndent("override fun toModel(row: ResultRow): $modelName {")
    logger.withIndent("override fun toModel(row: ResultRow): $modelName {")
    os.appendLineWithIndent("return $modelName(", 2)
    logger.withIndent("return $modelName(", 2)
    os.appendLineWithIndent("id = row[table.id].value,", 3)
    logger.withIndent("id = row[table.id].value,", 3)
    properties.forEach { property ->
        if (property.name == "isDeleted") {
        } else if (property.ksType.declaration.qualifiedName?.getShortName() == "EntityID") {
            os.appendLineWithIndent("${property.name} = row[table.${property.name}].value,", 3)
            logger.withIndent("${property.name} = row[table.${property.name}].value,", 3)
        } else {
            os.appendLineWithIndent("${property.name} = row[table.${property.name}],", 3)
            logger.withIndent("${property.name} = row[table.${property.name}],", 3)
        }
    }
    os.appendLineWithIndent(")", 2)
    logger.withIndent(")", 2)
    os.appendLineWithIndent("}")
    logger.withIndent("}")
    os.appendLine()
    logger.newLine()
    os.appendLineWithIndent("override fun filterWithSearchTerm(query: Query, searchTerm: String) {")
    logger.withIndent("override fun filterWithSearchTerm(query: Query, searchTerm: String) {")
    os.appendLineWithIndent("TODO(\"Not yet implemented\")", 2)
    logger.withIndent("TODO(\"Not yet implemented\")", 2)
    os.appendLineWithIndent("}")
    logger.withIndent("}")
    os.appendLine("}")
    logger.warn("}")
    return ""
}


fun generateDaoImport(qualifier: String, ksType: KSType, logger: KSPLogger): Set<String> {
    val importsSet = mutableSetOf<String>()
    val typeArguments = ksType.arguments
    if (typeArguments.isNotEmpty()) {
        typeArguments.map {
            val resolvedKsType =
                it.type?.resolve() ?: throw IllegalArgumentException("property ksType name cannot be null")
            val qualifierName = resolvedKsType.declaration.qualifiedName?.getQualifier()
                ?: throw IllegalArgumentException("property qualifier cannot be null")
            val imports = generateDaoImport(qualifierName, resolvedKsType, logger)
            imports.forEach { import ->
                importsSet.add(import)
            }
        }
    }
    val import = generateImport(qualifier, ksType)
    importsSet.add(import)

    return importsSet
}

fun getDaoPackageName(tableName: String, tablePackage: String): String {
    val daoName = tableName.replace("Table", "").lowercase()
    tablePackage.substringAfter("tables").lowercase().replace(".", "/").let {
        if (it.endsWith(daoName)) return "$it"
        else return "$it/$daoName"
    }
}


