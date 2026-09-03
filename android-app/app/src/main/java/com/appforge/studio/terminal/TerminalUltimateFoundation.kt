package com.appforge.studio.terminal

import java.io.File

internal enum class TerminalUltimateMode(
    val title: String,
    val subtitle: String
) {
    EASY(
        "Kolay",
        "Hazır işlemler ve Türkçe açıklamalar"
    ),
    ADVANCED(
        "Gelişmiş",
        "Terminal, dosyalar ve görsel Git"
    ),
    LINUX(
        "Linux",
        "Rootless Debian/Ubuntu çalışma alanı"
    ),
    SERVER(
        "Sunucu",
        "SSH, bağlantılar ve deployment merkezi"
    ),
    AI(
        "AI",
        "Türkçe geliştirici ve güvenli komut hazırlama"
    )
}

internal enum class AppForgeProjectKind(
    val title: String
) {
    HTML("HTML / Web"),
    NODE("Node.js"),
    REACT("React"),
    REACT_NATIVE("React Native"),
    PYTHON("Python"),
    FLUTTER("Flutter"),
    ANDROID("Android / Gradle"),
    JAVA("Java"),
    PHP("PHP"),
    GO("Go"),
    RUST("Rust"),
    C_CPP("C / C++"),
    UNKNOWN("Genel proje")
}

internal enum class UltimateActionTarget {
    LOCAL,
    LINUX,
    SERVER,
    APPFORGE
}

internal data class UltimateAction(
    val id: String,
    val title: String,
    val description: String,
    val command: String,
    val target: UltimateActionTarget
)

internal data class ProjectDetection(
    val kind: AppForgeProjectKind,
    val markers: List<String>,
    val actions: List<UltimateAction>
)

internal data class CommandExplanation(
    val title: String,
    val description: String,
    val risk: String,
    val allowed: Boolean,
    val requiresConfirmation: Boolean
)

internal enum class ProjectHealthLevel {
    OK,
    INFO,
    WARNING
}

internal data class ProjectHealthItem(
    val level: ProjectHealthLevel,
    val title: String,
    val detail: String
)

internal object AppForgeProjectDetector {
    private const val MAX_PACKAGE_JSON_BYTES =
        512L * 1_024L

    fun detect(root: File): ProjectDetection {
        val safeRoot =
            runCatching {
                root.canonicalFile
            }.getOrElse {
                root.absoluteFile
            }

        val files =
            safeRoot
                .listFiles()
                .orEmpty()

        val names =
            files
                .map {
                    it.name
                }
                .toSet()

        val packageJson =
            File(
                safeRoot,
                "package.json"
            )

        val packageText =
            if (
                packageJson.isFile &&
                packageJson.length() <=
                    MAX_PACKAGE_JSON_BYTES
            ) {
                runCatching {
                    packageJson.readText(
                        Charsets.UTF_8
                    )
                }.getOrDefault("")
            } else {
                ""
            }

        val detection =
            when {
                "pubspec.yaml" in names -> {
                    AppForgeProjectKind.FLUTTER to
                        listOf(
                            "pubspec.yaml"
                        )
                }

                "settings.gradle" in names ||
                    "settings.gradle.kts" in names ||
                    (
                        "gradlew" in names &&
                            File(
                                safeRoot,
                                "app"
                            ).isDirectory
                        ) -> {
                    AppForgeProjectKind.ANDROID to
                        listOf(
                            names.firstOrNull {
                                it == "settings.gradle" ||
                                    it == "settings.gradle.kts"
                            } ?: "gradlew"
                        )
                }

                packageJson.isFile &&
                    (
                        packageText.contains(
                            "\"react-native\"",
                            ignoreCase = true
                        ) ||
                            packageText.contains(
                                "\"@react-native/",
                                ignoreCase = true
                            )
                        ) -> {
                    AppForgeProjectKind.REACT_NATIVE to
                        listOf(
                            "package.json",
                            "react-native"
                        )
                }

                packageJson.isFile &&
                    packageText.contains(
                        "\"react\"",
                        ignoreCase = true
                    ) -> {
                    AppForgeProjectKind.REACT to
                        listOf(
                            "package.json",
                            "react"
                        )
                }

                packageJson.isFile -> {
                    AppForgeProjectKind.NODE to
                        listOf(
                            "package.json"
                        )
                }

                "pyproject.toml" in names ||
                    "requirements.txt" in names ||
                    files.any {
                        it.isFile &&
                            it.extension.equals(
                                "py",
                                ignoreCase = true
                            )
                    } -> {
                    AppForgeProjectKind.PYTHON to
                        buildList {
                            if (
                                "pyproject.toml" in names
                            ) {
                                add("pyproject.toml")
                            }

                            if (
                                "requirements.txt" in names
                            ) {
                                add("requirements.txt")
                            }

                            if (isEmpty()) {
                                add("*.py")
                            }
                        }
                }

                "Cargo.toml" in names -> {
                    AppForgeProjectKind.RUST to
                        listOf(
                            "Cargo.toml"
                        )
                }

                "go.mod" in names -> {
                    AppForgeProjectKind.GO to
                        listOf(
                            "go.mod"
                        )
                }

                "composer.json" in names ||
                    files.any {
                        it.isFile &&
                            it.extension.equals(
                                "php",
                                ignoreCase = true
                            )
                    } -> {
                    AppForgeProjectKind.PHP to
                        listOf(
                            if (
                                "composer.json" in names
                            ) {
                                "composer.json"
                            } else {
                                "*.php"
                            }
                        )
                }

                "pom.xml" in names -> {
                    AppForgeProjectKind.JAVA to
                        listOf(
                            "pom.xml"
                        )
                }

                "CMakeLists.txt" in names ||
                    files.any {
                        it.isFile &&
                            it.extension.lowercase() in
                            setOf(
                                "c",
                                "cc",
                                "cpp",
                                "cxx",
                                "h",
                                "hpp"
                            )
                    } -> {
                    AppForgeProjectKind.C_CPP to
                        listOf(
                            if (
                                "CMakeLists.txt" in names
                            ) {
                                "CMakeLists.txt"
                            } else {
                                "C/C++ kaynakları"
                            }
                        )
                }

                "index.html" in names -> {
                    AppForgeProjectKind.HTML to
                        listOf(
                            "index.html"
                        )
                }

                else -> {
                    AppForgeProjectKind.UNKNOWN to
                        emptyList()
                }
            }

        return ProjectDetection(
            kind = detection.first,
            markers = detection.second,
            actions =
                actionsFor(
                    detection.first,
                    names
                )
        )
    }

