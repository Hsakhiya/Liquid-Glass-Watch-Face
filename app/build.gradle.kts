plugins {
    alias(libs.plugins.android.application)
//    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.myface"
    compileSdk = 36
    // FIX 1: compileSdk expects a simple integer, not a nested block

    defaultConfig {
        applicationId = "com.example.myface"
        // FIX 2: Adjusted to stable Wear OS API levels
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // FIX 3: Added compileOptions to match your Kotlin JVM target
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

//    kotlinOptions {
//        jvmTarget = "11"
//    }
}

dependencies {
    // Core Wear OS Watch Face libraries
    implementation("androidx.wear.watchface:watchface:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")

    // Coroutines for smooth rendering
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.core:core-ktx:1.13.1")

    // UI Library to satisfy the default generated themes
    implementation("com.google.android.material:material:1.11.0")
}