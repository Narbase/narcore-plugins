plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "org.example"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.0.0-1.0.24")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}