plugins {
    id("com.android.application")
}

android {
    namespace = "com.enginegarage.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.enginegarage.game"
        minSdk = 21
        targetSdk = 35
        versionCode = 11
        versionName = "3.8"
    }

    // Ключ подписи передаётся через переменные окружения (в GitHub Actions).
    // Без них release подписывается отладочным ключом.
    val ksPath = System.getenv("SIGNING_KEYSTORE")
    signingConfigs {
        if (ksPath != null) create("shared") {
            storeFile = file(ksPath)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "enginegarage"
            keyPassword = System.getenv("SIGNING_STORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("shared") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
