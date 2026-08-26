package com.appforge.studio.io

import java.io.File

data class ProjectTechnologyInfo(
    val id: String,
    val label: String,
    val buildEngine: String,
    val buildReady: Boolean,
    val reason: String
)

object ProjectTechnologyDetector {

    private const val MAX_DEPTH = 6

    private fun files(root: File): List<File> =
        root.walkTopDown()
            .maxDepth(MAX_DEPTH)
            .filter { it.isFile }
            .take(4_000)
            .toList()

    private fun text(file: File?): String =
        if (
            file == null ||
            !file.isFile ||
            file.length() > 2L * 1024L * 1024L
        ) {
            ""
        } else {
            runCatching {
                file.readText(Charsets.UTF_8)
            }.getOrDefault("")
        }

    fun detect(root: File): ProjectTechnologyInfo {
        if (!root.exists() || !root.isDirectory) {
            return unknown("Proje klasörü bulunamadı.")
        }

        val all = files(root)
        val byName = all.groupBy { it.name.lowercase() }

        fun first(name: String): File? =
            byName[name.lowercase()]?.firstOrNull()

        fun has(name: String): Boolean =
            first(name) != null

        fun relative(file: File): String =
            file.relativeTo(root)
                .invariantSeparatorsPath
                .lowercase()

        fun hasPath(suffix: String): Boolean =
            all.any {
                relative(it).endsWith(suffix.lowercase())
            }

        fun hasExt(vararg ext: String): Boolean {
            val accepted = ext.map { it.lowercase() }.toSet()
            return all.any {
                it.extension.lowercase() in accepted
            }
        }

        val packageJson =
            text(first("package.json")).lowercase()

        val pubspec =
            text(first("pubspec.yaml")).lowercase()

        val pyproject =
            text(first("pyproject.toml")).lowercase()

        val packageJsonCompact =
            packageJson.replace(
                Regex("\\s+"),
                ""
            )

        val nextConfig =
            listOf(
                "next.config.js",
                "next.config.mjs",
                "next.config.cjs",
                "next.config.ts"
            )
                .asSequence()
                .mapNotNull { first(it) }
                .map { text(it).lowercase() }
                .firstOrNull()
                .orEmpty()

        val nextConfigCompact =
            nextConfig.replace(
                Regex("\\s+"),
                ""
            )

        val csproj =
            all.firstOrNull {
                it.extension.equals("csproj", true)
            }

        val csprojText =
            text(csproj).lowercase()

        if (
            hasPath("projectsettings/projectversion.txt") &&
            all.any {
                val p = relative(it)
                p.startsWith("assets/") ||
                    p.contains("/assets/")
            }
        ) {
            return ProjectTechnologyInfo(
                id = "unity",
                label = "Unity / C#",
                buildEngine = "unity-android",
                buildReady = false,
                reason = "ProjectSettings/ProjectVersion.txt ve Assets klasörü bulundu."
            )
        }

        if (
            has("pubspec.yaml") &&
            (
                hasPath("lib/main.dart") ||
                pubspec.contains("flutter:")
            )
        ) {
            return ProjectTechnologyInfo(
                id = "flutter",
                label = "Flutter / Dart",
                buildEngine = "flutter",
                buildReady = true,
                reason = "pubspec.yaml ve Flutter proje yapısı bulundu."
            )
        }

        if (
            (
                has("settings.gradle") ||
                has("settings.gradle.kts")
            ) &&
            hasPath("app/src/main/androidmanifest.xml")
        ) {
            val kotlin = hasExt("kt", "kts")

            return ProjectTechnologyInfo(
                id = if (kotlin) "android-kotlin" else "android-java",
                label = if (kotlin) "Android / Kotlin" else "Android / Java",
                buildEngine = "android-gradle",
                buildReady = true,
                reason = "Android Gradle projesi ve AndroidManifest.xml bulundu."
            )
        }

        if (packageJson.contains("\"expo\"")) {
            return ProjectTechnologyInfo(
                id = "expo",
                label = "Expo / React Native",
                buildEngine = "expo-android",
                buildReady = true,
                reason = "package.json içinde Expo bağımlılığı bulundu."
            )
        }

        if (packageJson.contains("\"react-native\"")) {
            return ProjectTechnologyInfo(
                id = "react-native",
                label = "React Native",
                buildEngine = "react-native-android",
                buildReady = true,
                reason = "package.json içinde react-native bulundu."
            )
        }

        if (
            csproj != null &&
            (
                csprojText.contains("<usemaui>true</usemaui>") ||
                csprojText.contains("microsoft.maui")
            )
        ) {
            val hasNet10AndroidTarget =
                csprojText.contains("net10.0-android")

            return ProjectTechnologyInfo(
                id = "dotnet-maui",
                label = ".NET MAUI / C#",
                buildEngine = "dotnet-maui-android",
                buildReady = hasNet10AndroidTarget,
                reason =
                    if (hasNet10AndroidTarget) {
                        ".NET MAUI ve net10.0-android hedefi bulundu."
                    } else {
                        ".NET MAUI bulundu. İlk canlı Android motoru net10.0-android hedefini destekliyor."
                    }
            )
        }

        if (has("manage.py")) {
            return ProjectTechnologyInfo(
                id = "python-django",
                label = "Python / Django",
                buildEngine = "python-android",
                buildReady = false,
                reason = "manage.py bulundu."
            )
        }

        if (
            (
                has("requirements.txt") ||
                has("pyproject.toml") ||
                has("setup.py")
            ) &&
            (
                all.any {
                    it.name.equals("app.py", true) ||
                    it.name.equals("main.py", true)
                } &&
                (
                    pyproject.contains("flask") ||
                    all.filter {
                        it.extension.equals("py", true)
                    }.take(30).any {
                        text(it).lowercase().contains("from flask") ||
                            text(it).lowercase().contains("import flask")
                    }
                )
            )
        ) {
            return ProjectTechnologyInfo(
                id = "python-flask",
                label = "Python / Flask",
                buildEngine = "python-android",
                buildReady = false,
                reason = "Python proje dosyaları ve Flask kullanımı bulundu."
            )
        }

        if (
            has("requirements.txt") ||
            has("pyproject.toml") ||
            has("setup.py") ||
            hasExt("py")
        ) {
            val hasPythonEntry =
                all.any {
                    it.name.equals(
                        "main.py",
                        true
                    ) ||
                    it.name.equals(
                        "app.py",
                        true
                    )
                }

            return ProjectTechnologyInfo(
                id = "python",
                label = "Python",
                buildEngine = "python-android",
                buildReady = hasPythonEntry,
                reason =
                    if (
                        hasPythonEntry
                    ) {
                        "Python kaynakları ve main.py/app.py giriş dosyası bulundu."
                    } else {
                        "Python projesi bulundu ancak main.py veya app.py giriş dosyası yok."
                    }
            )
        }

        if (packageJson.contains("\"next\"")) {
            val hasStaticExport =
                nextConfigCompact.contains(
                    "output:\"export\""
                ) ||
                    nextConfigCompact.contains(
                        "output:'export'"
                    ) ||
                    packageJsonCompact.contains(
                        "\"staticexport\":true"
                    )

            return ProjectTechnologyInfo(
                id = "nextjs",
                label = "Next.js / React",
                buildEngine = "node-web",
                buildReady = hasStaticExport,
                reason =
                    if (hasStaticExport) {
                        "Next.js static export yapılandırması bulundu."
                    } else {
                        "Next.js SSR olabilir. Android WebView build için output: 'export' gerekli."
                    }
            )
        }

        if (packageJson.contains("\"nuxt\"")) {
            val hasGenerateScript =
                packageJsonCompact.contains(
                    "\"generate\":\"nuxtgenerate"
                ) ||
                    packageJsonCompact.contains(
                        "\"generate\":\"nuxigenerate"
                    ) ||
                    packageJsonCompact.contains(
                        "\"build:static\":\"nuxtgenerate"
                    ) ||
                    packageJsonCompact.contains(
                        "\"build:static\":\"nuxigenerate"
                    )

            return ProjectTechnologyInfo(
                id = "nuxt",
                label = "Nuxt / Vue",
                buildEngine = "node-web",
                buildReady = hasGenerateScript,
                reason =
                    if (hasGenerateScript) {
                        "Nuxt static generate scripti bulundu."
                    } else {
                        "Nuxt SSR olabilir. Android WebView build için nuxt/nuxi generate scripti gerekli."
                    }
            )
        }

        if (packageJson.contains("\"@angular/core\"")) {
            return ProjectTechnologyInfo(
                id = "angular",
                label = "Angular / TypeScript",
                buildEngine = "node-web",
                buildReady = true,
                reason = "package.json içinde Angular bulundu."
            )
        }

        if (packageJson.contains("\"svelte\"")) {
            return ProjectTechnologyInfo(
                id = "svelte",
                label = "Svelte / TypeScript",
                buildEngine = "node-web",
                buildReady = true,
                reason = "package.json içinde Svelte bulundu."
            )
        }

        if (packageJson.contains("\"vue\"")) {
            return ProjectTechnologyInfo(
                id = "vue",
                label = "Vue / TypeScript-JavaScript",
                buildEngine = "node-web",
                buildReady = true,
                reason = "package.json içinde Vue bulundu."
            )
        }

        if (packageJson.contains("\"react\"")) {
            return ProjectTechnologyInfo(
                id = "react",
                label = "React / TypeScript-JavaScript",
                buildEngine = "node-web",
                buildReady = true,
                reason = "package.json içinde React bulundu."
            )
        }

        if (
            packageJson.contains("\"vite\"") ||
            has("vite.config.js") ||
            has("vite.config.ts")
        ) {
            return ProjectTechnologyInfo(
                id = "vite",
                label = "Vite / Web",
                buildEngine = "node-web",
                buildReady = true,
                reason = "Vite yapılandırması bulundu."
            )
        }

        if (
            has("composer.json") ||
            hasExt("php")
        ) {
            return ProjectTechnologyInfo(
                id = "php",
                label = "PHP",
                buildEngine = "remote-backend",
                buildReady = false,
                reason = "PHP kaynak veya composer.json bulundu."
            )
        }

        if (
            has("cmakelists.txt") ||
            hasExt("c", "cc", "cpp", "cxx", "h", "hpp")
        ) {
            val hasAppForgeNativeEntry =
                all.any {
                    it.name.lowercase() in setOf(
                        "appforge_main.c",
                        "appforge_main.cc",
                        "appforge_main.cpp",
                        "appforge_main.cxx"
                    )
                }

            return ProjectTechnologyInfo(
                id = "cpp",
                label = "C / C++",
                buildEngine = "android-ndk",
                buildReady = hasAppForgeNativeEntry,
                reason =
                    if (hasAppForgeNativeEntry) {
                        "C/C++ kaynakları ve appforge_main giriş dosyası bulundu."
                    } else {
                        "C/C++ projesi bulundu. Android build için appforge_main.c/cpp giriş kontratı gerekli."
                    }
            )
        }

        if (
            has("package.json") &&
            has("index.html")
        ) {
            val hasTypeScript =
                hasExt(
                    "ts",
                    "tsx"
                )

            return ProjectTechnologyInfo(
                id =
                    if (
                        hasTypeScript
                    ) {
                        "typescript-web"
                    } else {
                        "npm-web"
                    },
                label =
                    if (
                        hasTypeScript
                    ) {
                        "TypeScript / JavaScript Web"
                    } else {
                        "JavaScript / npm Web"
                    },
                buildEngine = "node-web",
                buildReady = true,
                reason = "package.json ve index.html bulundu."
            )
        }

        if (has("package.json")) {
            return ProjectTechnologyInfo(
                id = "nodejs",
                label = "Node.js / JavaScript-TypeScript",
                buildEngine = "node-web",
                buildReady = false,
                reason = "package.json bulundu; bunun web mi backend mi olduğu kesin değil."
            )
        }

        if (has("index.html")) {
            return ProjectTechnologyInfo(
                id = "web-static",
                label = "HTML / CSS / JavaScript",
                buildEngine = "webview-static",
                buildReady = true,
                reason = "index.html bulundu."
            )
        }

        return unknown("Bilinen proje imzası bulunamadı.")
    }

    fun unknown(
        reason: String = "Proje türü bilinmiyor."
    ): ProjectTechnologyInfo =
        ProjectTechnologyInfo(
            id = "unknown",
            label = "Bilinmeyen proje",
            buildEngine = "unknown",
            buildReady = false,
            reason = reason
        )
}
