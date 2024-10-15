package com.narbase.narcore.main

import groovy.transform.Internal
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import java.io.File
import javax.inject.Inject


open class NarratorExtension {
    lateinit var dtoWebPath: String

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
        target.tasks.register("narrateAll", NarratorTask::class.java, config).configure { dependsOn("kspKotlin") }
    }
}


abstract class NarratorTask @Inject constructor(@Input val config: NarratorExtension) : DefaultTask() {
    @get:Input
    @set:Option(option = "overwrite", description = "Whether this task should overwrite existing files")
    var overwrite: Boolean = false
    @get:Input
    @set:Option(option = "table", description = "Name of target db table")
    @Optional
    var tableName: String? = null


    @Internal
    private val destinationServerRootPath = "${project.projectDir.path}/src/main/kotlin"

    @Internal
    private val destinationServerPackagePath =
        "${destinationServerRootPath}/${config.destinationConfig.packageRelativePath}"

    @Internal
    private val destinationDaosRelativePath =
        "${destinationServerPackagePath}/${config.destinationConfig.daosRelativePath}"

    @Internal
    private val sourceRootPath = "${project.projectDir.path}/build/generated/ksp/main/kotlin"

    @Internal
    private val sourceDaosRelativePath = "${sourceRootPath}/daos"

    @Internal
    private val destinationDtoWebRootPath = "${config.dtoWebPath}/src/commonMain/kotlin"

    @Internal
    private val destinationDtoWebPackagePath =
        "${destinationDtoWebRootPath}/${config.destinationConfig.packageRelativePath}"

    @Internal
    private val destinationDtosRelativePath =
        "${destinationDtoWebPackagePath}/${config.destinationConfig.dtosRelativePath}"

    @Internal
    private val sourceDtosRelativePath = "${sourceRootPath}/dtos"

    @Internal
    private val destinationConvertorsRelativePath =
        "${destinationServerPackagePath}/${config.destinationConfig.convertorsRelativePath}"

    @Internal
    private val sourceConvertorsRelativePath = "${sourceRootPath}/conversions"

    @Internal
    private val sourceDaosDirectory = File(sourceDaosRelativePath)

    @Internal
    private val sourceDtosDirectory = File(sourceDtosRelativePath)

    @Internal
    private val sourceConvertorsDirectory = File(sourceConvertorsRelativePath)

    @Internal
    private val destinationDaosDirectory = File(destinationDaosRelativePath)

    @Internal
    private val destinationDtosDirectory = File(destinationDtosRelativePath)

    @Internal
    private val destinationConvertorsDirectory = File(destinationConvertorsRelativePath)


    @Internal
    private val commonModulePackagesPaths =
        getCommonModulePaths(config.dtoWebPath, config.destinationConfig.packageRelativePath)

    @Internal
    private val kspOptions = KspOptions(
        taskName = this.name,
        tableName = tableName,
        rootProjectName = project.rootProject.name,
        destinationDaosPath = destinationDaosRelativePath,
        destinationDtosPath = destinationDtosRelativePath,
        destinationConvertorsPath = destinationConvertorsRelativePath,
        commonModulePackagesPaths = commonModulePackagesPaths
    )

    @Internal
    private val kspOptionsFile = File("${project.rootProject.projectDir.path}/.kspOptions.json").apply {
        this.writeText(
            groovy.json.JsonOutput.prettyPrint(
                groovy.json.JsonOutput.toJson(kspOptions)
            )
        )
    }

    @TaskAction
    fun execute() {
        try {
            val didCopyDaos = sourceDaosDirectory.copyRecursively(
                destinationDaosDirectory,
                overwrite,
                onError = { file, exception ->
                    if (exception is FileAlreadyExistsException && overwrite.not())
                        OnErrorAction.SKIP
                    else
                        OnErrorAction.TERMINATE
                }
            )
            if (didCopyDaos) sourceDaosDirectory.deleteRecursively()

            val didCopyDtos =
                sourceDtosDirectory.copyRecursively(
                    destinationDtosDirectory,
                    overwrite,
                    onError = { file, exception ->
                        if (exception is FileAlreadyExistsException && overwrite.not())
                            OnErrorAction.SKIP
                        else
                            OnErrorAction.TERMINATE
                    }
                )
            if (didCopyDtos) sourceDtosDirectory.deleteRecursively()

            val didCopyConvertors =
                sourceConvertorsDirectory.copyRecursively(
                    destinationConvertorsDirectory,
                    overwrite,
                    onError = { file, exception ->
                        if (exception is FileAlreadyExistsException && overwrite.not())
                            OnErrorAction.SKIP
                        else
                            OnErrorAction.TERMINATE
                    }
                )
            if (didCopyConvertors) sourceConvertorsDirectory.deleteRecursively()
        } catch (e: Exception) {
            if (e !is FileAlreadyExistsException || overwrite) {
                throw e
            }
        } finally {
            kspOptionsFile.deleteRecursively()
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
    val taskName: String,
    val tableName: String?,
    val rootProjectName: String,
    val destinationDaosPath: String,
    val destinationDtosPath: String,
    val destinationConvertorsPath: String,
    val commonModulePackagesPaths: String
)