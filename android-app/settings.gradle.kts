pluginManagement {
    repositories {
        // R23: external Kotlin 2.4.10 BOM may be supplied only by CI, after
        // a pinned SHA-256 check in a disposable GitHub runner directory.
        val appforgeCIKotlinBomRepo = providers.gradleProperty("appforgeCIKotlinBomRepo").orNull
        if (appforgeCIKotlinBomRepo != null) {
            require(System.getenv("GITHUB_ACTIONS") == "true") { "CI-only Kotlin BOM repository" }
            val runnerTemp = java.io.File(requireNotNull(System.getenv("RUNNER_TEMP"))).canonicalFile.toPath()
            val bomRepo = java.io.File(appforgeCIKotlinBomRepo).canonicalFile.toPath()
            require(bomRepo.startsWith(runnerTemp) && bomRepo != runnerTemp) {
                "BOM repository must be under CI runner temporary storage"
            }
            maven {
                name = "appforgeCiPinnedKotlinGradleBom"
                url = uri(bomRepo.toFile())
                content {
                    includeModule("org.jetbrains.kotlin", "kotlin-gradle-plugins-bom")
                }
            }
        }
        // R32 CI only: plugin classpath dependencies missing on
        // the runner's default Maven routes. Fixed coordinates only.
        if (providers.gradleProperty("appforgeCIMavenGcs").orNull == "true") {
            require(
                System.getenv("GITHUB_ACTIONS") == "true" &&
                System.getenv("GITHUB_REPOSITORY") ==
                    "HACKMASTER-TR/AppForge-Studio"
            ) {
                "R32 Kotlin plugin mirror requires trusted GitHub CI"
            }
            maven {
                name = "appforgeR32CiKotlinPluginMirror"
                url = uri(
                    "https://maven-central.storage-download.googleapis.com/maven2/"
                )
                content {
                    includeVersion("org.jetbrains.kotlin", "kotlin-gradle-plugin-api", "2.4.10")
                    includeVersion("org.jetbrains.kotlin", "fus-statistics-gradle-plugin", "2.4.10")
                    includeVersion("org.jetbrains.kotlin", "kotlin-gradle-plugin-idea", "2.4.10")
                    includeVersion("org.jetbrains.kotlin", "kotlin-gradle-plugin-idea-proto", "2.4.10")
                    includeVersion("org.jetbrains.kotlin", "kotlin-klib-commonizer-api", "2.4.10")
                    includeVersion("org.jetbrains.kotlin", "kotlin-build-statistics", "2.4.10")
                    includeVersion("org.jetbrains.kotlin", "kotlin-util-klib-metadata", "2.4.10")
                    includeVersion("org.jetbrains.kotlin", "kotlin-compiler-runner", "2.4.10")
                    includeVersion("org.jetbrains.kotlin", "kotlin-gradle-plugins-bom", "2.4.10")
                }
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // CI-only Maven directory populated only after verifying JetBrains' SHA-256.
        // No production repository or dependency behavior is changed when absent.
        val appforgePinnedKotlinRepo = providers.gradleProperty("appforgePinnedKotlinCompilerRepo").orNull
        if (appforgePinnedKotlinRepo != null) {
            maven {
                name = "appforgeVerifiedKotlinCompiler"
                url = uri(appforgePinnedKotlinRepo)
                content {
                    includeModule("org.jetbrains.kotlin", "kotlin-compiler-embeddable")
                }
            }
        }
        // Opt-in CI mirror only: official Google Cloud Storage mirror of Maven Central.
        // Restricted to two groups that were unavailable in GitHub Actions.
        // This flag is NOT passed in device builds, Play, or regular Gradle use.
        if (providers.gradleProperty("appforgeCIMavenGcs").orNull == "true") {
            maven {
                name = "appforgeCIOnlyGcsMavenCentral"
                url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
                content {
                    includeGroup("com.google.zxing")
                    includeGroup("org.jetbrains.kotlinx")
                }
            }
        }
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
rootProject.name = "AppForgeStudio"
include(":app")
include(":html-apk-template")
include(":termux-terminal-emulator")
include(":termux-terminal-view")