    private fun actionsFor(
        kind: AppForgeProjectKind,
        names: Set<String>
    ): List<UltimateAction> =
        when (kind) {
            AppForgeProjectKind.NODE,
            AppForgeProjectKind.REACT ->
                listOf(
                    action(
                        "deps",
                        "Bağımlılıkları kur",
                        "package.json bağımlılıklarını kurar.",
                        "npm install",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "test",
                        "Projeyi test et",
                        "npm test komutunu çalıştırır.",
                        "npm test",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "build",
                        "Projeyi derle",
                        "Tanımlı build scriptini çalıştırır.",
                        "npm run build",
                        UltimateActionTarget.LINUX
                    )
                )

            AppForgeProjectKind.REACT_NATIVE ->
                listOf(
                    action(
                        "deps",
                        "Bağımlılıkları kur",
                        "React Native bağımlılıklarını kurar.",
                        "npm install",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "test",
                        "Testleri çalıştır",
                        "JavaScript testlerini çalıştırır.",
                        "npm test",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "android",
                        "Android build",
                        "Android Gradle debug buildini hazırlar.",
                        "cd android && ./gradlew assembleDebug",
                        UltimateActionTarget.LINUX
                    )
                )

            AppForgeProjectKind.PYTHON ->
                buildList {
                    if (
                        "requirements.txt" in names
                    ) {
                        add(
                            action(
                                "deps",
                                "Bağımlılıkları kur",
                                "requirements.txt içindeki paketleri kurar.",
                                "python3 -m pip install -r requirements.txt",
                                UltimateActionTarget.LINUX
                            )
                        )
                    }

                    add(
                        action(
                            "test",
                            "Python testlerini çalıştır",
                            "pytest ile proje testlerini çalıştırır.",
                            "python3 -m pytest",
                            UltimateActionTarget.LINUX
                        )
                    )
                }

            AppForgeProjectKind.FLUTTER ->
                listOf(
                    action(
                        "deps",
                        "Flutter paketlerini al",
                        "pubspec.yaml bağımlılıklarını indirir.",
                        "flutter pub get",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "test",
                        "Flutter testleri",
                        "Flutter test paketini çalıştırır.",
                        "flutter test",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "build",
                        "Flutter APK derle",
                        "Debug APK üretir.",
                        "flutter build apk --debug",
                        UltimateActionTarget.LINUX
                    )
                )

            AppForgeProjectKind.ANDROID ->
                listOf(
                    action(
                        "test",
                        "Android testleri",
                        "Gradle birim testlerini çalıştırır.",
                        "./gradlew test",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "build",
                        "Debug APK derle",
                        "Gradle ile debug APK üretir.",
                        "./gradlew assembleDebug",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "appforge-build",
                        "AppForge Builder",
                        "AppForge APK/AAB ekranını açar.",
                        "appforge build",
                        UltimateActionTarget.APPFORGE
                    )
                )

            AppForgeProjectKind.JAVA ->
                listOf(
                    action(
                        "test",
                        "Java testleri",
                        "Maven testlerini çalıştırır.",
                        "mvn test",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "build",
                        "Java paketi oluştur",
                        "Maven paketleme işlemini çalıştırır.",
                        "mvn package",
                        UltimateActionTarget.LINUX
                    )
                )

            AppForgeProjectKind.PHP ->
                listOf(
                    action(
                        "deps",
                        "Composer bağımlılıkları",
                        "composer.json bağımlılıklarını kurar.",
                        "composer install",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "serve",
                        "Yerel PHP sunucusu",
                        "Projeyi 127.0.0.1:8000 üzerinde başlatır.",
                        "php -S 127.0.0.1:8000",
                        UltimateActionTarget.LINUX
                    )
                )

            AppForgeProjectKind.GO ->
                listOf(
                    action(
                        "deps",
                        "Go modüllerini indir",
                        "go.mod bağımlılıklarını indirir.",
                        "go mod download",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "test",
                        "Go testleri",
                        "Tüm Go paketlerini test eder.",
                        "go test ./...",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "build",
                        "Go build",
                        "Tüm Go paketlerini derler.",
                        "go build ./...",
                        UltimateActionTarget.LINUX
                    )
                )

            AppForgeProjectKind.RUST ->
                listOf(
                    action(
                        "deps",
                        "Cargo bağımlılıkları",
                        "Rust bağımlılıklarını önceden indirir.",
                        "cargo fetch",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "test",
                        "Rust testleri",
                        "Cargo testlerini çalıştırır.",
                        "cargo test",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "build",
                        "Rust build",
                        "Debug Rust derlemesini çalıştırır.",
                        "cargo build",
                        UltimateActionTarget.LINUX
                    )
                )

            AppForgeProjectKind.C_CPP ->
                listOf(
                    action(
                        "configure",
                        "CMake hazırla",
                        "Build klasörünü yapılandırır.",
                        "cmake -S . -B build",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "build",
                        "C/C++ build",
                        "CMake build klasörünü derler.",
                        "cmake --build build",
                        UltimateActionTarget.LINUX
                    ),
                    action(
                        "test",
                        "C/C++ testleri",
                        "CTest testlerini çalıştırır.",
                        "ctest --test-dir build",
                        UltimateActionTarget.LINUX
                    )
                )

            AppForgeProjectKind.HTML ->
                listOf(
                    action(
                        "serve",
                        "Önizleme sunucusu",
                        "Statik projeyi yerel HTTP sunucusunda açar.",
                        "python3 -m http.server 8080",
                        UltimateActionTarget.LINUX
                    )
                )

            AppForgeProjectKind.UNKNOWN ->
                listOf(
                    action(
                        "status",
                        "Proje durumunu göster",
                        "Çalışma alanı ve AppForge proje bilgisini gösterir.",
                        "appforge status",
                        UltimateActionTarget.LOCAL
                    )
                )
        }

