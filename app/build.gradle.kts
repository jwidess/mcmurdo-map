plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val manifestFile = rootProject.file(".release-please-manifest.json")
val manifestVersion: String? = if (manifestFile.exists()) {
    try {
        val content = manifestFile.readText()
        val regex = Regex(""""\."\s*:\s*"([^"]+)"""")
        regex.find(content)?.groupValues?.get(1)
    } catch (e: Exception) {
        null
    }
} else null

val rawVersionName = (project.findProperty("versionName") as String?)
    ?: manifestVersion
    ?: "1.0.0"

// Strip leading 'v' if present (e.g. "v1.0.0" -> "1.0.0")
val appVersionName = rawVersionName.removePrefix("v")

// Compute increasing versionCode from semver (MAJOR * 10000 + MINOR * 100 + PATCH)
val computedVersionCode: Int = run {
    val parts = appVersionName.split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    (major * 10000) + (minor * 100) + patch
}

val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: computedVersionCode

android {
    namespace = "com.jwidess.mcmurdomap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jwidess.mcmurdomap"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            buildConfigField("Boolean", "IS_SLIM_BUILD", "false")
        }
        create("slim") {
            dimension = "edition"
            buildConfigField("Boolean", "IS_SLIM_BUILD", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            //! Use standard Android debug keystore for signing (temporary)
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}

// Automatically exclude PDF spec sheets from merged assets for slim flavor builds
tasks.matching { it.name.startsWith("mergeSlim") && it.name.endsWith("Assets") }.configureEach {
    doLast {
        val task = this as? com.android.build.gradle.tasks.MergeSourceSetFolders ?: return@doLast
        val outputDir = task.outputDir.orNull?.asFile ?: return@doLast
        val docDir = File(outputDir, "assets/documents")
        if (docDir.exists()) {
            docDir.deleteRecursively()
        }
    }
}
