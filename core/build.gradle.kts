plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

fun setupManifest(jar: Jar) {
    jar.manifest {
        attributes(mapOf("Can-Retransform-Classes" to "true"))
    }
}

dependencies {
    implementation("ch.randelshofer:fastdoubleparser:1.0.0")
}
