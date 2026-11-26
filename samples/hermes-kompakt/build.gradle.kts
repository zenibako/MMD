plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.mudita.mmd.samples.hermes"
    // androidx.activity 1.10.x (pulled in via mmd-core) requires compiling against API 35.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mudita.mmd.samples.hermes"
        // MuditaOS K is based on AOSP 12 (API 31); keep a little headroom below it.
        minSdk = 28
        targetSdk = properties["library.targetSdk"].toString().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":mmd-core"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
