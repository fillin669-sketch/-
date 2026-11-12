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
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    sourceSets {
        getByName("main").jniLibs.srcDirs("libs")
    }
}

dependencies {
    implementation(project(":core"))
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
