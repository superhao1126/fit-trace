plugins { id("fittrace.jvm.library") }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.retrofit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
