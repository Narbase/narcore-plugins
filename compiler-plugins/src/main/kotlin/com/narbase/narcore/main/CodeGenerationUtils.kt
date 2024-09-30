package com.narbase.narcore.main

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSType
import java.io.OutputStream

data class DBTable(
    val tableName: String,
    val tablePackage: String,
    val properties: List<DBTableProperty>,
    val isDeletable: Boolean
) {
    val modelName = tableName.getModelName()
    val daoName = tableName.replace("Table", "Dao")
    fun generateDaoFile(logger: KSPLogger, codeGenerator: CodeGenerator, commonImports: Set<String>) {
        codeGenerator.createNewFile(
            Dependencies(false),
            getDaoPackageName(tableName, tablePackage),
            fileName = daoName
        ).use { os ->
            logger.warn("Writing dao: $daoName")
            val imports = mutableSetOf<String>()
            imports.addAll(commonImports)
            imports.add("import $tablePackage.$tableName")
            properties.forEach {
                if (it.name != "isDeleted") {
                    generateImport(it.qualifier, it.ksType, logger).forEach { import ->
                        imports.add(import)
                    }
                }
            }
            imports.forEach {
                os.appendLine(it)
                logger.warn(it)
            }
            os.appendLine()
            logger.newLine()
            generateModel(modelName, properties, logger, os)
            os.appendLine()
            logger.newLine()
            generateDao(daoName, properties, isDeletable, logger, os)
        }
    }

    private fun generateModel(
        modelName: String,
        properties: List<DBTableProperty>,
        logger: KSPLogger,
        os: OutputStream
    ): String {
        os.appendLine("data class $modelName(")
        logger.warn("data class $modelName(")
        os.appendLineWithIndent("override val id: UUID?,")
        logger.withIndent("override val id: UUID?,")
        properties.forEach {
            if (it.name == "isDeleted") {
            } else if (it.name == "createdOn") {
                os.appendLineWithIndent("val ${it.name}: ${it.ksType}?,")
                logger.withIndent("val ${it.name}: ${it.ksType}?,")
            } else if (it.ksType.declaration.qualifiedName?.getShortName() == "EntityID") {
                os.appendLineWithIndent("val ${it.name}: ${it.ksType.getTypeArgument()},")
                logger.withIndent("val ${it.name}: ${it.ksType.getTypeArgument()},")
            } else {
                os.appendLineWithIndent("val ${it.name}: ${it.ksType},")
                logger.withIndent("val ${it.name}: ${it.ksType},")
            }
        }
        os.appendLine(") : ModelWithId<UUID>")
        logger.warn(") : ModelWithId<UUID>")
        return ""
    }

    private fun generateDao(
        daoName: String,
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
    val str = "import $qualifier.${ksType.makeNotNullable().declaration}"
    return str
}

fun KSType.getTypeArgument(): String {
    return this.toString().substringAfter("<").substringBeforeLast(">")
        .plus(this.toString().substringAfterLast(">"))
}

fun KSPLogger.withIndent(message: String, levels: Int = 1) = warn("\t".repeat(levels) + message)

fun KSPLogger.newLine() = warn("\n")

private fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}

private fun OutputStream.appendLine(str: String = "") {
    appendText(str + System.lineSeparator())
}

private fun OutputStream.appendLineWithIndent(str: String = "", levels: Int = 1) {
    appendText("\t".repeat(levels) + str + System.lineSeparator())
}

private fun String.getModelName(): String {
    val tableNameWithoutTable = this.replace("Table", "")
    val nameToWords = tableNameWithoutTable.split("(?=[A-Z][^A-Z]+\$)".toRegex())
    val lastWordToSingular = CountableNounsConverter.getSingularForNoun(nameToWords.last())
    return tableNameWithoutTable.replace(nameToWords.last(), lastWordToSingular.replaceFirstChar { it.titlecaseChar() })
}

private fun getDaoPackageName(tableName: String, tablePackage: String): String {
    val daoName = tableName.replace("Table", "").lowercase()
    tablePackage.substringAfter("tables").lowercase().replace(".", "/").let {
        if (it.endsWith(daoName)) return "daos/$it"
        else return "daos/$it/$daoName"
    }
}