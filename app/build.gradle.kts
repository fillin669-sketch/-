plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val gdxVersion: String by project
val natives: Configuration by configurations.creating

android {
    namespace = "com.seatrader.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.seatrader.game"
        minSdk = 21
        targetSdk = 35
        versionCode = 47
        versionName = "10.8"
    }

    // Подпись: ключ передаётся через переменные окружения (в GitHub Actions — из кеша или секретов).
    // Без них release подписывается отладочным ключом.
    val ksPath = System.getenv("SIGNING_KEYSTORE")
    signingConfigs {
        if (ksPath != null) create("shared") {
            storeFile = file(ksPath)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "seawolves"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: System.getenv("SIGNING_STORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("shared") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        // стандартная библиотека Java 8+ для старых Android (5.0–7.1)
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    sourceSets {
        getByName("main").jniLibs.srcDirs("libs")
    }
}

dependencies {
    implementation(project(":core"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
    }
}

// Распаковываем нативные библиотеки libGDX (.so) в app/libs/<abi>
val copyAndroidNatives by tasks.registering {
    doFirst {
        natives.files.forEach { jar ->
            val abi = listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86").firstOrNull { jar.name.endsWith("natives-$it.jar") }
            if (abi != null) {
                val outDir = file("libs/$abi")
                outDir.mkdirs()
                copy {
                    from(zipTree(jar))
                    into(outDir)
                    include("*.so")
                }
            }
        }
    }
}
tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
