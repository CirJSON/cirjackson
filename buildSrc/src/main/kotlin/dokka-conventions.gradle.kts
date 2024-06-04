import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial

plugins {
    id("org.jetbrains.dokka")
}

configure(subprojects) {
    apply(plugin = "org.jetbrains.dokka")
    configurePathsaver()
    configureDokkaSetup()
}

val knitVersion: String by project

dependencies {
    // Add explicit dependency between Dokka and Knit plugin
    add("dokkaHtmlMultiModulePlugin", "org.jetbrains.kotlinx:dokka-pathsaver-plugin:$knitVersion")
}

// Dependencies for Knit processing: Knit plugin to work with Dokka
private fun Project.configurePathsaver() {
    tasks.withType(DokkaTaskPartial::class).configureEach {
        dependencies {
            plugins("org.jetbrains.kotlinx:dokka-pathsaver-plugin:$knitVersion")
        }
    }
}

private fun Project.configureDokkaSetup() {
    tasks.withType<DokkaTask>().configureEach {
        outputs.upToDateWhen { false }
    }

    tasks.withType(DokkaTaskPartial::class).configureEach {
        suppressInheritedMembers = true
        outputs.upToDateWhen { false }

        dokkaSourceSets.configureEach {
            includes.from("packages.md")
        }
    }
}