package com.narbase.narcore.main.models

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.narbase.narcore.main.*
import java.io.OutputStream

data class DtoFile(
    val imports: Set<String>,
    val dto: DBTableDto
)

data class DBTableDto(
    val dtoName: String,
    val dtoPackage: String,
    val dtoProperties: List<DBTableProperty>
)

fun generateDtoFile(
    model: DBTableModel,
    isTableModel: Boolean,
    parentTypeParameters: Set<String>,
    logger: KSPLogger,
    codeGenerator: CodeGenerator,
    commonModuleDeclarations: List<KSDeclaration>
): DtoFile {
    val dtoName = "${model.modelName}Dto"
    val packageName = getDtoPackageName(model.modelPackage)
    if (CodeGenerationSettings.hasGeneratedDto(dtoName)) return CodeGenerationSettings.getGeneratedDto(dtoName)
        ?: throw IllegalArgumentException("DtoFile has not been generated")

    codeGenerator.createNewFile(
        dependencies = Dependencies(false),
        packageName = "dtos/$packageName",
        fileName = dtoName
    ).use { os ->
        logger.warn("Writing dto: $dtoName")
        val imports = mutableSetOf<String>()
        if (isTableModel) {
            val dtoDeclaration =
                commonModuleDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "StringUUID" }
            val import = generateImport(
                dtoDeclaration?.qualifiedName
                    ?: throw IllegalArgumentException("declaration qualified name cannot be null")
            )
            imports.add(import)
        }
        model.modelProperties.forEach {
            if (it.name != "isDeleted") {
                generateDtoImport(
                    it.qualifier,
                    it.ksType,
                    logger,
                    codeGenerator,
                    commonModuleDeclarations,
                    parentTypeParameters
                ).forEach { import ->
                    imports.add(import)
                }
            }
        }
        logger.warn("$dtoName imports:")
        logger.warn("packageName: $packageName")
        os.appendLine("package ${CodeGenerationSettings.getDestinationDtosPackage()}$packageName".replace("/", "."))
        logger.warn("package ${CodeGenerationSettings.getDestinationDtosPackage()}$packageName".replace("/", "."))
        os.appendLine()
        logger.warn("")
        imports.forEach {
            os.appendLine(it)
            logger.warn(it)
        }
        os.appendLine()
        logger.newLine()
        generateDto(model, parentTypeParameters, isTableModel, logger, os)
        os.appendLine()
        logger.newLine()
        val dto = DtoFile(
            imports = imports,
            DBTableDto(
                dtoName = dtoName,
                dtoPackage = packageName,
                listOf()
            )
        )
        CodeGenerationSettings.addGeneratedDto(dto)
        return dto
    }
}

fun generateDtoImport(
    qualifier: String,
    ksType: KSType,
    logger: KSPLogger,
    codeGenerator: CodeGenerator,
    commonModuleDeclarations: List<KSDeclaration>,
    parentTypeParameters: Set<String>
): Set<String> {
    val importsSet = mutableSetOf<String>()
    val arguments = ksType.arguments
    val typeParameters = ksType.declaration.typeParameters.map { it.name.getShortName() }.toSet()
    if (arguments.isNotEmpty()) {
        arguments.map {
            val resolvedKsType =
                it.type?.resolve() ?: throw IllegalArgumentException("property ksType name cannot be null")
            val qualifierName = resolvedKsType.declaration.qualifiedName?.getQualifier()
                ?: throw IllegalArgumentException("property qualifier cannot be null")
            val imports =
                generateDtoImport(
                    qualifierName,
                    resolvedKsType,
                    logger,
                    codeGenerator,
                    commonModuleDeclarations,
                    parentTypeParameters
                )
            imports.forEach { import ->
                importsSet.add(import)
            }
        }
    }
    when (ksType.makeNotNullable().declaration.simpleName.getShortName()) {
        "UUID", "EntityID" -> {
            val dtoDeclaration =
                commonModuleDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "StringUUID" }
            val import = generateImport(
                dtoDeclaration?.qualifiedName
                    ?: throw IllegalArgumentException("declaration qualified name cannot be null")
            )
            importsSet.add(import)
        }

        "Long" -> {
            val dtoDeclaration =
                commonModuleDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "KmmLong" }
            val import = generateImport(
                dtoDeclaration?.qualifiedName
                    ?: throw IllegalArgumentException("declaration qualified name cannot be null")
            )
            importsSet.add(import)
        }

        "List" -> {
            val dtoDeclaration = "kotlin.Array"
            val import = generateImport(dtoDeclaration)
            importsSet.add(import)
        }

        "DateTime" -> {
            val dtoDeclaration =
                commonModuleDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "DateTimeDto" }
            val import = generateImport(
                dtoDeclaration?.qualifiedName
                    ?: throw IllegalArgumentException("declaration qualified name cannot be null")
            )
            importsSet.add(import)
        }

        else -> {
            val dtoDeclaration =
                commonModuleDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "${it.qualifiedName?.getShortName()}Dto" }
            val import = if (dtoDeclaration != null) {
                generateImport(
                    dtoDeclaration.qualifiedName
                        ?: throw IllegalArgumentException("declaration qualified name cannot be null")
                )
            } else {
                if (qualifier.contains(CodeGenerationSettings.rootProjectName)) {
                    if (ksType.declaration.closestClassDeclaration()?.classKind == ClassKind.ENUM_CLASS) {
                        generateImport(qualifier, ksType)
                        val enumDtoDeclaration =
                            commonModuleDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "DtoName" }
                        val import = generateImport(
                            enumDtoDeclaration?.qualifiedName
                                ?: throw IllegalArgumentException("declaration qualified name cannot be null")
                        )
                        importsSet.add(import)
                        generateImport(qualifier, ksType)
                    } else {
                        if (ksType.toString() !in parentTypeParameters) {
                            val model = DBTableModel(
                                ksType.makeNotNullable().declaration.toString(),
                                qualifier,
                                ksType.declaration.closestClassDeclaration()?.getProperties()?.toList()
                                    ?: throw IllegalArgumentException("properties cannot be null")
                            )
                            val dto = generateDtoFile(
                                model,
                                false,
                                typeParameters,
                                logger,
                                codeGenerator,
                                commonModuleDeclarations
                            ).dto
                            generateImport("${CodeGenerationSettings.getDestinationDtosPackage()}${dto.dtoPackage}.${dto.dtoName}")
                        } else {
                            ""
                        }
                    }
                } else {
                    generateImport(qualifier, ksType)
                }
            }
            importsSet.add(import)
        }
    }

    return importsSet
}

