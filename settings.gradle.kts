pluginManagement {
    val kotlinVersion: String by settings
    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "cirjackson"
include(":cirjackson-core")
project(":cirjackson-core").projectDir = file("./core")

