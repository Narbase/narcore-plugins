package com.narbase.narcore.main

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import java.io.File


open class NarratorExtension {
    lateinit var dtoWebPath: String
    var shouldOverwrite: Boolean = false

    fun destinationConfig(block: DestinationConfig.() -> Unit) {
        destinationConfig.apply(block)
    }

    val destinationConfig = DestinationConfig()

    class DestinationConfig {
        lateinit var packageRelativePath: String
        lateinit var daosRelativePath: String
        lateinit var dtosRelativePath: String
        lateinit var convertorsRelativePath: String
    }
}

class NarratorPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val config = target.extensions.create("narrator", NarratorExtension::class)
        target.tasks.register("narrateAll") {

            val destinationServerRootPath = "${project.projectDir.path}/src/main/kotlin"
            val destinationServerPackagePath =
                "${destinationServerRootPath}/${config.destinationConfig.packageRelativePath}"
            val destinationDaosRelativePath =
                "${destinationServerPackagePath}/${config.destinationConfig.daosRelativePath}"
            val sourceRootPath = "${project.projectDir.path}/build/generated/ksp/main/kotlin"
            val sourceDaosRelativePath = "${sourceRootPath}/daos"

            val destinationDtoWebRootPath = "${config.dtoWebPath}/src/commonMain/kotlin"
            val destinationDtoWebPackagePath =
                "${destinationDtoWebRootPath}/${config.destinationConfig.packageRelativePath}"
            val destinationDtosRelativePath =
                "${destinationDtoWebPackagePath}/${config.destinationConfig.dtosRelativePath}"
            val sourceDtosRelativePath = "${sourceRootPath}/dtos"

            val destinationConvertorsRelativePath =
                "${destinationServerPackagePath}/${config.destinationConfig.convertorsRelativePath}"
            val sourceConvertorsRelativePath = "${sourceRootPath}/conversions"

            val sourceDaosFile = File(sourceDaosRelativePath)
            val sourceDtosFile = File(sourceDtosRelativePath)
            val sourceConvertorsFile = File(sourceConvertorsRelativePath)
            val destinationDaosFile = File(destinationDaosRelativePath)
            val destinationDtosFile = File(destinationDtosRelativePath)
            val destinationConvertorsFile = File(destinationConvertorsRelativePath)


            val commonModulePackagesPaths =
                getCommonModulePaths(config.dtoWebPath, config.destinationConfig.packageRelativePath)


            val kspOptions = KspOptions(
                rootProjectName = project.rootProject.name,
                destinationDaosPath = destinationDaosRelativePath,
                destinationDtosPath = destinationDtosRelativePath,
                destinationConvertorsPath = destinationConvertorsRelativePath,
                commonModulePackagesPaths = commonModulePackagesPaths
            )
            val kspOptionsFile = File("${project.rootProject.projectDir.path}/.kspOptions.json")
            kspOptionsFile.writeText(
                groovy.json.JsonOutput.prettyPrint(
                    groovy.json.JsonOutput.toJson(kspOptions)
                )
            )

            dependsOn("kspKotlin")
            doLast {
                val didCopyDaos = sourceDaosFile.copyRecursively(destinationDaosFile, config.shouldOverwrite)
                if (didCopyDaos) sourceDaosFile.deleteRecursively()

                val didCopyDtos = sourceDtosFile.copyRecursively(destinationDtosFile, config.shouldOverwrite)
                if (didCopyDtos) sourceDtosFile.deleteRecursively()

                val didCopyConvertors =
                    sourceConvertorsFile.copyRecursively(destinationConvertorsFile, config.shouldOverwrite)
                if (didCopyConvertors) sourceConvertorsFile.deleteRecursively()

                kspOptionsFile.deleteRecursively()
            }
        }
    }
}

fun File.listAllDirectories(): Set<File> {
    val allDirectories = mutableSetOf<File>()
    val directories = this.listFiles(File::isDirectory)
    if (directories != null) {
        allDirectories.addAll(directories)
        directories.forEach {
            allDirectories.addAll(it.listAllDirectories())
        }
    }
    return allDirectories
}

fun getCommonModulePaths(dtoWebPath: String, destinationPackageRelativePath: String): String {
    val commonModulePath =
        "$dtoWebPath/src/commonMain/kotlin/$destinationPackageRelativePath"
    val commonModulePackages = File(commonModulePath).listAllDirectories().map { it.path }
    var commonModulePackagesPaths = ""
    commonModulePackages.forEachIndexed { index, item ->
        commonModulePackagesPaths += item
        if (index != commonModulePackages.lastIndex) {
            commonModulePackagesPaths += ";"
        }
    }
    return commonModulePackagesPaths
}

data class KspOptions(
    val rootProjectName: String,
    val destinationDaosPath: String,
    val destinationDtosPath: String,
    val destinationConvertorsPath: String,
    val commonModulePackagesPaths: String
)