fun generateDto(
    model: DBTableModel,
    typeParameters: Set<String>,
    isTableModel: Boolean,
    logger: KSPLogger,
    os: OutputStream
): String {
    if (typeParameters.isNotEmpty()) {
        os.appendLine("data class ${model.modelName}Dto<${typeParameters.joinToString(",")}>(")
        logger.warn("data class ${model.modelName}Dto<${typeParameters.joinToString(",")}>(")
    } else {
        os.appendLine("data class ${model.modelName}Dto(")
        logger.warn("data class ${model.modelName}Dto(")
    }
    if (isTableModel) {
        os.appendLineWithIndent("val id: StringUUID?,")
        logger.withIndent("val id: StringUUID?,")
    }
    model.modelProperties.forEach {
        if (it.name != "isDeleted") {
            if (it.name == "createdOn") {
                os.appendLineWithIndent("val ${it.name}: DateTimeDto?,")
                logger.withIndent("val ${it.name}: DateTimeDto?,")
            } else if (it.ksType.declaration.qualifiedName?.getShortName() == "EntityID") {
                os.appendLineWithIndent("val ${it.name}: ${it.ksType.getTypeArgument().replace("UUID", "StringUUID")},")
                logger.withIndent("val ${it.name}: ${it.ksType.getTypeArgument().replace("UUID", "StringUUID")},")
            } else {
                os.appendLineWithIndent("val ${it.name}: ${it.ksType.mapModelTypeToDtoType(logger, typeParameters)},")
                logger.withIndent("val ${it.name}: ${it.ksType},")
            }
        }
    }
    os.appendLine(")")
    logger.warn(")")
    return ""
}

private fun KSType.mapModelTypeToDtoType(logger: KSPLogger, parentTypeParameters: Set<String>): String {
    val propertyArguments = this.arguments
    val propertyArgumentsDtos = mutableSetOf<String>()
    if (propertyArguments.isNotEmpty()) {
        propertyArguments.forEach {
            val resolvedKsType =
                it.type?.resolve() ?: throw IllegalArgumentException("property ksType name cannot be null")
            propertyArgumentsDtos.add(resolvedKsType.mapModelTypeToDtoType(logger, parentTypeParameters))
        }
    }
    return if (this.toString().contains("List")) {
        if (propertyArgumentsDtos.isNotEmpty()) {
            this.toString().replace("List", "Array").let {
                val listArguments = it.substringAfter("<").substringBeforeLast(">")
                it.replace(listArguments, propertyArgumentsDtos.joinToString(","))
            }
        } else {
            this.toString().replace("List", "Array")
        }
    } else if (this.toString().contains("Long")) {
        this.toString().replace("Long", "KmmLong")
    } else if (this.toString().contains("DateTime")) {
        this.toString().replace("DateTime", "DateTimeDto")
    } else {
        if (this.declaration.closestClassDeclaration()?.classKind == ClassKind.ENUM_CLASS) {
            "DtoName<$this>"
        } else {
            val qualifier = this.declaration.qualifiedName?.getQualifier()
                ?: throw IllegalArgumentException("qualifier cannot be null")
            if (qualifier.contains(CodeGenerationSettings.rootProjectName)) {
                if (propertyArgumentsDtos.isNotEmpty()) {
                    this.toString().let {
                        val arguments = it.substringAfter("<").substringBeforeLast(">")
                        val name = it.replace(arguments, propertyArgumentsDtos.joinToString(","))
                        name.replaceBefore("<", "${it.substringBefore("<")}Dto")
                    }
                } else {
                    if (this.toString() in parentTypeParameters) {
                        this.toString()
                    } else {
                        "${this}Dto"
                    }
                }
            } else {
                this.toString()
            }
        }
    }
}

private fun KSClassDeclaration.getProperties(): Sequence<DBTableProperty> {
    return this.getDeclaredProperties()
        .map { property ->
            val propertyName = property.qualifiedName?.getShortName()
            val resolvedProperty = property.type.resolve()
            val resolvedPropertyTypeQualifier = resolvedProperty.declaration.qualifiedName?.getQualifier()
            DBTableProperty(
                name = propertyName ?: throw IllegalArgumentException("property name cannot be null"),
                ksType = resolvedProperty,
                qualifier = resolvedPropertyTypeQualifier
                    ?: throw IllegalArgumentException("property qualifier cannot be null")
            )
        }
}

private fun getDtoPackageName(modelPackage: String): String {
    return if (modelPackage.substringAfter("data") != modelPackage) {
        modelPackage.substringAfter("data")
    } else {
        modelPackage
    }
}
