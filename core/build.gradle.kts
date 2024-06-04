plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

fun setupManifest(jar: Jar) {
    jar.manifest {
        attributes(mapOf("Can-Retransform-Classes" to "true"))
    }
}

// Workaround for https://github.com/Kotlin/dokka/issues/1833: make implicit dependency explicit
tasks.named("dokkaHtmlPartial") {
    dependsOn("jar")
}