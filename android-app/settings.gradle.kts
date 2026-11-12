pluginManagement {
    repositories {
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
