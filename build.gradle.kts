import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    id("com.android.application") version "9.3.2" apply false
    id("com.android.test") version "9.3.2" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
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
    // change what CI enforces. See config/detekt/detekt.yml for tuned rules
    // and config/detekt/baseline-<module>.xml for the "start clean going
    // forward" per-module baselines that swallow pre-existing violations.
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

// ktlint CLI pinned so Spotless updates don't silently bump the underlying
// linter version.
val ktlintCliVersion = "1.5.0"

// Detekt config path — shared across subprojects. Baseline is per-subproject
// (config/detekt/baseline-<module>.xml) because detekt's baseline format keys
// off simple file names and merging two modules into one file causes the
// later writer to clobber the earlier writer.
val detektConfigFile = rootProject.file("config/detekt/detekt.yml")
fun Project.detektBaselineFile(): File =
    rootProject.file("config/detekt/baseline-${name}.xml")

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

    apply(plugin = "io.gitlab.arturbosch.detekt")
    val moduleBaseline = detektBaselineFile()
    configure<DetektExtension> {
        toolVersion = "1.23.8"
        config.setFrom(detektConfigFile)
        // Baseline is opt-in per subproject — only apply it if it exists so
        // the initial `detektBaseline` run can succeed on a virgin repo.
        if (moduleBaseline.exists()) {
            baseline = moduleBaseline
        }
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        autoCorrect = false
        ignoreFailures = false
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget = "21"
        // The Android plugin doesn't wire its source-sets into the plain
        // `detekt` task, and the `helpers` JVM module's `sourceSets["main"]`
        // convention wiring also skips it. Point the task at src/**/*.kt
        // explicitly so both modules actually analyse code.
        setSource(files("src"))
        include("**/*.kt", "**/*.kts")
        exclude("**/build/**", "**/generated/**", "**/resources/**")
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
            md.required.set(false)
        }
    }
    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        jvmTarget = "21"
        setSource(files("src"))
        include("**/*.kt", "**/*.kts")
        exclude("**/build/**", "**/generated/**", "**/resources/**")
        // Write per-module baseline to a stable, VCS-tracked path so
        // subsequent runs pick it up automatically via the
        // DetektExtension.baseline wiring above.
        baseline.set(moduleBaseline)
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
