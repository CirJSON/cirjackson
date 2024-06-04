import java.util.*

plugins {
    `kotlin-dsl`
}

val gradleProperties = Properties().apply {
    file("../gradle.properties").inputStream().use { load(it) }
}

fun version(target: String): String {
    // Intercept reading from properties file
    if (target == "kotlin") {
        val snapshotVersion = properties["kotlin_snapshot_version"]
        if (snapshotVersion != null) return snapshotVersion.toString()
    }
    val version = "${target}Version"
    // Read from CLI first, used in aggregate builds
    return properties[version]?.let { "$it" } ?: gradleProperties.getProperty(version)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${version("dokka")}")
    implementation("org.jetbrains.dokka:dokka-core:${version("dokka")}")
}
