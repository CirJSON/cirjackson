import org.jetbrains.dokka.gradle.DokkaTask

tasks.withType<DokkaTask>().configureEach {
    outputs.upToDateWhen { false }
    dokkaSourceSets {
        outputs.upToDateWhen { false }
        configureEach {
            outputs.upToDateWhen { false }
        }
    }
}