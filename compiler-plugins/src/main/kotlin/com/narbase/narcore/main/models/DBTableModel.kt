package com.narbase.narcore.main.models

import com.google.devtools.ksp.processing.KSPLogger
import com.narbase.narcore.main.*
import java.io.OutputStream

data class DBTableModel(
    val modelName: String,
    val modelPackage: String,
    val modelProperties: List<DBTableProperty>
)

fun generateModel(
    model: DBTableModel,
    logger: KSPLogger,
    os: OutputStream
): String {
    os.appendLine("data class ${model.modelName}(")
    logger.warn("data class ${model.modelName}(")
    os.appendLineWithIndent("override val id: UUID?,")
    logger.withIndent("override val id: UUID?,")
    model.modelProperties.forEach {
        if (it.name != "isDeleted") {
            if (it.name == "createdOn") {
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
    }
    os.appendLine(") : ModelWithId<UUID>")
    logger.warn(") : ModelWithId<UUID>")
    return ""
}

fun String.getModelName(): String {
    val tableNameWithoutTable = this.replace("Table", "")
    val tableNameToWords = tableNameWithoutTable.split("(?=[A-Z][^A-Z]+\$)".toRegex())
    val lastWordToSingular = CountableNounsConverter.getSingularForNoun(tableNameToWords.last())
    return tableNameWithoutTable.replace(tableNameToWords.last(), lastWordToSingular.replaceFirstChar { it.titlecaseChar() })
}

