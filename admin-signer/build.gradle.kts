
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val stableKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val stableKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val stableKeyAlias = System.getenv("ANDROID_KEY_ALIAS")

android {
    namespace = "com.frankdev.cloudflarexuipanel"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.frankdev.cloudflarexuipanel"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        if (!stableKeystorePath.isNullOrBlank()) {
            create("frankdev") {
                storeFile = file(stableKeystorePath)
                storePassword = stableKeystorePassword
                keyAlias = stableKeyAlias
                keyPassword = stableKeystorePassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (!stableKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("frankdev")
            }
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
