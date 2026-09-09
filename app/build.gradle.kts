plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android { namespace = "com.kosmicznaprzygoda.app"; compileSdk = 35
    defaultConfig { applicationId = "com.kosmicznaprzygoda.app"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "2.00" }
}
