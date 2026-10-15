plugins {
    id("fittrace.android.library")
    id("fittrace.android.compose")
}

android { namespace = "com.fittrace.feature.main" }

dependencies {
    implementation(project(":core:designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
}
