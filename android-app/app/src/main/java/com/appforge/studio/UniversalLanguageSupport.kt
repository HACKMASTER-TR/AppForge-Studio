package com.appforge.studio

import java.io.File

data class UniversalLanguageReport(
    val languages: List<String>,
    val frameworks: List<String>,
    val summary: String
)

object UniversalLanguageSupport {
    private const val MAX_FILES = 5_000
    private const val MAX_DEPTH = 20

    private val extensionLanguages =
        mapOf(
            "html" to "HTML",
            "htm" to "HTML",
            "css" to "CSS",
            "scss" to "SCSS",
            "sass" to "Sass",
            "less" to "Less",
            "js" to "JavaScript",
            "mjs" to "JavaScript",
            "cjs" to "JavaScript",
            "jsx" to "JavaScript / JSX",
            "ts" to "TypeScript",
            "tsx" to "TypeScript / TSX",
            "kt" to "Kotlin",
            "kts" to "Kotlin",
            "java" to "Java",
            "dart" to "Dart",
            "py" to "Python",
            "cs" to "C#",
            "c" to "C",
            "h" to "C / C++",
            "cc" to "C++",
            "cpp" to "C++",
            "cxx" to "C++",
            "hpp" to "C++",
            "rs" to "Rust",
            "go" to "Go",
            "rb" to "Ruby",
            "php" to "PHP",
            "swift" to "Swift",
            "m" to "Objective-C",
            "mm" to "Objective-C++",
            "lua" to "Lua",
            "sh" to "Shell",
            "bash" to "Shell",
            "zsh" to "Shell",
            "ps1" to "PowerShell",
            "vue" to "Vue SFC",
            "svelte" to "Svelte",
            "xml" to "XML",
            "gradle" to "Gradle",
            "groovy" to "Groovy",
            "sql" to "SQL",
            "r" to "R"
        )

    fun analyze(root: File): UniversalLanguageReport {
        if (!root.exists() || !root.isDirectory) {
            return UniversalLanguageReport(
                emptyList(),
                emptyList(),
                "Kaynak klasörü bulunamadı"
            )
        }

        val files =
            root.walkTopDown()
                .maxDepth(MAX_DEPTH)
                .filter { it.isFile }
                .take(MAX_FILES)
                .toList()

        val languageCounts =
            linkedMapOf<String, Int>()

        files.forEach { file ->
            extensionLanguages[
                file.extension.lowercase()
            ]?.let { language ->
                languageCounts[
                    language
                ] =
                    (
                        languageCounts[
                            language
                        ] ?: 0
                    ) + 1
            }
        }

        val names =
            files.map {
                it.name.lowercase()
            }.toSet()

        fun fileText(name: String): String =
            files
                .firstOrNull {
                    it.name.equals(
                        name,
                        true
                    )
                }
                ?.takeIf {
                    it.length() <=
                        2L *
                        1024L *
                        1024L
                }
                ?.let {
                    runCatching {
                        it.readText(
                            Charsets.UTF_8
                        )
                    }.getOrDefault("")
                }
                .orEmpty()
                .lowercase()

        val packageJson =
            fileText(
                "package.json"
            )

        val pyproject =
            fileText(
                "pyproject.toml"
            )

        val composer =
            fileText(
                "composer.json"
            )

        val frameworks =
            linkedSetOf<String>()

        if ("pubspec.yaml" in names) frameworks += "Flutter"
        if ("settings.gradle" in names || "settings.gradle.kts" in names) frameworks += "Android Gradle"
        if ("react-native" in packageJson) frameworks += "React Native"
        if ("\"expo\"" in packageJson) frameworks += "Expo"
        if ("\"react\"" in packageJson) frameworks += "React"
        if ("\"next\"" in packageJson) frameworks += "Next.js"
        if ("\"vue\"" in packageJson) frameworks += "Vue"
        if ("\"nuxt\"" in packageJson) frameworks += "Nuxt"
        if ("@angular/core" in packageJson) frameworks += "Angular"
        if ("\"svelte\"" in packageJson) frameworks += "Svelte"
        if ("\"vite\"" in packageJson) frameworks += "Vite"
        if ("@capacitor/" in packageJson) frameworks += "Capacitor"
        if ("@ionic/" in packageJson) frameworks += "Ionic"
        if ("cordova" in packageJson) frameworks += "Cordova"
        if ("electron" in packageJson) frameworks += "Electron"
        if ("@nestjs/" in packageJson) frameworks += "NestJS"
        if ("express" in packageJson) frameworks += "Express"
        if ("fastapi" in pyproject) frameworks += "FastAPI"
        if ("kivy" in pyproject) frameworks += "Kivy"
        if ("laravel/framework" in composer) frameworks += "Laravel"
        if ("cargo.toml" in names) frameworks += "Cargo"
        if ("go.mod" in names) frameworks += "Go Modules"
        if ("gemfile" in names) frameworks += "Ruby Bundler"

        val languages =
            languageCounts
                .entries
                .sortedByDescending {
                    it.value
                }
                .map {
                    it.key
                }

        val summary =
            buildString {
                if (languages.isEmpty()) {
                    append(
                        "Yazılım dili otomatik belirlenemedi"
                    )
                } else {
                    append(
                        languages
                            .take(5)
                            .joinToString(
                                " + "
                            )
                    )
                }

                if (frameworks.isNotEmpty()) {
                    append(
                        " • "
                    )
                    append(
                        frameworks
                            .take(4)
                            .joinToString(
                                ", "
                            )
                    )
                }
            }

        return UniversalLanguageReport(
            languages =
                languages,
            frameworks =
                frameworks.toList(),
            summary =
                summary
        )
    }
}
