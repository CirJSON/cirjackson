plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

fun setupManifest(jar: Jar) {
    jar.manifest {
        attributes(mapOf("Can-Retransform-Classes" to "true"))
    }
}