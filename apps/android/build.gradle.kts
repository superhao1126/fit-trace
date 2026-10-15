plugins {
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

allprojects { dependencyLocking { lockAllConfigurations() } }

// Android 工程的验证入口，独立于 pnpm/H5。
tasks.register("verify") {
    group = "verification"
    description = "Run JVM tests, Android lint and build the development APK."
    dependsOn(
        "spotlessCheck",
        "checkModuleBoundaries",
        ":core:network:test",
        ":core:data:test",
        ":core:model:lint",
        ":core:domain:lint",
        ":core:network:lint",
        ":core:data:lint",
        ":app:testDevDebugUnitTest",
        ":app:lintDevDebug",
        ":app:assembleDevDebug",
    )
}

// 在 CI 中阻止 feature -> data/network、domain -> Android 等反向依赖。
tasks.register("checkModuleBoundaries") {
    group = "verification"
    doLast {
        val allowed =
            mapOf(
                ":app" to
                    setOf(
                        ":core:domain",
                        ":core:data",
                        ":core:network",
                        ":core:designsystem",
                        ":feature:main",
                    ),
                ":feature:main" to setOf(":core:domain", ":core:model", ":core:designsystem"),
                ":core:data" to setOf(":core:domain", ":core:model", ":core:network"),
                ":core:domain" to setOf(":core:model"),
                ":core:network" to emptySet(),
                ":core:model" to emptySet(),
                ":core:designsystem" to emptySet(),
            )
        subprojects
            .filter { it.buildFile.exists() }
            .forEach { module ->
                val dependencies =
                    module.configurations
                        .flatMap { configuration ->
                            configuration.dependencies.withType<ProjectDependency>().map { it.path }
                        }
                        .filter { it != module.path }
                        .toSet()
                check(dependencies.all { it in allowed.getValue(module.path) }) {
                    "Illegal module dependencies: ${module.path} -> ${dependencies - allowed.getValue(module.path)}"
                }
            }
    }
}

spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**", "**/bin/**", "**/.gradle/**")
        ktfmt("0.58").kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/bin/**", "**/.gradle/**")
        ktfmt("0.58").kotlinlangStyle()
    }
}
