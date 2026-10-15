pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FitTraceAndroid"

include(
    ":app",
    ":core:model",
    ":core:domain",
    ":core:network",
    ":core:data",
    ":core:designsystem",
    ":feature:main",
)
