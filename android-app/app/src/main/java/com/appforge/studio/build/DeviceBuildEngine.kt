package com.appforge.studio.build

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SigningMode
import com.appforge.studio.model.SourceMode
import com.appforge.studio.terminal.LinuxShellEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal data class DeviceBuildStart(
    val id: String,
    val buildNo: Long
)

internal data class DeviceBuildSnapshot(
    val id: String,
    val buildNo: Long,
    val status: String,
    val progress: Int,
    val logs: List<String>,
    val preflight: List<String>,
    val apk: File?,
    val aab: File?
)

private val LOCAL_ADDITIONAL_PERMISSION_ALLOWLIST =
    setOf(
        "BLUETOOTH_SCAN",
        "BLUETOOTH_CONNECT",
        "USE_BIOMETRIC",
        "READ_CALENDAR",
        "WRITE_CALENDAR",
        "READ_CONTACTS",
        "WRITE_CONTACTS",
        "ACCESS_BACKGROUND_LOCATION",
        "SCHEDULE_EXACT_ALARM",
        "READ_MEDIA_IMAGES",
        "READ_MEDIA_VIDEO",
        "ACTIVITY_RECOGNITION"
    )

object DeviceBuildEngine {
    private data class JobState(
        val id: String,
        val buildNo: Long,
        val logs: CopyOnWriteArrayList<String> = CopyOnWriteArrayList(),
        val preflight: CopyOnWriteArrayList<String> = CopyOnWriteArrayList(),
        val cancelled: AtomicBoolean = AtomicBoolean(false),
        @Volatile var status: String = "Hazırlanıyor",
        @Volatile var progress: Int = 1,
        @Volatile var offline: Boolean = false,
        @Volatile var apk: File? = null,
        @Volatile var aab: File? = null,
        @Volatile var shell: LinuxShellEngine? = null,
        @Volatile var shellSessionId: String? = null
    )

