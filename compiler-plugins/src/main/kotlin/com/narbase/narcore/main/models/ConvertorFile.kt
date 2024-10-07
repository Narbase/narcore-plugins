package com.narbase.narcore.main.models

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.narbase.narcore.main.*
import java.io.OutputStream

data class ConvertorFile(
    val convertorName: String,
    val convertorPackage: String
)

fun generateConvertorFile(
    model: DBTableModel,
    dto: DBTableDto,
    isTableModel: Boolean,
    parentTypeParameters: Set<String>,
    logger: KSPLogger,
    codeGenerator: CodeGenerator,
    classDeclarations: List<KSClassDeclaration>,
    functionDeclarations: List<KSFunctionDeclaration>
): ConvertorFile {
    val conversionFileName = model.modelName
    val packageName = getConvertorFilePackageName(model.modelPackage)
    if (CodeGenerationSettings.hasGeneratedConvertor(conversionFileName)) return CodeGenerationSettings.getGeneratedConvertor(
        conversionFileName
    )
        ?: throw IllegalArgumentException("ConvertorFile has not been generated")

    codeGenerator.createNewFile(
        dependencies = Dependencies(false),
        packageName = "conversions/$packageName",
        fileName = conversionFileName
    ).use { os ->
        logger.warn("Writing conversion: $conversionFileName")
        val imports = mutableSetOf<String>()
        imports.add(getModelImport(model.modelPackage, model.modelName))
        imports.add(getDtoImport(dto.dtoPackage, dto.dtoName))
        if (isTableModel) {
            val toDtoDeclaration =
                functionDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "toStringUUID" }
            val toDtoImport = generateImport(
                toDtoDeclaration?.qualifiedName
                    ?: throw IllegalArgumentException("declaration qualified name cannot be null")
            )
            val toModelDeclaration =
                functionDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "toUUID" }
            val toModelImport = generateImport(
                toModelDeclaration?.qualifiedName
                    ?: throw IllegalArgumentException("declaration qualified name cannot be null")
            )
            imports.add(toDtoImport)
            imports.add(toModelImport)
        }

        model.modelProperties.forEach {
            if (it.name != "isDeleted") {
                generateConvertorImport(
                    it,
                    logger,
                    codeGenerator,
                    classDeclarations,
                    functionDeclarations,
                    parentTypeParameters
                ).forEach { import ->
                    imports.add(import)
                }
            }
        }

        os.appendLine("package ${CodeGenerationSettings.getDestinationConvertorsPackage()}$packageName".replace("/", "."))
        logger.warn("package ${CodeGenerationSettings.getDestinationConvertorsPackage()}$packageName".replace("/", "."))
        os.appendLine()
        logger.warn("")
        imports.forEach {
            os.appendLine(it)
            logger.warn(it)
        }
        os.appendLine()
        logger.newLine()
        generateToDtoFunction(model, dto, isTableModel, parentTypeParameters, logger, os)
        os.appendLine()
        logger.newLine()
        generateToModelFunction(model, dto, isTableModel, parentTypeParameters, logger, os)
    }
    val convertorFile = ConvertorFile(
        conversionFileName,
        packageName
    )
    CodeGenerationSettings.addGeneratedConvertor(convertorFile)
    return convertorFile
}

