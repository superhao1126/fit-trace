import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        targetSdk = 36
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        lintConfig = rootProject.file("lint.xml")
        warningsAsErrors = true
        checkDependencies = true
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
