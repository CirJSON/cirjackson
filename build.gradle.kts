import java.util.*

val properties = File(rootDir, "gradle.properties").inputStream().use {
    Properties().apply { load(it) }
}
val cirJacksonVersion: String = properties.getProperty("cirjackson.version")

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
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
    apply(plugin = "org.jetbrains.dokka")

    dependencies {
        testImplementation(kotlin("test"))
    }

    tasks.test {
        outputs.upToDateWhen { false }
        useJUnitPlatform()
    }
    kotlin {
        jvmToolchain(17)
    }
}

val allLibs = listOf(":cirjackson-core")

task<Test>("checkAll") {
    group = "verification"

    allLibs.forEach {
        dependsOn("$it:check")
    }
}