fun generateConvertorImport(
    property: DBTableProperty,
    logger: KSPLogger,
    codeGenerator: CodeGenerator,
    classDeclarations: List<KSClassDeclaration>,
    functionDeclarations: List<KSFunctionDeclaration>,
    parentTypeParameters: Set<String>
): Set<String> {
    val importsSet = mutableSetOf<String>()
    val typeParameters = property.ksType.declaration.typeParameters.map { it.name.getShortName() }.toSet()
    when (property.ksType.declaration.qualifiedName?.getShortName()) {
        "UUID", "EntityID" -> {
            val toDtoDeclaration =
                functionDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "toStringUUID" }
            val toDtoImport = generateImport(
                toDtoDeclaration?.qualifiedName
                    ?: throw IllegalArgumentException("declaration qualified name cannot be null")
            )
            val toModelDeclaration =
                functionDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "toUUID" }
            val toModelImport = generateImport(
                toModelDeclaration?.qualifiedName
                    ?: throw IllegalArgumentException("declaration qualified name cannot be null")
            )
            importsSet.add(toDtoImport)
            importsSet.add(toModelImport)
        }

        "List" -> {
            val toTypedArrayImport = generateImport("kotlin.collections.toTypedArray")
            val arguments = property.ksType.arguments
            val argumentTypeParameters =
                property.ksType.declaration.typeParameters.map { it.name.getShortName() }.toSet()
            arguments.forEach {
                val resolvedType = it.type?.resolve() ?: throw IllegalArgumentException("argument cannot be resolved")
                val argumentProperty = DBTableProperty(
                    resolvedType.declaration.qualifiedName?.getShortName()
                        ?: throw IllegalArgumentException("property name cannot be null"),
                    resolvedType,
                    resolvedType.declaration.qualifiedName?.getQualifier()
                        ?: throw IllegalArgumentException("property qualifier cannot be null")
                )
                val imports = generateConvertorImport(
                    argumentProperty,
                    logger,
                    codeGenerator,
                    classDeclarations,
                    functionDeclarations,
                    argumentTypeParameters
                )
                importsSet.addAll(imports)
            }
            importsSet.add(toTypedArrayImport)
        }

        "Long" -> {
            val toKmmLongDeclaration =
                functionDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "kmmLongOf"  }
            val toKmmLongImport = generateImport(
                toKmmLongDeclaration?.qualifiedName
                    ?: throw IllegalArgumentException("declaration qualified name cannot be null")
            )
            importsSet.add(toKmmLongImport)
        }

        "DateTime" -> {
            val toDtoDeclaration =
                functionDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "toDto" && it.extensionReceiver?.resolve()?.declaration?.qualifiedName?.getShortName() == property.ksType.declaration.qualifiedName?.getShortName() }
            val toDtoImport = generateImport(
                toDtoDeclaration?.qualifiedName
                    ?: throw IllegalArgumentException("declaration qualified name cannot be null")
            )
            val toModelDeclaration =
                functionDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "toDateTime" && it.extensionReceiver?.resolve()?.declaration?.qualifiedName?.getShortName() == "${property.ksType.declaration.qualifiedName?.getShortName()}Dto" }
            val toModelImport = generateImport(
                toModelDeclaration?.qualifiedName
                    ?: throw IllegalArgumentException("declaration qualified name cannot be null")
            )
            importsSet.add(toDtoImport)
            importsSet.add(toModelImport)
        }

        else -> {
            if (property.qualifier.contains(CodeGenerationSettings.rootProjectName)) {
                if (property.ksType.declaration.closestClassDeclaration()?.classKind == ClassKind.ENUM_CLASS) {
                    val toDtoDeclaration =
                        functionDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "dto" }
                    val toDtoImport = generateImport(
                        toDtoDeclaration?.qualifiedName
                            ?: throw IllegalArgumentException("declaration qualified name cannot be null")
                    )
                    val toModelDeclaration =
                        functionDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "enum" }
                    val toModelImport = generateImport(
                        toModelDeclaration?.qualifiedName
                            ?: throw IllegalArgumentException("declaration qualified name cannot be null")
                    )
                    importsSet.add(toDtoImport)
                    importsSet.add(toModelImport)
                } else {
                    val toDtoDeclaration =
                        functionDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "toDto" && it.extensionReceiver?.resolve()?.declaration?.qualifiedName?.getShortName() == property.ksType.declaration.qualifiedName?.getShortName() }
                    val toDtoImport = if (toDtoDeclaration != null) {
                        generateImport(
                            toDtoDeclaration.qualifiedName
                                ?: throw IllegalArgumentException("declaration qualified name cannot be null")
                        )
                    } else {
                        if (property.ksType.toString() !in parentTypeParameters) {
                            val modelName = property.ksType.declaration.qualifiedName?.getShortName()
                                ?: throw IllegalArgumentException("model name cannot be null")
                            val model =
                                classDeclarations.firstOrNull { it.qualifiedName?.getShortName() == modelName && it.classKind == ClassKind.CLASS }
                                    ?.let {
                                        DBTableModel(
                                            modelName,
                                            it.qualifiedName?.getQualifier()
                                                ?: throw IllegalArgumentException("model package cannot be null"),
                                            it.getProperties().toList()
                                        )
                                    } ?: CodeGenerationSettings.getGeneratedModel(modelName)
                                ?: throw IllegalArgumentException("model cannot be null")

                            val dtoName = "${modelName}Dto"
                            val dto =
                                classDeclarations.firstOrNull { it.qualifiedName?.getShortName() == dtoName && it.classKind == ClassKind.CLASS }
                                    ?.let {
                                        DBTableDto(
                                            dtoName,
                                            it.qualifiedName?.getQualifier()
                                                ?: throw IllegalArgumentException("dto package cannot be null"),
                                            it.getProperties().toList()
                                        )
                                    } ?: CodeGenerationSettings.getGeneratedDto(dtoName)?.dto
                                ?: throw IllegalArgumentException("dto cannot be null")
                            val convertor = generateConvertorFile(
                                model,
                                dto,
                                false,
                                typeParameters,
                                logger,
                                codeGenerator,
                                classDeclarations,
                                functionDeclarations
                            )
                            generateImport("${CodeGenerationSettings.getDestinationConvertorsPackage()}${convertor.convertorPackage}.toDto")
                        } else {
                            ""
                        }
                    }

                    val toModelDeclaration =
                        functionDeclarations.firstOrNull { it.qualifiedName?.getShortName() == "toModel" && it.extensionReceiver?.resolve()?.declaration?.qualifiedName?.getShortName() == "${property.ksType.declaration.qualifiedName?.getShortName()}Dto" }
                    val toModelImport = if (toModelDeclaration != null) {
                        generateImport(
                            toModelDeclaration.qualifiedName
                                ?: throw IllegalArgumentException("declaration qualified name cannot be null")
                        )
                    } else {
                        if (property.ksType.toString() !in parentTypeParameters) {
                            val modelName = property.ksType.declaration.qualifiedName?.getShortName()
                                ?: throw IllegalArgumentException("model name cannot be null")
                            val model =
                                classDeclarations.firstOrNull { it.qualifiedName?.getShortName() == modelName && it.classKind == ClassKind.CLASS }
                                    ?.let {
                                        DBTableModel(
                                            modelName,
                                            it.qualifiedName?.getQualifier()
                                                ?: throw IllegalArgumentException("model package cannot be null"),
                                            it.getProperties().toList()
                                        )
                                    } ?: CodeGenerationSettings.getGeneratedModel(modelName)
                                ?: throw IllegalArgumentException("model cannot be null")

                            val dtoName = "${modelName}Dto"
                            val dto =
                                classDeclarations.firstOrNull { it.qualifiedName?.getShortName() == dtoName && it.classKind == ClassKind.CLASS }
                                    ?.let {
                                        DBTableDto(
                                            dtoName,
                                            it.qualifiedName?.getQualifier()
                                                ?: throw IllegalArgumentException("dto package cannot be null"),
                                            it.getProperties().toList()
                                        )
                                    } ?: CodeGenerationSettings.getGeneratedDto(dtoName)?.dto
                                ?: throw IllegalArgumentException("dto cannot be null")
                            val convertor = generateConvertorFile(
                                model,
                                dto,
                                false,
                                typeParameters,
                                logger,
                                codeGenerator,
                                classDeclarations,
                                functionDeclarations
                            )
                            generateImport("${CodeGenerationSettings.getDestinationConvertorsPackage()}${convertor.convertorPackage}.toModel")
                        } else {
                            ""
                        }
                    }
                    importsSet.add(toDtoImport)
                    importsSet.add(toModelImport)
                }
            }
        }
    }
    return importsSet
}