    private fun action(
        id: String,
        title: String,
        description: String,
        command: String,
        target: UltimateActionTarget
    ) =
        UltimateAction(
            id = id,
            title = title,
            description = description,
            command = command,
            target = target
        )
}

internal object TerminalCommandAdvisor {
    fun explain(
        command: String
    ): CommandExplanation {
        val clean =
            command.trim()

        val review =
            TerminalCommandPolicy.review(
                clean
            )

        val known =
            when {
                clean == "npm install" ->
                    "Node.js proje bağımlılıklarını indirip kurar."

                clean == "npm test" ->
                    "Projenin tanımlı test komutunu çalıştırır."

                clean == "npm run build" ->
                    "Projenin build scriptini çalıştırır."

                clean == "git status" ->
                    "Git çalışma alanındaki değişiklikleri yalnızca görüntüler."

                clean == "git pull" ->
                    "Uzak depodaki yeni commitleri mevcut dala getirir."

                clean == "git push" ->
                    "Yerel commitleri yapılandırılmış uzak depoya gönderir."

                clean.startsWith("./gradlew") ->
                    "Gradle görevini seçili proje içinde çalıştırır."

                clean.startsWith("python3 -m pip install") ->
                    "Python bağımlılıklarını seçili ortama kurar."

                clean.startsWith("apt ") ||
                    clean.startsWith("apt-get ") ->
                    "Linux ortamındaki apt paket yöneticisini çalıştırır."

                clean.startsWith("cargo ") ->
                    "Rust Cargo aracını seçili proje üzerinde çalıştırır."

                clean.startsWith("go ") ->
                    "Go araç zinciri komutunu seçili proje üzerinde çalıştırır."

                clean.startsWith("flutter ") ->
                    "Flutter araç zinciri komutunu seçili proje üzerinde çalıştırır."

                else ->
                    "Komut seçili çalışma alanında çalıştırılacaktır."
            }

        val risk =
            when {
                !review.allowed ->
                    "Engellendi"

                review.requiresConfirmation ->
                    "Yüksek — ikinci onay gerekir"

                clean.startsWith("git push") ||
                    clean.startsWith("git pull") ||
                    clean.startsWith("npm install") ||
                    clean.startsWith("apt ") ||
                    clean.startsWith("apt-get ") ->
                    "Orta — dosya veya ağ durumu değişebilir"

                else ->
                    "Düşük"
            }

        return CommandExplanation(
            title =
                if (review.allowed) {
                    "Komut açıklaması"
                } else {
                    "Güvenlik engeli"
                },
            description =
                if (review.allowed) {
                    known
                } else {
                    review.message
                },
            risk = risk,
            allowed = review.allowed,
            requiresConfirmation =
                review.requiresConfirmation
        )
    }
}

