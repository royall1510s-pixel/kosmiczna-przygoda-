plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kosmicznaprzygoda.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kosmicznaprzygoda.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 300
        versionName = "3.00"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
} 
