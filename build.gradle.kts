import java.util.*

val properties = File(rootDir, "gradle.properties").inputStream().use {
    Properties().apply { load(it) }
}
val cirJacksonVersion: String = properties.getProperty("cirjackson.version")

plugins {
    kotlin("jvm")
}

group = "org.cirjson.cirjackson"
version = cirJacksonVersion

repositories {
    mavenCentral()
}

subprojects {
    group = "org.cirjson.cirjackson"
    version = cirJacksonVersion

    repositories {
        mavenCentral()
    }

    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        testImplementation(kotlin("test"))
    }

    tasks.test {
        useJUnitPlatform()
    }
    kotlin {
        jvmToolchain(17)
    }
}