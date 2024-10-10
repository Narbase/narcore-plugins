import org.jetbrains.kotlin.gradle.dsl.JvmTarget

allprojects {
    version = "0.0.1"

    val javaVersion = JavaVersion.VERSION_1_8
    val jvmTargetValue = JvmTarget.fromTarget("$javaVersion")

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "$javaVersion"
        targetCompatibility = "$javaVersion"

    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>()
        .configureEach {
            compilerOptions {
                jvmTarget.set(jvmTargetValue)
                freeCompilerArgs.addAll(
                    "-Xcontext-receivers",
                    "-Xexpect-actual-classes",
//                    "-Xjdk-release=$javaVersion",
                )


                optIn.add("kotlin.js.ExperimentalJsExport")
            }
        }
}


plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlinJvm) apply false
}







