pluginManagement {
    val kotlinVersion: String by settings
    val dokkaVersion: String by settings
    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.dokka") version dokkaVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "cirjackson"
include(":cirjackson-core")
project(":cirjackson-core").projectDir = file("./core")

include(":cirjackson-annotations")
project(":cirjackson-annotations").projectDir = file("./annotations")
