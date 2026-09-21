import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

// Pinned so this module tracks the same androidx-benchmark train wired into
// :app (macro-benchmark + baseline-profile artifacts share a version).
val benchmarkVersion = "1.5.0"

android {
    namespace = "com.eliormachlev.currencix.baselineprofile"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_21)
        targetCompatibility(JavaVersion.VERSION_21)
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        // Baseline-profile generators are AndroidX-benchmark instrumented tests;
        // the runner must be the benchmark runner, not the default one.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Mirror :app's flavor dimension so this test module can target both the
    // fdroid and play flavors. Baseline-profile generation runs per-flavor.
    flavorDimensions.add("version")
    productFlavors {
        create("play") {
            dimension = "version"
        }
        create("fdroid") {
            dimension = "version"
        }
    }

    // AGP requires an explicit target variant for the com.android.test module;
    // release is the correct target because R8 rewrites bytecode and the
    // profile must reflect the final (minified) code paths.
    targetProjectPath = ":app"
}

// Which variant of :app to generate profiles against. Wiring both flavors keeps
// per-flavor rewrites (if any diverge) reflected in the shipped profile.
baselineProfile {
    // Emit both baseline + startup profiles from a single instrumentation run.
    enableEmulatorDisplay = false
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.espresso:espresso-core:3.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:$benchmarkVersion")
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId ?: "" },
        )
    }
}