    private val jobs = ConcurrentHashMap<String, JobState>()
    private val buildNumbers = AtomicLong(System.currentTimeMillis() / 1000L)
    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "AppForgeDeviceBuild").apply { isDaemon = true }
    }

    internal fun start(
        context: Context,
        draft: ProjectDraft,
        projectZip: File?
    ): DeviceBuildStart {
        val id = "local-" + UUID.randomUUID().toString().replace("-", "").take(20)
        val buildNo = buildNumbers.incrementAndGet()
        val state = JobState(id = id, buildNo = buildNo)
        jobs[id] = state

        executor.execute {
            runBuild(
                context = context.applicationContext,
                draft = draft.copy(),
                projectZip = projectZip,
                state = state
            )
        }

        return DeviceBuildStart(id, buildNo)
    }

    internal fun snapshot(id: String): DeviceBuildSnapshot? =
        jobs[id]?.let {
            DeviceBuildSnapshot(
                id = it.id,
                buildNo = it.buildNo,
                status = it.status,
                progress = it.progress,
                logs = it.logs.toList(),
                preflight = it.preflight.toList(),
                apk = it.apk,
                aab = it.aab
            )
        }

    fun artifact(id: String, kind: String): File? {
        val state = jobs[id] ?: return null
        val file = if (kind.equals("aab", true)) state.aab else state.apk
        return file?.takeIf { it.isFile && it.length() > 0L }
    }

    fun cancel(id: String): Boolean {
        val state = jobs[id] ?: return false
        state.cancelled.set(true)
        state.shellSessionId?.let { session -> state.shell?.cancel(session) }
        state.status = "cancelled"
        state.logs.add("⛔ Derleme iptal edildi.")
        return true
    }

    private fun runBuild(
        context: Context,
        draft: ProjectDraft,
        projectZip: File?,
        state: JobState
    ) {
        val workspace = File(context.cacheDir, "device-build/${state.id}")

        try {
            state.offline = runCatching {
                val manager = context.getSystemService(
                    ConnectivityManager::class.java
                )
                val capabilities = manager?.activeNetwork?.let { network ->
                    manager.getNetworkCapabilities(network)
                }
                capabilities?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                ) != true
            }.getOrDefault(true)

            state.logs.add(
                if (state.offline) {
                    "📴 Çevrimdışı build • yalnız yerel araçlar kullanılacak."
                } else {
                    "🌐 Bağlantı mevcut • eksik araç hazırlığı yapılabilir."
                }
            )

            validateCapabilities(draft)

            val sourceEngine =
                draft
                    .sourceBuildEngine
                    .trim()
                    .lowercase()
                    .ifBlank {
                        "webview-static"
                    }

            state.preflight.add("✅ Build cihaz üzerinde çalışacak.")
            state.preflight.add("✅ Worker / queue / cloud build kullanılmıyor.")
            state.preflight.add("✅ Clean Device Build Runtime V3 • Terminal Linux ortamından izole.")

            workspace.deleteRecursively()
            workspace.mkdirs()
            copyRuntimeAssets(context, File(workspace, "runtime"))

            if (draft.sourceMode == SourceMode.LOCAL) {
                val sourceDir = draft.importedFolder?.let(::File)?.takeIf { it.isDirectory }
                    ?: error("Yerel proje klasörü bulunamadı.")
                val target = File(workspace, "source")
                check(sourceDir.copyRecursively(target, overwrite = true)) {
                    "Proje çalışma alanına kopyalanamadı."
                }
                state.logs.add("📁 Proje cihaz çalışma alanına hazırlandı.")
            }

            checkCancelled(state)
            state.progress = 8
            state.status = "Hazırlanıyor"

            val rootfs =
                runBlocking {
                    DeviceBuildRuntimeV3
                        .ensureReady(
                            context
                        ) { detail ->
                            state.logs.add(
                                detail
                            )
                        }
                }

            val shell =
                LinuxShellEngine(
                    context
                )
            state.shell = shell

            runShellBlocking(
                shell = shell,
                rootfs = rootfs,
                workspace = workspace,
                state = state,
                command = "chmod +x /workspace/runtime/install-toolchain.sh /workspace/runtime/build-node.sh && APPFORGE_DEVICE_OFFLINE=${if (state.offline) 1 else 0} /bin/sh /workspace/runtime/install-toolchain.sh ${sh(sourceEngine)}",
                suffix = "toolchain"
            )

            checkCancelled(state)
            state.progress = 25
            state.status = "Derleniyor"

            when (sourceEngine) {
                "node-web" -> buildNodeWeb(context, draft, workspace, rootfs, shell, state)
                "android-gradle" -> buildAndroidProject(context, draft, workspace, rootfs, shell, state)
                "python-android" -> buildPythonProject(context, draft, workspace, rootfs, shell, state)
                "webview-static", "", "unknown" -> buildWebWrapper(
                    context,
                    draft,
                    workspace,
                    rootfs,
                    shell,
                    state,
                    resolveStaticWebRoot(draft, workspace)
                )
                else -> error("${draft.sourceTechnologyLabel} için cihaz build motoru henüz etkin değil.")
            }

            checkCancelled(state)
            state.status = "İmzalanıyor"
            state.progress = 94

            check(state.apk != null || state.aab != null) {
                "APK/AAB çıktısı üretilemedi."
            }

            state.status = "success"
            state.progress = 100
            state.logs.add("✅ Hazır • Derleme tamamen cihaz üzerinde tamamlandı.")
        } catch (t: Throwable) {
            if (state.cancelled.get()) {
                state.status = "cancelled"
                state.progress = 0
            } else {
                state.status = "failed"
                state.logs.add("❌ ${t.message ?: t.javaClass.simpleName}")
            }
        } finally {
            state.shellSessionId = null
            state.shell = null
            projectZip?.takeIf { it.name.startsWith("appforge-") }?.delete()
        }
    }

    private fun validateCapabilities(draft: ProjectDraft) {
        require(draft.packageName.matches(Regex("""^[A-Za-z_]\w*(\.[A-Za-z_]\w*)+$"""))) {
            "Geçersiz Android package name."
        }
        require(draft.minSdk in 26..37 && draft.targetSdk in 26..37 && draft.minSdk <= draft.targetSdk) {
            "Android SDK aralığı geçersiz."
        }
        if (draft.sourceMode == SourceMode.URL) {
            require(draft.webUrl.startsWith("https://", true)) { "Web URL HTTPS olmalı." }
        }

        val unsupported = buildList {
            if (draft.qrScanner) add("QR Scanner")
            if (draft.mediaPlayerBridge) add("Media3")
            if (draft.admobEnabled) add("AdMob")
            if (draft.billingEnabled) add("uygulama içi Billing enjeksiyonu")
            if (draft.firebaseAnalyticsEnabled || draft.firebaseCrashlyticsEnabled || draft.firebaseMessagingEnabled) {
                add("Firebase otomatik enjeksiyonu")
            }
        }
        require(unsupported.isEmpty()) {
            "Cihaz motorunda henüz taşınmamış eklentiler: ${unsupported.joinToString(", ")}"
        }

        val sourceEngine =
            draft
                .sourceBuildEngine
                .trim()
                .lowercase()
                .ifBlank {
                    "webview-static"
                }

        val technologyCapability =
            DeviceBuildCapabilities
                .forTechnology(
                    draft.sourceTechnology
                )

        if (
            technologyCapability != null &&
            technologyCapability.support !=
                DeviceBuildSupport.READY
        ) {
            error(
                "${draft.sourceTechnologyLabel}: " +
                    technologyCapability.note
            )
        }

        val capability =
            technologyCapability
                ?: DeviceBuildCapabilities
                    .forEngine(
                        sourceEngine
                    )
                ?: error(
                    "${draft.sourceTechnologyLabel} için cihaz-build capability kaydı yok."
                )

        require(
            capability.support ==
                DeviceBuildSupport.READY
        ) {
            capability.note
        }

        val requestedOutputs =
            DeviceBuildCapabilities
                .requestedOutputs(
                    draft.buildOutput
                )

        val unavailable =
            requestedOutputs -
                capability.readyOutputs

        require(
            unavailable.isEmpty()
        ) {
            "Bu motor henüz şu cihaz-local çıktıları üretmiyor: " +
                DeviceBuildCapabilities
                    .outputLabels(
                        unavailable
                    ) +
                ". " +
                capability.note
        }
    }

    private fun buildNodeWeb(
        context: Context,
        draft: ProjectDraft,
        workspace: File,
        rootfs: File,
        shell: LinuxShellEngine,
        state: JobState
    ) {
        state.logs.add("📦 Node/Web bağımlılıkları hazırlanıyor.")
        runShellBlocking(shell, rootfs, workspace, state, "APPFORGE_DEVICE_OFFLINE=${if (state.offline) 1 else 0} /bin/sh /workspace/runtime/build-node.sh", "node-web")

        val relative = File(workspace, ".appforge-web-output").readText().trim()
        require(relative.isNotBlank()) { "Web build çıktısı belirlenemedi." }

        val site = File(File(workspace, "source"), relative).canonicalFile
        require(site.isDirectory && File(site, "index.html").isFile) {
            "Derlenmiş web index.html bulunamadı."
        }

        state.logs.add("✅ Web kaynakları derlendi.")
        state.progress = 55
        buildWebWrapper(context, draft, workspace, rootfs, shell, state, site)
    }

    private fun resolveStaticWebRoot(draft: ProjectDraft, workspace: File): File? {
        if (draft.sourceMode == SourceMode.URL) return null
        val copiedRoot = File(workspace, "source")
        val originalRoot = draft.importedFolder?.let(::File)?.canonicalFile
        val originalStart = draft.startPage?.let(::File)?.canonicalFile

        if (originalRoot != null && originalStart != null && originalStart.isFile) {
            val rel = runCatching { originalStart.relativeTo(originalRoot) }.getOrNull()
            val copiedStart = rel?.let { File(copiedRoot, it.path) }
            copiedStart?.parentFile?.takeIf { File(it, "index.html").isFile }?.let { return it }
        }

        if (File(copiedRoot, "index.html").isFile) return copiedRoot
        return copiedRoot.walkTopDown().maxDepth(12).firstOrNull {
            it.isFile && it.name.equals("index.html", true)
        }?.parentFile
    }

    private fun buildWebWrapper(
        context: Context,
        draft: ProjectDraft,
        workspace: File,
        rootfs: File,
        shell: LinuxShellEngine,
        state: JobState,
        siteRoot: File?
    ) {
        val project = File(workspace, "android-wrapper")
        project.deleteRecursively()

        val javaDir = File(project, "app/src/main/java/com/appforge/runtime").apply { mkdirs() }
        val assetsDir = File(project, "app/src/main/assets").apply { mkdirs() }
        File(workspace, "runtime/FastActivity.java").copyTo(File(javaDir, "FastActivity.java"), overwrite = true)

        val site = File(assetsDir, "site").apply { mkdirs() }
        if (draft.sourceMode == SourceMode.LOCAL) {
            val source = siteRoot ?: error("index.html bulunamadı.")
            check(source.copyRecursively(site, overwrite = true)) {
                "Web dosyaları Android assets alanına kopyalanamadı."
            }
        } else {
            File(site, "index.html").writeText("<!doctype html><html><body></body></html>")
        }

        val config = JSONObject()
            .put("sourceMode", draft.sourceMode.name)
            .put("webUrl", draft.webUrl)
            .put("appName", draft.appName)
            .put("splashEnabled", draft.splashEnabled)
            .put("splashText", draft.splashText)
            .put("backgroundColor", draft.backgroundColor)
            .put("statusBarColor", draft.statusBarColor)
            .put("navigationBarColor", draft.navigationBarColor)
            .put("fullscreen", draft.fullscreen)
            .put("notifications", draft.notifications)
            .put("camera", draft.camera)
            .put("location", draft.location)
            .put("fileUpload", draft.fileUpload)
            .put("downloads", draft.downloads)
            .put("webJavaScriptEnabled", draft.webJavaScriptEnabled)
            .put("webDomStorageEnabled", draft.webDomStorageEnabled)
            .put("webZoomEnabled", draft.webZoomEnabled)
            .put("webWideViewPortEnabled", draft.webWideViewPortEnabled)
            .put("webOverviewModeEnabled", draft.webOverviewModeEnabled)
            .put("webMediaAutoplayEnabled", draft.webMediaAutoplayEnabled)
            .put("webMixedContentAllowed", draft.webMixedContentAllowed)
            .put("nativeBridge", draft.javascriptBridge)
            .put("share", draft.shareBridge)
            .put("clipboard", draft.clipboardBridge)
            .put("vibration", draft.vibrationBridge)
            .put("offlineCache", draft.offlineCache)

        File(assetsDir, "appforge-fast.json").writeText(config.toString())
        writeAndroidRootProject(project)
        File(project, "app/build.gradle.kts").writeText(webAppGradle(draft))
        File(project, "app/src/main/AndroidManifest.xml").apply {
            parentFile?.mkdirs()
            writeText(webManifest(draft))
        }
        writeSdkFiles(project)

        state.logs.add("🤖 Android paketi cihazda oluşturuluyor.")
        buildGradleProject(context, draft, project, workspace, rootfs, shell, state, "9.3.1")
    }

    private fun buildAndroidProject(
        context: Context,
        draft: ProjectDraft,
        workspace: File,
        rootfs: File,
        shell: LinuxShellEngine,
        state: JobState
    ) {
        val project = File(workspace, "source")
        require(File(project, "settings.gradle").isFile || File(project, "settings.gradle.kts").isFile) {
            "Android Gradle settings.gradle(.kts) bulunamadı."
        }
        writeSdkFiles(project)
        val gradleVersion = detectGradleVersion(project)
        state.logs.add("🤖 Native Android proje • Gradle $gradleVersion")
        buildGradleProject(context, draft, project, workspace, rootfs, shell, state, gradleVersion)
    }

    private fun buildPythonProject(
        context: Context,
        draft: ProjectDraft,
        workspace: File,
        rootfs: File,
        shell: LinuxShellEngine,
        state: JobState
    ) {
        val template = File(workspace, "runtime/python-template")
        val project = File(workspace, "python-android")
        project.deleteRecursively()
        check(template.copyRecursively(project, overwrite = true)) { "Python Android template hazırlanamadı." }

        val source = File(workspace, "source")
        val pythonDest = File(project, "app/src/main/python").apply { mkdirs() }
        val ignored = setOf(".git", ".gradle", ".venv", "venv", "__pycache__", "node_modules", "build", "dist")

        source.walkTopDown().maxDepth(12).filter {
            it.isFile && it.extension.lowercase() in setOf("py", "json", "txt", "csv", "yaml", "yml", "toml", "ini", "cfg") &&
                it.relativeTo(source).path.split(File.separatorChar).none { part -> part in ignored }
        }.forEach { file ->
            val relative = file.relativeTo(source)
            val target = File(pythonDest, relative.path)
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
        }

        val entry = listOf(File(pythonDest, "main.py"), File(pythonDest, "app.py")).firstOrNull { it.isFile }
            ?: pythonDest.walkTopDown().firstOrNull {
                it.isFile && (it.name.equals("main.py", true) || it.name.equals("app.py", true))
            }
            ?: error("Python main.py veya app.py bulunamadı.")

        val module = entry.relativeTo(pythonDest).invariantSeparatorsPath.removeSuffix(".py").replace('/', '.')
        val bridge = File(pythonDest, "appforge_entry.py")
        bridge.writeText(
            bridge.readText().replace(
                "importlib.import_module(\"main\")",
                "importlib.import_module(${JSONObject.quote(module)})"
            )
        )

        source.walkTopDown().maxDepth(3).firstOrNull {
            it.isFile && it.name.equals("requirements.txt", true)
        }?.copyTo(File(project, "app/requirements.txt"), overwrite = true)

        File(project, "app/build.gradle.kts").writeText(pythonAppGradle(draft))
        val manifest = File(project, "app/src/main/AndroidManifest.xml")
        manifest.writeText(
            manifest.readText().replace(
                "android:label=\"AppForge Python\"",
                "android:label=\"${xml(draft.appName)}\""
            )
        )
        writeSdkFiles(project)

        state.logs.add("🐍 Python / Chaquopy cihaz derlemesi hazırlanıyor.")
        buildGradleProject(context, draft, project, workspace, rootfs, shell, state, "9.3.1")
    }

    private fun buildGradleProject(
        context: Context,
        draft: ProjectDraft,
        project: File,
        workspace: File,
        rootfs: File,
        shell: LinuxShellEngine,
        state: JobState,
        gradleVersion: String
    ) {
        val relativeProject = project.relativeTo(workspace).invariantSeparatorsPath
        val gradlePath = runShellBlocking(
            shell,
            rootfs,
            workspace,
            state,
            "APPFORGE_DEVICE_OFFLINE=${if (state.offline) 1 else 0} /opt/appforge-device/ensure-gradle ${sh(gradleVersion)}",
            "gradle-$gradleVersion"
        ).lineSequence().lastOrNull { it.isNotBlank() }?.trim()
            ?: error("Gradle hazırlanamadı.")

        val variant = if (draft.signingMode == SigningMode.CUSTOM) "Release" else "Debug"
        val tasks = buildList {
            if (draft.buildOutput != "aab") add(":app:assemble$variant")
            if (draft.buildOutput != "apk") add(":app:bundle$variant")
        }
        require(tasks.isNotEmpty()) { "En az bir build çıktısı seçilmeli." }

        val signingArgs = if (draft.signingMode == SigningMode.CUSTOM) {
            val key = copyKeystore(context, draft, workspace)
            listOf(
                "-Pandroid.injected.signing.store.file=/workspace/${key.name}",
                "-Pandroid.injected.signing.store.password=${draft.storePassword}",
                "-Pandroid.injected.signing.key.alias=${draft.keyAlias}",
                "-Pandroid.injected.signing.key.password=${draft.keyPassword}"
            ).joinToString(" ") { sh(it) }
        } else ""

        state.progress = 65

        val command = buildString {
            append("export JAVA_HOME=/opt/appforge-device/jdk-17; ")
            append("export PATH=/opt/appforge-device/jdk-17/bin:\$PATH; ")
            append("export ANDROID_SDK_ROOT=/opt/appforge-device/android-sdk; ")
            append("export ANDROID_HOME=/opt/appforge-device/android-sdk; ")
            append("export GRADLE_USER_HOME=/root/.gradle-appforge; ")
            append(sh(gradlePath))
            append(" -p ")
            append(sh("/workspace/$relativeProject"))
            append(" --no-daemon --stacktrace ")
            if (state.offline) append("--offline ")
            append(tasks.joinToString(" "))
            if (signingArgs.isNotBlank()) append(" $signingArgs")
        }

        runShellBlocking(shell, rootfs, workspace, state, command, "android-build")
        state.progress = 90

        val outputs = project.walkTopDown().maxDepth(14).filter {
            it.isFile && (it.extension.equals("apk", true) || it.extension.equals("aab", true))
        }.toList()

        val artifactRoot = File(context.filesDir, "device-build/artifacts/${state.id}").apply { mkdirs() }

        outputs.filter { it.extension.equals("apk", true) }.maxByOrNull { it.lastModified() }?.let { source ->
            val target = File(artifactRoot, "${safeName(draft.appName)}-${state.buildNo}.apk")
            source.copyTo(target, overwrite = true)
            state.apk = target
        }

        outputs.filter { it.extension.equals("aab", true) }.maxByOrNull { it.lastModified() }?.let { source ->
            val target = File(artifactRoot, "${safeName(draft.appName)}-${state.buildNo}.aab")
            source.copyTo(target, overwrite = true)
            state.aab = target
        }

        if (draft.buildOutput == "apk" || draft.buildOutput == "both") {
            require(state.apk != null) { "Gradle tamamlandı ancak APK bulunamadı." }
        }
        if (draft.buildOutput == "aab" || draft.buildOutput == "both") {
            require(state.aab != null) { "Gradle tamamlandı ancak AAB bulunamadı." }
        }
    }

    private fun copyKeystore(context: Context, draft: ProjectDraft, workspace: File): File {
        val uri = draft.keystoreUri ?: error("Release keystore seçilmedi.")
        require(draft.keyAlias.isNotBlank() && draft.storePassword.isNotBlank() && draft.keyPassword.isNotBlank()) {
            "Release signing bilgileri eksik."
        }

        val target = File(workspace, "appforge-release.keystore")
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Keystore açılamadı.")
        require(target.length() > 0L) { "Keystore boş." }
        return target
    }

    private fun writeAndroidRootProject(project: File) {
        project.mkdirs()
        File(project, "settings.gradle.kts").writeText(
            """
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
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "AppForgeDeviceBuild"
            include(":app")
            """.trimIndent()
        )
        File(project, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "9.1.1" apply false
            }
            """.trimIndent()
        )
    }

    private fun writeSdkFiles(project: File) {
        File(project, "local.properties").writeText("sdk.dir=/opt/appforge-device/android-sdk\n")
        val gradleProperties = File(project, "gradle.properties")
        val existing = if (gradleProperties.isFile) gradleProperties.readText() else ""
        val filtered = existing.lineSequence().filterNot {
            it.startsWith("android.aapt2FromMavenOverride=") ||
                it.startsWith("org.gradle.workers.max=") ||
                it.startsWith("org.gradle.jvmargs=")
        }.joinToString("\n")
        gradleProperties.writeText(
            filtered.trimEnd() + "\n" +
                "android.aapt2FromMavenOverride=/opt/appforge-device/android-sdk/build-tools/36.0.0/aapt2\n" +
                "org.gradle.workers.max=2\n" +
                "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n"
        )
    }

    private fun webAppGradle(draft: ProjectDraft): String =
        """
        plugins { id("com.android.application") }
        android {
            namespace = ${JSONObject.quote(draft.packageName)}
            compileSdk = 37
            defaultConfig {
                applicationId = ${JSONObject.quote(draft.packageName)}
                minSdk = ${draft.minSdk}
                targetSdk = ${draft.targetSdk}
                versionCode = ${draft.versionCode.coerceAtLeast(1)}
                versionName = ${JSONObject.quote(draft.versionName.ifBlank { "1.0.0" })}
            }
        }
        """.trimIndent()

    private fun pythonAppGradle(draft: ProjectDraft): String =
        """
        plugins {
            id("com.android.application")
            id("com.chaquo.python")
        }
        android {
            namespace = "com.appforge.pythonruntime"
            compileSdk = 37
            defaultConfig {
                applicationId = ${JSONObject.quote(draft.packageName)}
                minSdk = ${draft.minSdk}
                targetSdk = ${draft.targetSdk}
                versionCode = ${draft.versionCode.coerceAtLeast(1)}
                versionName = ${JSONObject.quote(draft.versionName.ifBlank { "1.0.0" })}
                ndk { abiFilters += listOf("arm64-v8a") }
            }
        }
        chaquopy {
            defaultConfig {
                version = "3.11"
                buildPython("/usr/bin/python3")
                pip { install("-r", "requirements.txt") }
            }
        }
        """.trimIndent()

    private fun webManifest(draft: ProjectDraft): String {
        val permissions = buildString {
            append("<uses-permission android:name=\"android.permission.INTERNET\" />\n")
            if (draft.notifications) append("<uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />\n")
            if (draft.camera) append("<uses-permission android:name=\"android.permission.CAMERA\" />\n")
            if (draft.microphone) append("<uses-permission android:name=\"android.permission.RECORD_AUDIO\" />\n")
            if (draft.location) {
                append("<uses-permission android:name=\"android.permission.ACCESS_COARSE_LOCATION\" />\n")
                append("<uses-permission android:name=\"android.permission.ACCESS_FINE_LOCATION\" />\n")
            }
            if (draft.networkState) append("<uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />\n")
            if (draft.wakeLock) append("<uses-permission android:name=\"android.permission.WAKE_LOCK\" />\n")
            if (draft.nfc) append("<uses-permission android:name=\"android.permission.NFC\" />\n")

            draft
                .additionalPermissions
                .asSequence()
                .map {
                    it
                        .removePrefix(
                            "android.permission."
                        )
                        .trim()
                        .uppercase()
                }
                .filter {
                    it in
                        LOCAL_ADDITIONAL_PERMISSION_ALLOWLIST
                }
                .distinct()
                .forEach { permission ->
                    append(
                        "<uses-permission android:name=\"android.permission.$permission\" />\n"
                    )
                }

        }
        val orientation = draft.orientation.takeIf { it in setOf("portrait", "landscape", "unspecified") } ?: "unspecified"
        val deepLink = if (draft.deepLinkEnabled) {
            """
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="${xml(draft.deepLinkScheme)}" android:host="${xml(draft.deepLinkHost)}" android:pathPrefix="${xml(draft.deepLinkPathPrefix)}" />
            </intent-filter>
            """.trimIndent()
        } else ""

        return """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                $permissions
                <application
                    android:allowBackup="false"
                    android:hardwareAccelerated="true"
                    android:label="${xml(draft.appName.ifBlank { "AppForge App" })}"
                    android:theme="@android:style/Theme.Material.NoActionBar"
                    android:usesCleartextTraffic="false">
                    <activity
                        android:name="com.appforge.runtime.FastActivity"
                        android:exported="true"
                        android:launchMode="singleTop"
                        android:screenOrientation="$orientation">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                        $deepLink
                    </activity>
                </application>
            </manifest>
        """.trimIndent()
    }

    private fun detectGradleVersion(project: File): String {
        val wrapper = File(project, "gradle/wrapper/gradle-wrapper.properties")
        if (wrapper.isFile) {
            Regex("""gradle-([0-9]+(?:\.[0-9]+){1,2})-(?:bin|all)\.zip""")
                .find(wrapper.readText())?.let { return it.groupValues[1] }
        }
        return "9.3.1"
    }

    private fun runShellBlocking(
        shell: LinuxShellEngine,
        rootfs: File,
        workspace: File,
        state: JobState,
        command: String,
        suffix: String,
        timeoutMs: Long = 1_800_000L
    ): String = runBlocking {
        runShell(shell, rootfs, workspace, state, command, suffix, timeoutMs)
    }

    private suspend fun runShell(
        shell: LinuxShellEngine,
        rootfs: File,
        workspace: File,
        state: JobState,
        command: String,
        suffix: String,
        timeoutMs: Long
    ): String {
        checkCancelled(state)
        val sessionId = "device-${state.id}-$suffix"
        state.shellSessionId = sessionId
        val result = shell.execute(
            sessionId = sessionId,
            rootfs = rootfs,
            workspace = workspace,
            command = command,
            confirmed = true,
            timeoutMs = timeoutMs
        )
        state.shellSessionId = null

        result.output.lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(120)
            .forEach {
            state.logs.add(it.take(700))
        }

        check(!result.timedOut) { "Cihaz build komutu zaman aşımına uğradı." }
        check(result.exitCode == 0) {
            result.output.takeLast(4000).ifBlank { "Cihaz build komutu başarısız." }
        }
        checkCancelled(state)
        return result.output
    }

    private fun checkCancelled(state: JobState) {
        check(!state.cancelled.get()) { "Derleme iptal edildi." }
    }

    private fun copyRuntimeAssets(context: Context, target: File) {
        target.deleteRecursively()
        target.mkdirs()
        copyAssetTree(context, "device-build", target)
    }

    private fun copyAssetTree(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        target.mkdirs()
        children.forEach { child ->
            copyAssetTree(context, "$assetPath/$child", File(target, child))
        }
    }

    private fun safeName(value: String): String =
        value.trim().replace(Regex("""[^A-Za-z0-9._-]+"""), "-").trim('-').take(80).ifBlank { "AppForge" }

    private fun sh(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
