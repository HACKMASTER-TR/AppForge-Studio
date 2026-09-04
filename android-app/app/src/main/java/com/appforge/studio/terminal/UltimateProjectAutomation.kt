package com.appforge.studio.terminal

import android.content.Context
import java.io.File
import java.util.UUID

internal enum class ProjectAutomationStepKind {
    INSTALL,
    TEST,
    BUILD
}

internal data class ProjectAutomationStep(
    val id: String,
    val kind: ProjectAutomationStepKind,
    val title: String,
    val description: String,
    val command: String
)

internal data class UltimateProjectAutomationPlan(
    val projectKind: AppForgeProjectKind,
    val recommendedToolchains: Set<LinuxToolchainId>,
    val steps: List<ProjectAutomationStep>,
    val deployHints: List<DeploymentProvider>,
    val notes: List<String>
)

internal object UltimateProjectAutomationPlanner {
    fun plan(
        workspace: File,
        projectKind: AppForgeProjectKind
    ): UltimateProjectAutomationPlan {
        val root =
            workspace.canonicalFile

        val toolchains =
            linkedSetOf(
                LinuxToolchainId.BASE
            ).apply {
                when (projectKind) {
                    AppForgeProjectKind.NODE,
                    AppForgeProjectKind.REACT,
                    AppForgeProjectKind.REACT_NATIVE ->
                        add(
                            LinuxToolchainId.NODE
                        )

                    AppForgeProjectKind.PYTHON ->
                        add(
                            LinuxToolchainId.PYTHON
                        )

                    AppForgeProjectKind.ANDROID,
                    AppForgeProjectKind.JAVA ->
                        add(
                            LinuxToolchainId.JAVA
                        )

                    AppForgeProjectKind.PHP ->
                        add(
                            LinuxToolchainId.PHP
                        )

                    AppForgeProjectKind.GO ->
                        add(
                            LinuxToolchainId.GO
                        )

                    AppForgeProjectKind.RUST ->
                        add(
                            LinuxToolchainId.RUST
                        )

                    AppForgeProjectKind.C_CPP ->
                        add(
                            LinuxToolchainId.C_CPP
                        )

                    AppForgeProjectKind.HTML,
                    AppForgeProjectKind.FLUTTER,
                    AppForgeProjectKind.UNKNOWN ->
                        Unit
                }
            }

        val steps =
            lifecycleSteps(
                root,
                projectKind
            )

        val deployHints =
            detectDeployTargets(
                root
            )

        val notes =
            buildList {
                if (
                    projectKind ==
                    AppForgeProjectKind.FLUTTER
                ) {
                    add(
                        "Flutter SDK, apt paket mağazasından otomatik kurulmaz. Doğrulanmış SDK dağıtımı ayrı bir kaynak olarak eklenmelidir."
                    )
                }

                if (
                    projectKind ==
                    AppForgeProjectKind.ANDROID &&
                    !File(root, "gradlew").isFile
                ) {
                    add(
                        "Android projesinde gradlew bulunamadı. Sistem Gradle kurulumuna sessizce geçilmez."
                    )
                }

                if (
                    deployHints.isEmpty()
                ) {
                    add(
                        "Sağlayıcı yapılandırma dosyası algılanmadı; Deployment Merkezi'nde hedef elle seçilebilir."
                    )
                }
            }

        return UltimateProjectAutomationPlan(
            projectKind =
                projectKind,
            recommendedToolchains =
                toolchains,
            steps =
                steps,
            deployHints =
                deployHints,
            notes =
                notes
        )
    }

    fun runtimeProbeCommand(): String =
        "for c in git python3 pip3 node npm java javac php composer go rustc cargo gcc g++ cmake make; do " +
            "if command -v \"\$c\" >/dev/null 2>&1; then printf '%s\\tOK\\t' \"\$c\"; command -v \"\$c\"; " +
            "else printf '%s\\tYOK\\n' \"\$c\"; fi; done"

