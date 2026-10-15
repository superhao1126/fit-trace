plugins { id("fittrace.android.library") }

android { namespace = "com.fittrace.core.session" }

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
}
