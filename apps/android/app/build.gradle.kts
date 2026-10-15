plugins {
    id("fittrace.android.application")
    id("fittrace.android.compose")
}

android {
    namespace = "com.fittrace.android"
    defaultConfig {
        applicationId = "com.fittrace.android"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures.buildConfig = true
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000/api/v1/\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"https://fittrace.idoit.icu/api/v1/\"")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        if (variant.productFlavors.any { it.second == "dev" }) variant.enable = false
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:main"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
}
