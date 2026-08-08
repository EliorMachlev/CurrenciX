@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("com.google.devtools.ksp") version "2.3.10"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

base {
    archivesName.set("com.eliormachlev.currencix-v12300")
}

android {
    namespace = "com.eliormachlev.currencix"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.eliormachlev.currencix"
        minSdk = 26
        targetSdk = 37
        // SemVer
        versionName = "1.23.0"
        versionCode = 12300
    }

    signingConfigs {
        create("release") {
            if (getSecret("KEYSTORE_FILE") != null) {
                storeFile = File(getSecret("KEYSTORE_FILE")!!)
                storePassword = getSecret("KEYSTORE_PASSWORD")
                keyAlias = getSecret("KEYSTORE_KEY_ALIAS")
                keyPassword = getSecret("KEYSTORE_KEY_PASSWORD")
            }
        }
        // Shared debug keystore checked into the repo so debug APKs built on
        // any machine (CI or local) share a signature and can upgrade cleanly
        // instead of tripping INSTALL_FAILED_UPDATE_INCOMPATIBLE. Credentials
        // are the Android SDK defaults — non-secret by design.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Only apply the release signing config when a keystore is
            // actually present — CI's F-Droid reproducibility job builds
            // release unsigned and would otherwise fail
            // validateSigningRelease with "Keystore file not set".
            if (getSecret("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Release builds never carry PR or commit context — the in-app
            // "Release notes" entry deep-links to the GitHub release for the
            // shipped versionName. Fields must exist so debug/release share
            // a shape.
            buildConfigField("String", "PR_URL", "\"\"")
            buildConfigField("String", "COMMIT_SHA", "\"\"")
        }
        debug {
            applicationIdSuffix = ".debug"
            // Commit SHA sourced from -PdebugCommitSha (CI passes it) or, as
            // a fallback, the local git HEAD short SHA so a locally-built APK
            // still knows which commit it came from. versionName encodes the
            // SHA (e.g. "1.23.0-abc1234"); if git isn't available, keep the
            // "[DEBUG]" tag.
            val commitSha = (project.findProperty("debugCommitSha") as String?) ?: gitShortSha()
            versionNameSuffix = if (commitSha != null) "-$commitSha" else " [DEBUG]"
            buildConfigField("String", "COMMIT_SHA", "\"${commitSha ?: ""}\"")
            // CI passes -PprUrl=<pr html_url> for pull_request builds so the
            // in-app "Release notes" entry can deep-link back to the exact PR
            // the APK was built from. When absent the client falls back to
            // the commit URL, and finally to the repo pulls page.
            val prUrl = project.findProperty("prUrl") as String? ?: ""
            buildConfigField("String", "PR_URL", "\"$prUrl\"")
        }
    }

    flavorDimensions.add("version")
    productFlavors {
        create("play") {
            dimension = "version"
        }
        create("fdroid") {
            dimension = "version"
        }
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_21)
        targetCompatibility(JavaVersion.VERSION_21)
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    lint {
        disable.add("MissingTranslation")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    // kotlin
    implementation("androidx.core:core-ktx:1.19.0")
    // support libs
    val appCompatVersion = "1.7.1"
    implementation("androidx.appcompat:appcompat:$appCompatVersion")
    implementation("androidx.appcompat:appcompat-resources:$appCompatVersion")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    val livecycleVersion = "2.11.0"
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$livecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$livecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$livecycleVersion")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.window:window:1.5.1")
    implementation("com.google.android.material:material:1.14.0")
    // downloader: OkHttp is the sole HTTP client. Timber-bridged logging
    // interceptor is wired up in HttpClientProvider; provider modules call
    // the shared instance via the HttpClientProvider.fetch extension.
    val okHttpVersion = "5.4.0"
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okHttpVersion")
    val moshiVersion = "1.15.2"
    implementation("com.squareup.moshi:moshi-kotlin:$moshiVersion")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:$moshiVersion")
    // math: EvalEx (Apache-2.0) evaluates the calculator expression. Replaced
    // mXparser 4.4.3, which was pinned because its v5+ dual license isn't
    // F-Droid compatible. EvalEx is actively maintained and BigDecimal-native.
    implementation("com.ezylang:EvalEx:3.7.0")
    // compose (hosts the Vico chart plus migrated UI surfaces via ComposeView)
    val composeBomVersion = "2026.06.01"
    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // Pin material3 to latest stable (newer than the BOM ships).
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$livecycleVersion")
    // charts
    val vicoVersion = "3.2.3"
    implementation("com.patrykandpatrick.vico:compose:$vicoVersion")
    // crypto: BouncyCastle provides pure-Java Argon2id, used by BackupManager
    // for password-based backup encryption (quantum-resistant KDF).
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    // logging: Timber routes to a rotating file tree written under filesDir/logs.
    // Local-only — no remote crash / analytics sink.
    implementation("com.jakewharton.timber:timber:5.0.1")
    // test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    // core-testing provides InstantTaskExecutorRule so LiveData setValue can
    // run on the JVM test thread without hitting the main-thread assertion.
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    // fuzzing
    val junitVersion = "6.1.2"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:$junitVersion")
    testImplementation("com.code-intelligence:jazzer-junit:0.30.0")
}

// Best-effort short git SHA for the currently checked-out HEAD. Returns null
// if git isn't installed, the repo isn't a git checkout, or the command
// fails for any reason — callers treat that as "no commit context".
fun gitShortSha(): String? =
    try {
        val proc =
            ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
        proc.waitFor()
        proc.inputStream
            .bufferedReader()
            .readLine()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

fun getSecret(key: String): String? {
    val secretsFile: File = rootProject.file("secrets.properties")
    return if (secretsFile.exists()) {
        val props = Properties()
        props.load(FileInputStream(secretsFile))
        props.getProperty(key)
    } else {
        null
    }
}

// versionCode <-> versionName /////////////////////////////////////////////////////////////////////

/**
 * Checks if versionCode and versionName match.
 * Needed because of F-Droid: both have to be hard-coded and can't be assigned dynamically.
 * So at least check during build for them to match.
 */
tasks.register("checkVersion") {
    doLast {
        val versionCode: Int? = android.defaultConfig.versionCode
        val correctVersionCode: Int = generateVersionCode(android.defaultConfig.versionName!!)
        if (versionCode != correctVersionCode) {
            throw GradleException(
                "versionCode and versionName don't match: versionCode should be $correctVersionCode. Is $versionCode.",
            )
        }
    }
}
tasks.findByName("assemble")!!.dependsOn(tasks.findByName("checkVersion")!!)

/**
 * Checks if a fastlane changelog for the current version is present.
 */
tasks.register("checkFastlaneChangelog") {
    doLast {
        val versionCode: Int? = android.defaultConfig.versionCode
        val changelogFile: File =
            file("$rootDir/fastlane/metadata/android/en-US/changelogs/$versionCode.txt")
        if (!changelogFile.exists()) {
            throw GradleException(
                "Fastlane changelog missing: expecting file '$changelogFile'",
            )
        }
    }
}
tasks.findByName("build")!!.dependsOn(tasks.findByName("checkFastlaneChangelog")!!)

/**
 * Generates a versionCode based on the given semVer String.
 *
 * @param semVer e.g. 1.3.1
 * @return e.g. 10301 (-> 1 03 01)
 */
fun generateVersionCode(semVer: String): Int =
    semVer
        .split('.')
        .map { Integer.parseInt(it) }
        .reduce { sum, value -> sum * 100 + value }
