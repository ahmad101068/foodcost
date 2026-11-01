// The Android app: Compose screens over the shared modules, data kept in SQLite on the phone.
import java.io.File

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

// A private signing key, given to the build through the repository's secrets. Without it the
// release APK is signed with the build machine's debug key, which is enough for a preview.
val privateKeystore: File? = System.getenv("FOODCOST_KEYSTORE_FILE")?.let(::File)?.takeIf { it.isFile }

android {
    namespace = "ir.foodcost.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.foodcost.app"
        minSdk = 26
        targetSdk = 35
        // Every build on GitHub gets a higher number, so a new APK installs over the old one.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) + 1
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (privateKeystore != null) {
            create("private") {
                storeFile = privateKeystore
                storePassword = System.getenv("FOODCOST_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FOODCOST_KEY_ALIAS") ?: "foodcost"
                keyPassword = System.getenv("FOODCOST_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("private") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

dependencies {
    implementation(project(":presentation"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // The walk-through test that runs on an emulator in GitHub Actions.
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
