plugins {
    id("com.android.application")
}

android {
    namespace = "com.agoose.xiaomiwatch5.neonface"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.agoose.xiaomiwatch5.neonface"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
