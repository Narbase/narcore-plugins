package com.narbase.narcore.main

import com.narbase.narcore.main.models.ConvertorFile
import com.narbase.narcore.main.models.DBTableModel
import com.narbase.narcore.main.models.DtoFile

object CodeGenerationSettings {
    var didGenerate = false
    lateinit var rootProjectName: String
    lateinit var commonModulePackagesPaths: String
    var targetTableName: String? = null
    private lateinit var _destinationDaosPackage: String
    private lateinit var _destinationDtosPackage: String
    private lateinit var _destinationConvertorsPackage: String
    fun setDestinationDaosPackage(path: String) {
        _destinationDaosPackage = path.substringAfter("kotlin/").replace("/", ".")
    }
    fun getDestinationDaosPackage(): String {
        return _destinationDaosPackage
    }
    fun setDestinationDtosPackage(path: String) {
        _destinationDtosPackage = path.substringAfter("kotlin/").replace("/", ".")
    }
    fun getDestinationDtosPackage(): String {
        return _destinationDtosPackage
    }
    fun setDestinationConvertorsPackage(path: String) {
        _destinationConvertorsPackage = path.substringAfter("kotlin/").replace("/", ".")
    }
    fun getDestinationConvertorsPackage(): String {
        return _destinationConvertorsPackage
    }


    private val generatedModels: MutableSet<DBTableModel> = mutableSetOf()
    fun addGeneratedModel (model: DBTableModel) {
        generatedModels.add(model)
    }
    fun hasGeneratedModel(modelName: String): Boolean {
        return generatedModels.any { it.modelName == modelName }
    }
    fun getGeneratedModel(modelName: String): DBTableModel? {
        return generatedModels.firstOrNull {
            it.modelName == modelName
        }
    }

    private val generatedDtos: MutableSet<DtoFile> = mutableSetOf()
    fun addGeneratedDto (dto: DtoFile) {
        generatedDtos.add(dto)
    }
    fun hasGeneratedDto(dtoName: String): Boolean {
        return generatedDtos.any { it.dto.dtoName == dtoName }
    }
    fun getGeneratedDto(dtoName: String): DtoFile? {
        return generatedDtos.firstOrNull {
            it.dto.dtoName == dtoName
        }
    }

    private val generatedConvertors: MutableSet<ConvertorFile> = mutableSetOf()
    fun addGeneratedConvertor (convertor: ConvertorFile) {
        generatedConvertors.add(convertor)
    }
    fun hasGeneratedConvertor(convertorName: String): Boolean {
        return generatedConvertors.any { it.convertorName == convertorName }
    }
    fun getGeneratedConvertor(convertorName: String): ConvertorFile? {
        return generatedConvertors.firstOrNull {
            it.convertorName == convertorName
        }
    }
}