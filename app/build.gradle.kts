plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val stableKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
android {
    namespace = "com.frankdev.xxxcorevpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.frankdev.xxxcorevpn"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
signingConfigs {
    if (!stableKeystorePath.isNullOrBlank()) {
        create("stableDebug") {
            storeFile = file(stableKeystorePath)
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: ""
        }
    }
}
buildTypes {   
    getByName("debug") {
        if (!stableKeystorePath.isNullOrBlank()) {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
    }
}

splits {
    abi {
        isEnable = true
        reset()
        include("arm64-v8a", "armeabi-v7a", "x86_64")
        isUniversalApk = false
    }
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
    implementation(files("libs/ThroneCore.aar"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
}
