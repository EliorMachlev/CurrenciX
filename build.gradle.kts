import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension

plugins {
    id("com.android.application") version "9.3.2" apply false
    id("com.android.test") version "9.3.2" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.20" apply false
    // Baseline-profile Gradle plugin — wired at :app (to consume generated
    // profiles) and :baselineprofile (to run the generator). Pinned to the
    // same androidx-benchmark train as the macro-benchmark dependency in the
    // :baselineprofile module so the generator + consumer stay in lockstep.
    id("androidx.baselineprofile") version "1.5.0" apply false
    // dependency-update-checker
    id("io.github.ben-manes.versions") version "0.61.0"
    // Spotless drives ktlint (chosen over the org.jlleitschuh.gradle.ktlint
    // plugin because that plugin's Android source-set hook does not fire under
    // AGP 9 — only its .kts checker runs, leaving app/src/main/kotlin unlinted).
    // apply=false at root so the base plugin doesn't collide with the manual
    // clean task below; each subproject opts in.
    id("com.diffplug.spotless") version "8.10.0" apply false
    // Static analysis. Version pinned so upstream releases can't silently
    // change what CI enforces. See config/detekt/detekt.yml for tuned rules.
    // There is no baseline: every finding fails the build.
    id("dev.detekt") version "2.0.0-alpha.6" apply false
}

// ktlint CLI pinned so Spotless updates don't silently bump the underlying
// linter version.
val ktlintCliVersion = "1.5.0"

// Detekt config path — shared across subprojects.
val detektConfigFile = rootProject.file("config/detekt/detekt.yml")

// detekt 2 runs its type-aware rules (LongParameterList, UnsafeCallOnNullableType,
// InjectDispatcher, …) only in the per-variant tasks, not in the plain
// `detekt` one. These cover every source folder once: main + fdroid + debug,
// play + release, and the unit tests.
val detektVariantTasks =
    mapOf(
        "app" to listOf("detektFdroidDebug", "detektPlayRelease", "detektFdroidDebugUnitTest"),
        "helpers" to listOf("detektMain", "detektTest"),
        "baselineprofile" to listOf("detektFdroidBenchmarkRelease"),
    )

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

    apply(plugin = "dev.detekt")
    configure<DetektExtension> {
        toolVersion = "2.0.0-alpha.6"
        config.setFrom(detektConfigFile)
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        autoCorrect = false
        ignoreFailures = false
    }
    // `detekt` stands for the variant tasks above: it runs them, and skips
    // its own pass, which would leave the type-aware rules out.
    tasks.named("detekt") {
        enabled = false
        dependsOn(detektVariantTasks.getValue(project.name))
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget = "21"
        reports {
            html.required.set(true)
            checkstyle.required.set(true)
            sarif.required.set(true)
            markdown.required.set(false)
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
