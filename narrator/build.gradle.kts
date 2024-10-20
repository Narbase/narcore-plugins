plugins {
    alias(libs.plugins.kotlinJvm)
    id("maven-publish")
    `kotlin-dsl`
}

group = "com.narbase.narcore"
version = "0.1.2"

publishing {
    publications {
        create<MavenPublication>("narrator") {
            from(components["java"])
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.exposed.core)
    implementation("com.google.devtools.ksp:symbol-processing-api:2.0.0-1.0.24")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}

gradlePlugin {
    plugins {
        create("Narrator") {
            id = "$group.narrator"
            implementationClass = "com.narbase.narcore.main.NarratorPlugin"
            version = project.version
        }
    }
}