internal object TerminalSecretMasker {
    private val assignment =
        Regex(
            """(?i)\b([A-Z0-9_]*(?:TOKEN|PASSWORD|PASSWD|SECRET|API_KEY|ACCESS_KEY|PRIVATE_KEY)[A-Z0-9_]*)\s*=\s*([^\s"'`]+|["'][^"']*["'])"""
        )

    private val bearer =
        Regex(
            """(?i)(Authorization\s*:\s*Bearer\s+)([A-Za-z0-9._~+/\-=]{8,})"""
        )

    private val knownTokens =
        listOf(
            Regex("""\bgh[pousr]_[A-Za-z0-9_]{16,}\b"""),
            Regex("""\bgithub_pat_[A-Za-z0-9_]{16,}\b"""),
            Regex("""\bglpat-[A-Za-z0-9_-]{12,}\b"""),
            Regex("""\bxox[baprs]-[A-Za-z0-9-]{12,}\b"""),
            Regex("""\bsk-[A-Za-z0-9_-]{16,}\b""")
        )

    fun redact(value: String): String {
        var result =
            assignment.replace(
                value
            ) { match ->
                "${match.groupValues[1]}=••••••"
            }

        result =
            bearer.replace(
                result
            ) { match ->
                "${match.groupValues[1]}••••••"
            }

        knownTokens.forEach { pattern ->
            result =
                pattern.replace(
                    result,
                    "••••••"
                )
        }

        return result
    }
}

internal object ProjectHealthInspector {
    fun inspect(
        root: File,
        detection: ProjectDetection =
            AppForgeProjectDetector.detect(
                root
            )
    ): List<ProjectHealthItem> {
        val items =
            mutableListOf<ProjectHealthItem>()

        if (
            detection.kind ==
            AppForgeProjectKind.UNKNOWN
        ) {
            items +=
                ProjectHealthItem(
                    ProjectHealthLevel.INFO,
                    "Proje türü belirlenemedi",
                    "Dosyaları açabilir, Terminal veya AI ile çalışma alanını inceleyebilirsin."
                )
        } else {
            items +=
                ProjectHealthItem(
                    ProjectHealthLevel.OK,
                    "Proje algılandı",
                    "${detection.kind.title}: ${detection.markers.joinToString().ifBlank { "işaret bulundu" }}"
                )
        }

        val git =
            File(
                root,
                ".git"
            )

        items +=
            if (git.exists()) {
                ProjectHealthItem(
                    ProjectHealthLevel.OK,
                    "Git deposu hazır",
                    "Görsel Git ekranı bu çalışma alanını kullanabilir."
                )
            } else {
                ProjectHealthItem(
                    ProjectHealthLevel.INFO,
                    "Git deposu yok",
                    "İstersen Git sekmesinden depo başlatabilir veya clone yapabilirsin."
                )
            }

        val env =
            File(
                root,
                ".env"
            )

        if (env.isFile) {
            items +=
                ProjectHealthItem(
                    ProjectHealthLevel.WARNING,
                    ".env dosyası bulundu",
                    "Terminal çıktılarında gizli değerler otomatik maskelenir; .env dosyasını commit etmeden önce kontrol et."
                )
        }

        if (
            detection.kind in
            setOf(
                AppForgeProjectKind.NODE,
                AppForgeProjectKind.REACT,
                AppForgeProjectKind.REACT_NATIVE
            ) &&
            !File(
                root,
                "node_modules"
            ).isDirectory
        ) {
            items +=
                ProjectHealthItem(
                    ProjectHealthLevel.INFO,
                    "Node bağımlılıkları eksik olabilir",
                    "Linux ortamı hazır olduğunda Bağımlılıkları kur işlemi npm install çalıştıracak."
                )
        }

        return items
    }
}