fun generateToDtoFunction(
    model: DBTableModel,
    dto: DBTableDto,
    isTableModel: Boolean,
    parentTypeParameters: Set<String>,
    logger: KSPLogger,
    os: OutputStream
) {
    if (parentTypeParameters.isNotEmpty()) {
        os.appendLine(
            "fun <${parentTypeParameters.joinToString(",")}>${model.modelName}<${
                parentTypeParameters.joinToString(
                    ","
                )
            }>.toDto(): ${dto.dtoName}<${parentTypeParameters.joinToString(",")}> {"
        )
        logger.warn("fun ${model.modelName}.toDto(): ${dto.dtoName} {")
    } else {
        os.appendLine("fun ${model.modelName}.toDto(): ${dto.dtoName} {")
        logger.warn("fun ${model.modelName}.toDto(): ${dto.dtoName} {")
    }
    os.appendLineWithIndent("return ${dto.dtoName}(")
    logger.withIndent("return ${dto.dtoName}(")
    if (isTableModel) {
        os.appendLineWithIndent("id = id?.toStringUUID(),", 2)
        logger.withIndent("id = id?.toStringUUID(),", 2)
    }
    model.modelProperties.forEach {
        logger.warn("new prop: ${it.name}")
        if (it.name != "isDeleted") {
            val propertyType = it.ksType.declaration.qualifiedName?.getShortName()
            val nullability = if (it.ksType.isMarkedNullable) "?" else ""
            if (it.name == "createdOn") {
                os.appendLineWithIndent("${it.name} = ${it.name}?.toDto(),", 2)
                logger.withIndent("${it.name} = ${it.name}?.toDto(),", 2)
            } else if (propertyType == "EntityID") {
                os.appendLineWithIndent("${it.name} = ${it.name}$nullability.toStringUUID(),", 2)
                logger.withIndent("${it.name} = ${it.name}$nullability.toStringUUID(),", 2)
            } else if (propertyType == "Long") {
                os.appendLineWithIndent("${it.name} = ${it.name}$nullability.let{ kmmLongOf(it) },", 2)
                logger.withIndent("${it.name} = ${it.name}$nullability.let{ kmmLongOf(it) },", 2)
            } else if (propertyType == "DateTime") {
                os.appendLineWithIndent("${it.name} = ${it.name}$nullability.toDto(),", 2)
                logger.withIndent("${it.name} = ${it.name}$nullability.toDto(),", 2)
            } else if (propertyType == "List") {
                val mappings = it.ksType.mapModelTypeToDtoType(logger, parentTypeParameters)
                os.appendLineWithIndent("${it.name} = ${it.name}$nullability$mappings.toTypedArray(),", 2)
                logger.withIndent("${it.name} = ${it.name}$nullability$mappings.toTypedArray(),", 2)
            } else {
                if (it.ksType.declaration.closestClassDeclaration()?.classKind == ClassKind.ENUM_CLASS) {
                    os.appendLineWithIndent("${it.name} = ${it.name}$nullability.dto(),", 2)
                    logger.withIndent("${it.name} = ${it.name}$nullability.dto(),", 2)
                } else {
                    val qualifier = it.ksType.declaration.qualifiedName?.getQualifier()
                        ?: throw IllegalArgumentException("qualifier cannot be null")
                    if (qualifier.contains(CodeGenerationSettings.rootProjectName)) {
                        if (propertyType in parentTypeParameters) {
                            os.appendLineWithIndent("${it.name} = ${it.name}$nullability,", 2)
                            logger.withIndent("${it.name} = ${it.name}$nullability,", 2)
                        } else {
                            os.appendLineWithIndent("${it.name} = ${it.name}$nullability.toDto(),", 2)
                            logger.withIndent("${it.name} = ${it.name}$nullability.toDto(),", 2)
                        }
                    } else {
                        os.appendLineWithIndent("${it.name} = ${it.name}$nullability,", 2)
                        logger.withIndent("${it.name} = ${it.name}$nullability,", 2)
                    }
                }
            }
        }
    }
    os.appendLineWithIndent(")")
    logger.withIndent(")")
    os.appendLine("}")
    logger.warn("}")
}

