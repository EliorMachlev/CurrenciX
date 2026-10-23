import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Receipt scanning's text recognizer for the Play app, delivered on demand:
// most people never scan a receipt, so they never download its native code.
// The F-Droid app carries the recognizer itself (fdroidImplementation of
// :ocr-engine in :app), so this module's fdroid variant is empty.
plugins {
    id("com.android.dynamic-feature")
}

// Built only with an app bundle, the one build the app declares it in (see
// dynamicFeatures in :app); APK builds leave it out.
val buildsBundle = gradle.startParameter.taskNames.any { it.substringAfterLast(':').startsWith("bundle") }

androidComponents {
    beforeVariants { variant -> variant.enable = buildsBundle }
}

android {
    namespace = "com.eliormachlev.currencix.ocr.feature"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk { version = release(33) }
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":app"))
    "playImplementation"(project(":ocr-engine"))
}
