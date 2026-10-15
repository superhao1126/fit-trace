plugins {
    id("fittrace.android.library")
    id("fittrace.android.compose")
}

android { namespace = "com.fittrace.core.designsystem" }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
}