fun generateToModelFunction(
    model: DBTableModel,
    dto: DBTableDto,
    isTableModel: Boolean,
    parentTypeParameters: Set<String>,
    logger: KSPLogger,
    os: OutputStream
) {
    if (parentTypeParameters.isNotEmpty()) {
        os.appendLine(
            "fun <${parentTypeParameters.joinToString(",")}>${dto.dtoName}<${
                parentTypeParameters.joinToString(
                    ","
                )
            }>.toModel(): ${model.modelName}<${parentTypeParameters.joinToString(",")}> {"
        )
        logger.warn("fun ${dto.dtoName}.toModel(): ${model.modelName} {")
    } else {
        os.appendLine("fun ${dto.dtoName}.toModel(): ${model.modelName} {")
        logger.warn("fun ${dto.dtoName}.toModel(): ${model.modelName} {")
    }
    os.appendLineWithIndent("return ${model.modelName}(")
    logger.withIndent("return ${model.modelName}(")
    if (isTableModel) {
        os.appendLineWithIndent("id = id?.toUUID(),", 2)
        logger.withIndent("id = id?.toUUID(),", 2)
    }
    model.modelProperties.forEach {
        logger.warn("new prop: ${it.name}")
        if (it.name != "isDeleted") {
            val propertyType = it.ksType.declaration.qualifiedName?.getShortName()
            val nullability = if (it.ksType.isMarkedNullable) "?" else ""
            if (it.name == "createdOn") {
                os.appendLineWithIndent("${it.name} = ${it.name}?.toDateTime(),", 2)
                logger.withIndent("${it.name} = ${it.name}?.toDateTime(),", 2)
            } else if (propertyType == "EntityID") {
                os.appendLineWithIndent("${it.name} = ${it.name}$nullability.toUUID(),", 2)
                logger.withIndent("${it.name} = ${it.name}$nullability.toUUID(),", 2)
            } else if (propertyType == "DateTime") {
                os.appendLineWithIndent("${it.name} = ${it.name}$nullability.toDateTime(),", 2)
                logger.withIndent("${it.name} = ${it.name}$nullability.toDateTime(),", 2)
            } else if (propertyType == "List") {
                val mappings = it.ksType.mapDtoTypeToModelType(logger, parentTypeParameters)
                os.appendLineWithIndent("${it.name} = ${it.name}$nullability$mappings.toList(),", 2)
                logger.withIndent("${it.name} = ${it.name}$nullability$mappings.toList(),", 2)
            } else {
                if (it.ksType.declaration.closestClassDeclaration()?.classKind == ClassKind.ENUM_CLASS) {
                    os.appendLineWithIndent("${it.name} = ${it.name}$nullability.enum(),", 2)
                    logger.withIndent("${it.name} = ${it.name}$nullability.enum(),", 2)
                } else {
                    val qualifier = it.ksType.declaration.qualifiedName?.getQualifier()
                        ?: throw IllegalArgumentException("qualifier cannot be null")
                    if (qualifier.contains(CodeGenerationSettings.rootProjectName)) {
                        if (propertyType in parentTypeParameters) {
                            os.appendLineWithIndent("${it.name} = ${it.name}$nullability,", 2)
                            logger.withIndent("${it.name} = ${it.name}$nullability,", 2)
                        } else {
                            os.appendLineWithIndent("${it.name} = ${it.name}$nullability.toModel(),", 2)
                            logger.withIndent("${it.name} = ${it.name}$nullability.toModel(),", 2)
                        }
                    } else {
                        os.appendLineWithIndent("${it.name} = ${it.name}$nullability,", 2)
                        logger.withIndent("${it.name} = ${it.name}$nullability,", 2)
                    }
                }
            }
        }
    }
    os.appendLineWithIndent(")")
    logger.withIndent(")")
    os.appendLine("}")
    logger.warn("}")
}


