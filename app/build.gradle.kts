plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI supplies the run number, so every build gets a higher versionCode than the last.
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.jondude1.summonersrpg"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jondude1.summonersrpg"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    // One permanent key, committed on purpose: this is a sideloaded app, and Android
    // refuses to update an app that was signed with a different key.
    signingConfigs {
        create("summoners") {
            storeFile = rootProject.file("keystore/summoners.jks")
            storePassword = "summoners"
            keyAlias = "summoners"
            keyPassword = "summoners"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("summoners")
            isMinifyEnabled = false
        }
        debug {
            signingConfig = signingConfigs.getByName("summoners")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
