package com.narbase.narcore.main

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.narbase.narcore.main.models.*

class DBTableProcessor(
    private val options: Map<String, String>,
    private val codeGenerator: CodeGenerator,
    val logger: KSPLogger
) :
    SymbolProcessor {

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (CodeGenerationSettings.didGenerate) return emptyList()
        CodeGenerationSettings.rootProjectName = getOption(ROOT_PROJECT_NAME_OPTION)
        CodeGenerationSettings.compilerPluginsProjectRootPath = getOption(COMPILER_PLUGINS_ROOT_PATH_OPTION)
        CodeGenerationSettings.setDestinationDaosPackage(getOption(DESTINATION_DAOS_PATH_OPTION))
        CodeGenerationSettings.setDestinationDtosPackage(getOption(DESTINATION_DTOS_PATH_OPTION))
        CodeGenerationSettings.setDestinationConvertorsPackage(getOption(DESTINATION_CONVERTORS_PATH_OPTION))
        val uuidTableKsName = resolver.getKSNameFromString(UUID_TABLE_FULL_QUALIFIER)
        val symbols = resolver.getAllFiles()
            .flatMap { it.declarations }
        val commonModulePackages = getCommonModulePackagesFromOptions(getOption(COMMON_MODULE_PACKAGES_OPTION))
        val commonModuleSymbols = commonModulePackages.flatMap { resolver.getDeclarationsFromPackage(it) }
        val functionDeclarations = (commonModuleSymbols + symbols).filterIsInstance<KSFunctionDeclaration>()
        val classDeclarations = (commonModuleSymbols + symbols).filterIsInstance<KSClassDeclaration>()
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
        CodeGenerationSettings.didGenerate = true
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
        const val ROOT_PROJECT_NAME_OPTION = "rootProjectName"
        const val COMPILER_PLUGINS_ROOT_PATH_OPTION = "compilerPluginsRootPath"
        const val DESTINATION_DAOS_PATH_OPTION = "destinationDaosPath"
        const val DESTINATION_DTOS_PATH_OPTION = "destinationDtosPath"
        const val DESTINATION_CONVERTORS_PATH_OPTION = "destinationConvertorsPath"
        const val COMMON_MODULE_PACKAGES_OPTION = "commonModulePackages"
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