private fun getModelImport(modelPackage: String, modelName: String): String {
    return if (modelPackage.contains(CodeGenerationSettings.rootProjectName)) {
        generateImport("$modelPackage.$modelName")
    } else {
        generateImport(
            "${CodeGenerationSettings.getDestinationDaosPackage()}$modelPackage.$modelName".replace(
                "/",
                "."
            )
        )
    }
}

private fun getDtoImport(dtoPackage: String, dtoName: String): String {
    return if (dtoPackage.contains(CodeGenerationSettings.rootProjectName)) {
        generateImport("$dtoPackage.$dtoName")
    } else {
        generateImport("${CodeGenerationSettings.getDestinationDtosPackage()}$dtoPackage.$dtoName".replace("/", "."))
    }
}

private fun getConvertorFilePackageName(modelPackage: String): String {
    return if (modelPackage.substringAfter("data") != "") {
        modelPackage.substringAfter("data")
    } else {
        modelPackage
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

private fun KSType.mapModelTypeToDtoType(logger: KSPLogger, parentTypeParameters: Set<String>): String {
    val arguments = this.arguments
    val argumentsDtos = mutableSetOf<String>()
    if (arguments.isNotEmpty()) {
        arguments.forEach {
            val resolvedKsType =
                it.type?.resolve() ?: throw IllegalArgumentException("property ksType name cannot be null")
            argumentsDtos.add(resolvedKsType.mapModelTypeToDtoType(logger, parentTypeParameters))
        }
    }
    return if (this.toString().contains("List")) {
        if (argumentsDtos.isNotEmpty()) {
            argumentsDtos.first()
        } else {
            ""
        }
    } else
        if (this.toString().contains("Long")) {
            ".map{ kmmLongOf(it) }"
        } else if (this.toString().contains("DateTime")) {
            ".map{ it.toDto() }"
        } else {
            if (this.declaration.closestClassDeclaration()?.classKind == ClassKind.ENUM_CLASS) {
                ".map{ it.dto() }"
            } else {
                val qualifier = this.declaration.qualifiedName?.getQualifier()
                    ?: throw IllegalArgumentException("qualifier cannot be null")
                if (qualifier.contains(CodeGenerationSettings.rootProjectName)) {
                    if (this.toString() in parentTypeParameters) {
                        ""
                    } else {
                        ".map{ it.toDto() }"
                    }
                } else {
                    ""
                }
            }
        }
}

private fun KSType.mapDtoTypeToModelType(logger: KSPLogger, parentTypeParameters: Set<String>): String {
    val arguments = this.arguments
    val argumentsModels = mutableSetOf<String>()
    if (arguments.isNotEmpty()) {
        arguments.forEach {
            val resolvedKsType =
                it.type?.resolve() ?: throw IllegalArgumentException("property ksType name cannot be null")
            argumentsModels.add(resolvedKsType.mapDtoTypeToModelType(logger, parentTypeParameters))
        }
    }
    return if (this.toString().contains("List")) {
        if (argumentsModels.isNotEmpty()) {
            argumentsModels.first()
        } else {
            ""
        }
    } else
        if (this.toString().contains("Long")) {
            ".map{ it.toLong() }"
        } else if (this.toString().contains("DateTime")) {
            ".map{ it.toDateTime() }"
        } else {
            if (this.declaration.closestClassDeclaration()?.classKind == ClassKind.ENUM_CLASS) {
                ".map{ it.enum() }"
            } else {
                val qualifier = this.declaration.qualifiedName?.getQualifier()
                    ?: throw IllegalArgumentException("qualifier cannot be null")
                if (qualifier.contains(CodeGenerationSettings.rootProjectName)) {
                    if (this.toString() in parentTypeParameters) {
                        ""
                    } else {
                        ".map{ it.toModel() }"
                    }
                } else {
                    ""
                }
            }
        }
}

