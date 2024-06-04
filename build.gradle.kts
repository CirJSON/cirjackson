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

apply(plugin = "dokka-conventions")
