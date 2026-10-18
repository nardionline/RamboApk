plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "al.rambo.bridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "al.rambo.bridge"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
}
