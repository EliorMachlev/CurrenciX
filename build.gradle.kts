import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
    // dependency-update-checker
    id("com.github.ben-manes.versions") version "0.61.0"
    // Spotless drives ktlint (chosen over the org.jlleitschuh.gradle.ktlint
    // plugin because that plugin's Android source-set hook does not fire under
    // AGP 9 — only its .kts checker runs, leaving app/src/main/kotlin unlinted).
    // apply=false at root so the base plugin doesn't collide with the manual
    // clean task below; each subproject opts in.
    id("com.diffplug.spotless") version "8.9.0" apply false
}

// ktlint CLI pinned so Spotless updates don't silently bump the underlying
// linter version.
val ktlintCliVersion = "1.5.0"

subprojects {
    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**", "**/generated/**")
            ktlint(ktlintCliVersion)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintCliVersion)
        }
    }
}

// only check for stable versions
tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.register("clean", Delete::class.java) {
    delete(rootProject.layout.buildDirectory)
}
