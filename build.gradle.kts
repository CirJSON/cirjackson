import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import java.net.URL
import java.util.*

val properties = File(rootDir, "gradle.properties").inputStream().use {
    Properties().apply { load(it) }
}
val cirJacksonVersion: String = properties.getProperty("cirjackson.version")

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.kover") version "0.8.2"
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
        outputs.upToDateWhen { false }
        useJUnitPlatform()
    }
    kotlin {
        jvmToolchain(17)
    }

    @Suppress("ObjectLiteralToLambda")
    val createVersionTask = task<Copy>("createVersion") {
        outputs.upToDateWhen { false }

        from("src/main/template", object : Action<CopySpec> {
            override fun execute(spec: CopySpec) {
                spec.eachFile(object : Action<FileCopyDetails> {
                    override fun execute(details: FileCopyDetails) {
                        details.name = details.name.removeSuffix(".in")
                        val fileContent =
                                details.file.readText().replace("@projectVersion@", project.version.toString())
                                        .replace("@projectGroupId@", project.group.toString())
                                        .replace("@projectArtifactId@", project.name)

                        details.file.writeText(fileContent)
                    }
                })
            }
        })
        into("gen")
    }

    sourceSets {
        main {
            kotlin.srcDirs("src/main/kotlin", "gen")
        }
    }

    tasks.compileKotlin {
        dependsOn(createVersionTask)
    }
}

val allLibs = listOf(":cirjackson-core")

task<Test>("checkAll") {
    group = "verification"

    allLibs.forEach {
        dependsOn("$it:check")
    }
}

configure(subprojects) {
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "org.jetbrains.kotlinx.kover")
    configurePathsaver()
    configureDokkaSetup()
}

val knitVersion: String by project

// Setup top-level 'dokkaHtmlMultiModule' with templates
tasks.withType<DokkaMultiModuleTask>().named("dokkaHtmlMultiModule") {
    setupDokkaTemplatesDir(this)
}

dependencies {
    // Add explicit dependency between Dokka and Knit plugin
    add("dokkaHtmlMultiModulePlugin", "org.jetbrains.kotlinx:dokka-pathsaver-plugin:$knitVersion")

    for (lib in allLibs) {
        kover(project(lib))
    }
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
    tasks.withType(DokkaTask::class).configureEach {
        suppressInheritedMembers = true
        setupDokkaTemplatesDir(this)
        outputs.upToDateWhen { false }

        dokkaSourceSets.configureEach {
            jdkVersion = 17

            // Something suspicious to figure out, probably legacy of earlier days
            dependsOn(project.configurations["compileClasspath"])
            includes.from("packages.md")
        }

        // Source links
        dokkaSourceSets.configureEach {
            sourceLink {
                localDirectory = rootDir
                remoteUrl = URL("https://github.com/cirjson/cirjackson/tree/master")
                remoteLineSuffix = "#L"
            }
        }
    }

    tasks.withType(DokkaTaskPartial::class).configureEach {
        suppressInheritedMembers = true
        setupDokkaTemplatesDir(this)
        outputs.upToDateWhen { false }

        dokkaSourceSets.configureEach {
            jdkVersion = 17

            // Something suspicious to figure out, probably legacy of earlier days
            dependsOn(project.configurations["compileClasspath"])
            includes.from("packages.md")
        }

        // Source links
        dokkaSourceSets.configureEach {
            sourceLink {
                localDirectory = rootDir
                remoteUrl = URL("https://github.com/cirjson/cirjackson/tree/master")
                remoteLineSuffix = "#L"
            }
        }
    }
}

/**
 * Setups Dokka templates. While this directory is empty in our repository,
 * 'kotlinlang' build pipeline adds templates there when preparing our documentation
 * to be published on kotlinlang.
 *
 * See:
 * - Template setup: https://github.com/JetBrains/kotlin-web-site/blob/master/.teamcity/builds/apiReferences/kotlinx/coroutines/KotlinxCoroutinesPrepareDokkaTemplates.kt
 * - Templates repository: https://github.com/JetBrains/kotlin-web-site/tree/master/dokka-templates
 */
private fun Project.setupDokkaTemplatesDir(dokkaTask: AbstractDokkaTask) {
    dokkaTask.pluginsMapConfiguration = mapOf(
            "org.jetbrains.dokka.base.DokkaBase" to """{ "templatesDir" : "${
                project.rootProject.projectDir.toString().replace('\\', '/')
            }/dokka-templates" }"""
    )
}
