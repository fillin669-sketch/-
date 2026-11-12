// Плагины подключаются здесь один раз для всех модулей.
// Плагин Android не нужен для десктопной проверки (-Pdesktop).
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        if (!project.hasProperty("desktop")) classpath("com.android.tools.build:gradle:8.7.3")
    }
}