    private fun lifecycleSteps(
        root: File,
        kind: AppForgeProjectKind
    ): List<ProjectAutomationStep> =
        when (kind) {
            AppForgeProjectKind.HTML ->
                listOf(
                    step(
                        ProjectAutomationStepKind.TEST,
                        "HTML girişini doğrula",
                        "index.html dosyasının bulunduğunu kontrol eder.",
                        "test -f index.html"
                    )
                )

            AppForgeProjectKind.NODE,
            AppForgeProjectKind.REACT ->
                nodeSteps(root)

            AppForgeProjectKind.REACT_NATIVE ->
                buildList {
                    addAll(
                        nodeSteps(root)
                    )

                    if (
                        File(
                            root,
                            "android/gradlew"
                        ).isFile
                    ) {
                        add(
                            step(
                                ProjectAutomationStepKind.BUILD,
                                "Android Debug APK",
                                "React Native Android Gradle wrapper ile debug APK oluşturur.",
                                "cd android && ./gradlew assembleDebug"
                            )
                        )
                    }
                }

            AppForgeProjectKind.PYTHON ->
                buildList {
                    when {
                        File(
                            root,
                            "requirements.txt"
                        ).isFile ->
                            add(
                                step(
                                    ProjectAutomationStepKind.INSTALL,
                                    "Python bağımlılıkları",
                                    "requirements.txt içindeki paketleri kurar.",
                                    "python3 -m pip install -r requirements.txt"
                                )
                            )

                        File(
                            root,
                            "pyproject.toml"
                        ).isFile ->
                            add(
                                step(
                                    ProjectAutomationStepKind.INSTALL,
                                    "Python projesini kur",
                                    "pyproject.toml tabanlı projeyi pip ile kurar.",
                                    "python3 -m pip install ."
                                )
                            )
                    }

                    add(
                        step(
                            ProjectAutomationStepKind.TEST,
                            "Python sözdizimi kontrolü",
                            "Kaynakları bytecode derleyerek temel sözdizimi hatalarını yakalar.",
                            "python3 -m compileall -q ."
                        )
                    )
                }

            AppForgeProjectKind.FLUTTER ->
                listOf(
                    step(
                        ProjectAutomationStepKind.INSTALL,
                        "Flutter paketleri",
                        "pubspec.yaml bağımlılıklarını getirir.",
                        "flutter pub get"
                    ),
                    step(
                        ProjectAutomationStepKind.TEST,
                        "Flutter test",
                        "Flutter test paketini çalıştırır.",
                        "flutter test"
                    ),
                    step(
                        ProjectAutomationStepKind.BUILD,
                        "Flutter APK",
                        "Flutter Android APK çıktısı oluşturur.",
                        "flutter build apk"
                    )
                )

            AppForgeProjectKind.ANDROID ->
                if (
                    File(
                        root,
                        "gradlew"
                    ).isFile
                ) {
                    listOf(
                        step(
                            ProjectAutomationStepKind.TEST,
                            "Gradle test",
                            "Projenin kendi Gradle wrapper'ı ile testleri çalıştırır.",
                            "./gradlew test"
                        ),
                        step(
                            ProjectAutomationStepKind.BUILD,
                            "Android Debug",
                            "Projenin kendi Gradle wrapper'ı ile debug derleme alır.",
                            "./gradlew assembleDebug"
                        )
                    )
                } else {
                    emptyList()
                }

            AppForgeProjectKind.JAVA ->
                javaSteps(root)

            AppForgeProjectKind.PHP ->
                buildList {
                    if (
                        File(
                            root,
                            "composer.json"
                        ).isFile
                    ) {
                        add(
                            step(
                                ProjectAutomationStepKind.INSTALL,
                                "Composer bağımlılıkları",
                                "composer.json paketlerini etkileşimsiz kurar.",
                                "composer install --no-interaction"
                            )
                        )
                    }

                    add(
                        step(
                            ProjectAutomationStepKind.TEST,
                            "PHP sözdizimi",
                            "Çalışma alanındaki PHP dosyalarını php -l ile denetler.",
                            "find . -type f -name '*.php' -print0 | xargs -0 -r -n1 php -l"
                        )
                    )
                }

            AppForgeProjectKind.GO ->
                listOf(
                    step(
                        ProjectAutomationStepKind.INSTALL,
                        "Go modülleri",
                        "go.mod bağımlılıklarını indirir.",
                        "go mod download"
                    ),
                    step(
                        ProjectAutomationStepKind.TEST,
                        "Go test",
                        "Tüm Go paket testlerini çalıştırır.",
                        "go test ./..."
                    ),
                    step(
                        ProjectAutomationStepKind.BUILD,
                        "Go build",
                        "Tüm Go paketlerini derler.",
                        "go build ./..."
                    )
                )

            AppForgeProjectKind.RUST ->
                listOf(
                    step(
                        ProjectAutomationStepKind.INSTALL,
                        "Cargo fetch",
                        "Cargo bağımlılıklarını önceden getirir.",
                        "cargo fetch"
                    ),
                    step(
                        ProjectAutomationStepKind.TEST,
                        "Cargo test",
                        "Rust testlerini çalıştırır.",
                        "cargo test"
                    ),
                    step(
                        ProjectAutomationStepKind.BUILD,
                        "Cargo build",
                        "Rust projesini derler.",
                        "cargo build"
                    )
                )

            AppForgeProjectKind.C_CPP ->
                cCppSteps(root)

            AppForgeProjectKind.UNKNOWN ->
                emptyList()
        }

    private fun nodeSteps(
        root: File
    ): List<ProjectAutomationStep> {
        val install =
            if (
                File(
                    root,
                    "package-lock.json"
                ).isFile
            ) {
                "npm ci"
            } else {
                "npm install"
            }

        return listOf(
            step(
                ProjectAutomationStepKind.INSTALL,
                "Node bağımlılıkları",
                if (install == "npm ci") {
                    "package-lock.json bulunduğu için yeniden üretilebilir npm ci kullanır."
                } else {
                    "package-lock.json yok; npm install kullanır."
                },
                install
            ),
            step(
                ProjectAutomationStepKind.TEST,
                "Node test",
                "package.json içindeki test scripti varsa çalıştırır.",
                "npm run test --if-present"
            ),
            step(
                ProjectAutomationStepKind.BUILD,
                "Node build",
                "package.json içindeki build scripti varsa çalıştırır.",
                "npm run build --if-present"
            )
        )
    }

