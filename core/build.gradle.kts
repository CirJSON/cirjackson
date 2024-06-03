import org.jetbrains.dokka.gradle.DokkaTask

tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        configureEach {
            includes.from(project.files(), "src/main/kotlin/org/cirjson/cirjackson/core/packages.md")
        }
    }
}