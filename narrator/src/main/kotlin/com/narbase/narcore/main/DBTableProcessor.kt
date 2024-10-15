package com.narbase.narcore.main

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.gson.Gson
import com.narbase.narcore.main.models.*
import java.io.File

class DBTableProcessor(
    private val options: Map<String, String>,
    private val codeGenerator: CodeGenerator,
    val logger: KSPLogger
) :
    SymbolProcessor {

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (CodeGenerationSettings.didGenerate) return emptyList()
        readAndSetOptions() ?: return emptyList()

        val uuidTableKsName = resolver.getKSNameFromString(UUID_TABLE_FULL_QUALIFIER)
        val symbols = resolver.getAllFiles()
            .flatMap { it.declarations }
        val commonModulePackages = getCommonModulePackagesFromOptions(CodeGenerationSettings.commonModulePackagesPaths)
        val commonModuleSymbols = commonModulePackages.flatMap { resolver.getDeclarationsFromPackage(it) }
        val functionDeclarations = (commonModuleSymbols + symbols).filterIsInstance<KSFunctionDeclaration>()
        val classDeclarations = (commonModuleSymbols + symbols).filterIsInstance<KSClassDeclaration>()
        val tables = classDeclarations
            .filter { ksClassDeclaration ->
                ksClassDeclaration.superTypes
                    .map { it.resolve().declaration.qualifiedName }
                    .contains(uuidTableKsName) && ksClassDeclaration.isOpen().not()
            }
        if (CodeGenerationSettings.targetTableName != null) {
            val table = tables.first { it.qualifiedName?.getShortName() == CodeGenerationSettings.targetTableName }
            generateAll(table, classDeclarations, commonModuleSymbols, functionDeclarations)
        } else {
            tables.forEach { table ->
                generateAll(table, classDeclarations, commonModuleSymbols, functionDeclarations)
            }
        }
        CodeGenerationSettings.didGenerate = true
        return emptyList()
    }

    private fun generateAll(
        table: KSClassDeclaration,
        classDeclarations: List<KSClassDeclaration>,
        commonModuleSymbols: List<KSDeclaration>,
        functionDeclarations: List<KSFunctionDeclaration>
    ) {
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
        val commonImports = getTablesCommonImports(classDeclarations, dbTable.isDeletable)
        val daoFile = generateDaoFile(dbTable, logger, codeGenerator, commonImports)
        val dtoFile = generateDtoFile(daoFile.model, true, setOf(), logger, codeGenerator, commonModuleSymbols)
        generateConvertorFile(
            daoFile.model,
            dtoFile.dto,
            true,
            setOf(),
            logger,
            codeGenerator,
            classDeclarations,
            functionDeclarations
        )
        logger.newLine()
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

    private fun getTablesCommonImports(
        classDeclarations: List<KSClassDeclaration>,
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
        const val ROOT_PROJECT_PATH = "rootProjectPath"
        const val ROOT_PROJECT_NAME_OPTION = "rootProjectName"
        const val DESTINATION_DAOS_PATH_OPTION = "destinationDaosPath"
        const val DESTINATION_DTOS_PATH_OPTION = "destinationDtosPath"
        const val DESTINATION_CONVERTORS_PATH_OPTION = "destinationConvertorsPath"
        const val COMMON_MODULE_PACKAGES_OPTION = "commonModulePackagesPaths"
        const val KSP_OPTIONS_FILE_NAME = ".kspOptions.json"
        const val TASK_NAME_OPTION = "taskName"
        const val TABLE_NAME_OPTION = "tableName"
        val commonDaoImports: Set<String> = setOf(
            "import java.util.UUID",
            "import org.jetbrains.exposed.sql.statements.UpdateBuilder",
            "import org.jetbrains.exposed.sql.ResultRow",
            "import org.jetbrains.exposed.sql.Query",
        )
    }

    private fun getOption(option: String): String {
        return options[option] ?: throw IllegalArgumentException("$option option cannot be null")
    }

    private fun readAndSetOptions(): String? {
        val gson = Gson()
        val kspOptionsFile = File("${getOption(ROOT_PROJECT_PATH)}/$KSP_OPTIONS_FILE_NAME")
        val json = gson.fromJson<Map<String, String>>(kspOptionsFile.reader(), Map::class.java)
        CodeGenerationSettings.rootProjectName = json.get(ROOT_PROJECT_NAME_OPTION)
            ?: throw IllegalArgumentException("$ROOT_PROJECT_NAME_OPTION option not found in $KSP_OPTIONS_FILE_NAME file")

        CodeGenerationSettings.commonModulePackagesPaths = json.get(COMMON_MODULE_PACKAGES_OPTION)
            ?: throw IllegalArgumentException("$COMMON_MODULE_PACKAGES_OPTION option not found in $KSP_OPTIONS_FILE_NAME file")

        CodeGenerationSettings.setDestinationDaosPackage(
            json.get(DESTINATION_DAOS_PATH_OPTION)
                ?: throw IllegalArgumentException("$DESTINATION_DAOS_PATH_OPTION option not found in $KSP_OPTIONS_FILE_NAME file")
        )
        CodeGenerationSettings.setDestinationDtosPackage(
            json.get(DESTINATION_DTOS_PATH_OPTION)
                ?: throw IllegalArgumentException("$DESTINATION_DTOS_PATH_OPTION option not found in $KSP_OPTIONS_FILE_NAME file")
        )
        CodeGenerationSettings.setDestinationConvertorsPackage(
            json.get(DESTINATION_CONVERTORS_PATH_OPTION)
                ?: throw IllegalArgumentException("$DESTINATION_CONVERTORS_PATH_OPTION option not found in $KSP_OPTIONS_FILE_NAME file")
        )
//        CodeGenerationSettings.targetTableName =
//            json.get(TABLE_NAME_OPTION)
//                ?: throw IllegalArgumentException("$TABLE_NAME_OPTION option not found in $KSP_OPTIONS_FILE_NAME file")

        return json.get(TASK_NAME_OPTION)
    }
}


private fun getCommonModulePackagesFromOptions(commonModulePackagesOption: String): Set<String> {
    return commonModulePackagesOption.split(";")
        .map { it.substringAfter("commonMain/kotlin/").replace("/", ".") }
        .toSet()
}

class DBTableProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return DBTableProcessor(environment.options, environment.codeGenerator, environment.logger)
    }
}