    private fun javaSteps(
        root: File
    ): List<ProjectAutomationStep> =
        when {
            File(
                root,
                "gradlew"
            ).isFile ->
                listOf(
                    step(
                        ProjectAutomationStepKind.TEST,
                        "Gradle test",
                        "Java Gradle testlerini wrapper ile çalıştırır.",
                        "./gradlew test"
                    ),
                    step(
                        ProjectAutomationStepKind.BUILD,
                        "Gradle build",
                        "Java Gradle projesini wrapper ile derler.",
                        "./gradlew build"
                    )
                )

            File(
                root,
                "pom.xml"
            ).isFile ->
                listOf(
                    step(
                        ProjectAutomationStepKind.INSTALL,
                        "Maven bağımlılık önbelleği",
                        "Maven bağımlılıklarını önceden getirir.",
                        "mvn -B dependency:go-offline"
                    ),
                    step(
                        ProjectAutomationStepKind.TEST,
                        "Maven test",
                        "Maven testlerini çalıştırır.",
                        "mvn -B test"
                    ),
                    step(
                        ProjectAutomationStepKind.BUILD,
                        "Maven package",
                        "Testleri tekrar çalıştırmadan paket oluşturur.",
                        "mvn -B package -DskipTests"
                    )
                )

            else ->
                emptyList()
        }

    private fun cCppSteps(
        root: File
    ): List<ProjectAutomationStep> =
        when {
            File(
                root,
                "CMakeLists.txt"
            ).isFile ->
                listOf(
                    step(
                        ProjectAutomationStepKind.BUILD,
                        "CMake build",
                        "Ayrı build klasöründe CMake yapılandırma ve derleme yapar.",
                        "cmake -S . -B build && cmake --build build"
                    ),
                    step(
                        ProjectAutomationStepKind.TEST,
                        "CTest",
                        "CMake testleri tanımlıysa çalıştırır.",
                        "ctest --test-dir build --output-on-failure"
                    )
                )

            File(
                root,
                "Makefile"
            ).isFile ->
                listOf(
                    step(
                        ProjectAutomationStepKind.BUILD,
                        "Make",
                        "Projenin Makefile hedefini çalıştırır.",
                        "make"
                    )
                )

            else ->
                emptyList()
        }

    private fun detectDeployTargets(
        root: File
    ): List<DeploymentProvider> =
        buildList {
            if (
                File(root, "railway.toml").isFile ||
                File(root, "railway.json").isFile
            ) {
                add(
                    DeploymentProvider.RAILWAY
                )
            }

            if (
                File(root, "vercel.json").isFile
            ) {
                add(
                    DeploymentProvider.VERCEL
                )
            }

            if (
                File(root, "wrangler.toml").isFile ||
                File(root, "wrangler.json").isFile ||
                File(root, "wrangler.jsonc").isFile
            ) {
                add(
                    DeploymentProvider.CLOUDFLARE
                )
            }

            if (
                File(root, "firebase.json").isFile
            ) {
                add(
                    DeploymentProvider.FIREBASE
                )
            }

            if (
                File(root, "supabase/config.toml").isFile
            ) {
                add(
                    DeploymentProvider.SUPABASE
                )
            }

            if (
                File(root, "render.yaml").isFile ||
                File(root, "render.yml").isFile
            ) {
                add(
                    DeploymentProvider.RENDER
                )
            }
        }.distinct()

    private fun step(
        kind: ProjectAutomationStepKind,
        title: String,
        description: String,
        command: String
    ) =
        ProjectAutomationStep(
            id =
                "${kind.name.lowercase()}-${title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}",
            kind =
                kind,
            title =
                title,
            description =
                description,
            command =
                command
        )
}

internal class UltimateProjectAutomationExecutor(
    context: Context
) {
    private val appContext =
        context.applicationContext

    private val runtimeManager =
        AndroidLinuxRuntimeManager(
            appContext
        )

    private val shellEngine =
        LinuxShellEngine(
            appContext
        )

    suspend fun execute(
        workspace: File,
        distribution: LinuxDistribution,
        command: String,
        confirmed: Boolean,
        timeoutMs: Long = 600_000L
    ): LinuxShellResult {
        val rootfs =
            runtimeManager
                .requireReadyRootfs(
                    distribution
                )

        return shellEngine.execute(
            sessionId =
                "ultimate-auto-${UUID.randomUUID()}",
            rootfs =
                rootfs,
            workspace =
                workspace.canonicalFile,
            command =
                command,
            confirmed =
                confirmed,
            timeoutMs =
                timeoutMs
        )
    }

    fun packageInstallCommand(
        selected: Collection<LinuxToolchainId>
    ): String =
        runtimeManager
            .toolchainCommand(
                selected
            )
}
