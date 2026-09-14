package com.appforge.studio.ai

import com.appforge.studio.io.SourceCapabilityAnalyzer
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class AppForgeAgentPreparedBuildProject(
    val tempRoot: File,
    val projectRoot: File,
    val zipFile: File,
    val sourceTechnology: String,
    val sourceTechnologyLabel: String,
    val buildEngine: String,
    val packageName: String
) {
    fun cleanup() {
        tempRoot.deleteRecursively()
    }
}

internal object AppForgeAgentBuildProjectPreparer {
    private const val MAX_FILES = 8_000
    private const val MAX_TOTAL_BYTES = 120L * 1024L * 1024L

    fun prepare(
        cacheRoot: File,
        workspace: File,
        blueprint: AppForgeAgentBlueprint
    ): AppForgeAgentPreparedBuildProject {
        val workspaceRoot = workspace.canonicalFile
        require(workspaceRoot.isDirectory) {
            "Unified Agent workspace bulunamadı."
        }

        val sourceRoot = File(
            workspaceRoot,
            when (blueprint.platform) {
                AppForgeAgentPlatform.ANDROID -> "android"
                AppForgeAgentPlatform.FLUTTER -> "flutter"
                AppForgeAgentPlatform.REACT_NATIVE -> "react-native"
                AppForgeAgentPlatform.WEB -> "web"
            }
        ).canonicalFile

        require(sourceRoot.isDirectory && isInside(workspaceRoot, sourceRoot)) {
            "Üretilen platform kaynağı workspace içinde bulunamadı."
        }

        val tempRoot = File(
            cacheRoot,
            "appforge-unified-agent-build/${UUID.randomUUID()}"
        ).canonicalFile

        val projectRoot = File(tempRoot, "project")
        require(projectRoot.mkdirs()) {
            "Geçici Unified Agent build klasörü oluşturulamadı."
        }

        try {
            copyDirectoryContents(sourceRoot, projectRoot)

            when (blueprint.platform) {
                AppForgeAgentPlatform.ANDROID ->
                    scaffoldAndroid(projectRoot, blueprint)

                AppForgeAgentPlatform.FLUTTER ->
                    scaffoldFlutter(projectRoot, blueprint)

                AppForgeAgentPlatform.REACT_NATIVE ->
                    scaffoldReactNative(projectRoot, blueprint)

                AppForgeAgentPlatform.WEB ->
                    require(File(projectRoot, "index.html").isFile) {
                        "Web codegen index.html üretmedi."
                    }
            }

            val analysis = SourceCapabilityAnalyzer.analyze(projectRoot)
            val expectedEngine = when (blueprint.platform) {
                AppForgeAgentPlatform.ANDROID -> "android-gradle"
                AppForgeAgentPlatform.FLUTTER -> "flutter"
                AppForgeAgentPlatform.REACT_NATIVE -> "expo-android"
                AppForgeAgentPlatform.WEB -> "webview-static"
            }

            require(analysis.buildReady) {
                "Üretilen proje AppForge build için hazır değil: ${analysis.technologyReason.orEmpty()}"
            }
            require(analysis.buildEngine == expectedEngine) {
                "Build motoru uyuşmuyor: beklenen=$expectedEngine gerçek=${analysis.buildEngine}"
            }

            val zipFile = File(tempRoot, "input.zip")
            zipProject(projectRoot, zipFile)
            require(zipFile.isFile && zipFile.length() > 0L) {
                "Unified Agent build ZIP'i oluşturulamadı."
            }

            return AppForgeAgentPreparedBuildProject(
                tempRoot = tempRoot,
                projectRoot = projectRoot,
                zipFile = zipFile,
                sourceTechnology = analysis.technologyId,
                sourceTechnologyLabel = analysis.technologyLabel,
                buildEngine = analysis.buildEngine,
                packageName = safeApplicationId(blueprint.appName)
            )
        } catch (error: Throwable) {
            tempRoot.deleteRecursively()
            throw error
        }
    }

    internal fun safeApplicationId(appName: String): String {
        val leaf = appName
            .trim()
            .map { ch ->
                if (ch.code < 128 && ch.isLetterOrDigit()) ch else '_'
            }
            .joinToString("")
            .trim('_')
            .ifBlank { "app" }
            .let { value ->
                if (value.first().isDigit()) "app_$value" else value
            }
            .lowercase()
            .take(48)
            .trimEnd('_')
            .ifBlank { "app" }

        return "com.appforge.generated.$leaf"
    }

    internal fun workspaceDigest(
        workspace: File,
        platform: AppForgeAgentPlatform
    ): String {
        val root = File(
            workspace.canonicalFile,
            when (platform) {
                AppForgeAgentPlatform.ANDROID -> "android"
                AppForgeAgentPlatform.FLUTTER -> "flutter"
                AppForgeAgentPlatform.REACT_NATIVE -> "react-native"
                AppForgeAgentPlatform.WEB -> "web"
            }
        ).canonicalFile

        require(root.isDirectory && isInside(workspace.canonicalFile, root)) {
            "Workspace digest kaynağı bulunamadı."
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0
        var bytes = 0L

        collectFiles(root).forEach { file ->
            count += 1
            require(count <= MAX_FILES) {
                "Workspace dosya sayısı sınırı aşıldı."
            }

            bytes += file.length()
            require(bytes <= MAX_TOTAL_BYTES) {
                "Workspace toplam boyut sınırı aşıldı."
            }

            val relative = file.relativeTo(root).invariantSeparatorsPath
            digest.update(relative.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.update(0.toByte())
        }

        return digest.digest()
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun scaffoldAndroid(
        projectRoot: File,
        blueprint: AppForgeAgentBlueprint
    ) {
        val mainActivity = projectRoot
            .walkTopDown()
            .firstOrNull {
                it.isFile &&
                    it.name == "MainActivity.kt" &&
                    "/src/main/java/" in it.invariantSeparatorsPath
            }
            ?: error("Android codegen MainActivity.kt üretmedi.")

        val packageName = Regex(
            """(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)\s*$"""
        ).find(mainActivity.readText())
            ?.groupValues
            ?.getOrNull(1)
            ?: error("Android MainActivity package adı okunamadı.")

        require(packageName == safeApplicationId(blueprint.appName)) {
            "Android codegen package adı build package adıyla eşleşmiyor."
        }

        val manifest = File(projectRoot, "app/src/main/AndroidManifest.xml")
        require(manifest.isFile) {
            "Android codegen manifest üretmedi."
        }

        manifest.writeText(
            manifest.readText()
                .replace(
                    "@style/Theme.Material3.DayNight.NoActionBar",
                    "@style/Theme.AppForge.Generated"
                )
        )

        writeText(
            File(projectRoot, "settings.gradle.kts"),
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

            rootProject.name = "AppForgeGenerated"
            include(":app")
            """.trimIndent()
        )

        writeText(
            File(projectRoot, "build.gradle.kts"),
            """
            plugins {
                id("com.android.application") version "9.1.1" apply false
                id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
            }
            """.trimIndent()
        )

        writeText(
            File(projectRoot, "gradle.properties"),
            """
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            android.nonTransitiveRClass=true
            """.trimIndent()
        )

        writeText(
            File(projectRoot, "app/build.gradle.kts"),
            """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.plugin.compose")
            }

            android {
                namespace = "$packageName"
                compileSdk = 37

                defaultConfig {
                    applicationId = "$packageName"
                    minSdk = 26
                    targetSdk = 37
                    versionCode = 1
                    versionName = "1.0.0"
                }

                buildFeatures {
                    compose = true
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            dependencies {
                val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
                implementation(composeBom)
                implementation("androidx.activity:activity-compose:1.13.0")
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.foundation:foundation")
                implementation("androidx.compose.material3:material3")
            }
            """.trimIndent()
        )

        writeText(
            File(projectRoot, "app/src/main/res/values/styles.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <style
                    name="Theme.AppForge.Generated"
                    parent="android:style/Theme.Material.Light.NoActionBar" />
            </resources>
            """.trimIndent()
        )
    }

    private fun scaffoldFlutter(
        projectRoot: File,
        blueprint: AppForgeAgentBlueprint
    ) {
        require(File(projectRoot, "lib/main.dart").isFile) {
            "Flutter codegen lib/main.dart üretmedi."
        }

        val packageName = safeApplicationId(blueprint.appName)
        val flutterName = packageName.substringAfterLast('.')

        writeText(
            File(projectRoot, "pubspec.yaml"),
            """
            name: $flutterName
            description: AppForge Unified Agent generated application
            publish_to: "none"
            version: 1.0.0+1

            environment:
              sdk: ">=3.5.0 <4.0.0"

            dependencies:
              flutter:
                sdk: flutter

            flutter:
              uses-material-design: true
            """.trimIndent()
        )

        writeText(
            File(projectRoot, "android/settings.gradle.kts"),
            """
            pluginManagement {
                val flutterSdkPath = run {
                    val properties = java.util.Properties()
                    file("local.properties").inputStream().use {
                        properties.load(it)
                    }
                    val flutterSdkPath = properties.getProperty("flutter.sdk")
                    require(flutterSdkPath != null) {
                        "flutter.sdk not set in local.properties"
                    }
                    flutterSdkPath
                }

                includeBuild("${'$'}flutterSdkPath/packages/flutter_tools/gradle")

                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            plugins {
                id("dev.flutter.flutter-plugin-loader") version "1.0.0"
                id("com.android.application") version "8.11.1" apply false
                id("org.jetbrains.kotlin.android") version "2.2.20" apply false
            }

            include(":app")
            """.trimIndent()
        )

        writeText(
            File(projectRoot, "android/build.gradle.kts"),
            """
            allprojects {
                repositories {
                    google()
                    mavenCentral()
                }
            }

            val newBuildDir =
                rootProject.layout.buildDirectory
                    .dir("../../build")
                    .get()

            rootProject.layout.buildDirectory.value(newBuildDir)

            subprojects {
                val newSubprojectBuildDir =
                    newBuildDir.dir(project.name)
                project.layout.buildDirectory.value(newSubprojectBuildDir)
            }

            subprojects {
                project.evaluationDependsOn(":app")
            }

            tasks.register<Delete>("clean") {
                delete(rootProject.layout.buildDirectory)
            }
            """.trimIndent()
        )

        writeText(
            File(projectRoot, "android/gradle.properties"),
            """
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            android.enableJetifier=true
            """.trimIndent()
        )

        writeText(
            File(projectRoot, "android/app/build.gradle.kts"),
            """
            plugins {
                id("com.android.application")
                id("kotlin-android")
                id("dev.flutter.flutter-gradle-plugin")
            }

            android {
                namespace = "$packageName"
                compileSdk = flutter.compileSdkVersion
                ndkVersion = flutter.ndkVersion

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                kotlinOptions {
                    jvmTarget = JavaVersion.VERSION_17.toString()
                }

                defaultConfig {
                    applicationId = "$packageName"
                    minSdk = 26
                    targetSdk = flutter.targetSdkVersion
                    versionCode = flutter.versionCode
                    versionName = flutter.versionName
                }
            }

            flutter {
                source = "../.."
            }
            """.trimIndent()
        )

        val kotlinPath = packageName.replace('.', '/')
        writeText(
            File(
                projectRoot,
                "android/app/src/main/kotlin/$kotlinPath/MainActivity.kt"
            ),
            """
            package $packageName

            import io.flutter.embedding.android.FlutterActivity

            class MainActivity : FlutterActivity()
            """.trimIndent()
        )

        writeText(
            File(projectRoot, "android/app/src/main/AndroidManifest.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:label="${xmlEscape(blueprint.appName)}"
                    android:icon="@mipmap/ic_launcher">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true"
                        android:launchMode="singleTop"
                        android:theme="@style/LaunchTheme"
                        android:configChanges="orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|locale|layoutDirection|fontScale|screenLayout|density|uiMode"
                        android:hardwareAccelerated="true"
                        android:windowSoftInputMode="adjustResize">
                        <meta-data
                            android:name="io.flutter.embedding.android.NormalTheme"
                            android:resource="@style/NormalTheme" />
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <meta-data
                        android:name="flutterEmbedding"
                        android:value="2" />
                </application>
            </manifest>
            """.trimIndent()
        )

        writeText(
            File(projectRoot, "android/app/src/main/res/values/styles.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <style name="LaunchTheme" parent="@android:style/Theme.Light.NoTitleBar">
                    <item name="android:windowBackground">@android:color/white</item>
                </style>
                <style name="NormalTheme" parent="@android:style/Theme.Light.NoTitleBar">
                    <item name="android:windowBackground">@android:color/white</item>
                </style>
            </resources>
            """.trimIndent()
        )

        // Flutter requires the icon resource referenced by the manifest.
        writeText(
            File(projectRoot, "android/app/src/main/res/values/colors.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="appforge_icon_bg">#FFFFFF</color>
            </resources>
            """.trimIndent()
        )

        // Avoid requiring a generated launcher icon: remove the icon attribute.
        val manifest = File(
            projectRoot,
            "android/app/src/main/AndroidManifest.xml"
        )
        manifest.writeText(
            manifest.readText()
                .replace(
                    "\n                    android:icon=\"@mipmap/ic_launcher\"",
                    ""
                )
        )
    }

    private fun scaffoldReactNative(
        projectRoot: File,
        blueprint: AppForgeAgentBlueprint
    ) {
        require(File(projectRoot, "App.tsx").isFile) {
            "React Native codegen App.tsx üretmedi."
        }

        val packageName = safeApplicationId(blueprint.appName)
        val slug = packageName.substringAfterLast('.').replace('_', '-')

        writeText(
            File(projectRoot, "package.json"),
            """
            {
              "name": "${jsonEscape(slug)}",
              "version": "1.0.0",
              "private": true,
              "main": "node_modules/expo/AppEntry.js",
              "scripts": {
                "start": "expo start"
              },
              "dependencies": {
                "expo": "~54.0.0",
                "react": "19.1.0",
                "react-native": "0.81.4"
              },
              "devDependencies": {
                "@types/react": "~19.1.10",
                "typescript": "~5.9.2"
              }
            }
            """.trimIndent()
        )

        writeText(
            File(projectRoot, "app.json"),
            """
            {
              "expo": {
                "name": "${jsonEscape(blueprint.appName.take(80))}",
                "slug": "${jsonEscape(slug)}",
                "version": "1.0.0",
                "orientation": "default",
                "android": {
                  "package": "$packageName"
                }
              }
            }
            """.trimIndent()
        )

        writeText(
            File(projectRoot, "tsconfig.json"),
            """
            {
              "extends": "expo/tsconfig.base",
              "compilerOptions": {
                "strict": true
              }
            }
            """.trimIndent()
        )
    }

    private fun copyDirectoryContents(
        sourceRoot: File,
        targetRoot: File
    ) {
        var count = 0
        var totalBytes = 0L

        fun visit(
            source: File,
            relative: String
        ) {
            require(!Files.isSymbolicLink(source.toPath())) {
                "Symbolic link build girdisinde yasak: $relative"
            }

            val canonical = source.canonicalFile
            require(isInside(sourceRoot, canonical)) {
                "Kaynak klasörü dışına çıkış engellendi: $relative"
            }

            if (source.isDirectory) {
                File(targetRoot, relative)
                    .takeIf { relative.isNotBlank() }
                    ?.mkdirs()

                source.listFiles()
                    .orEmpty()
                    .sortedBy { it.name }
                    .forEach { child ->
                        val childRelative =
                            if (relative.isBlank()) {
                                child.name
                            } else {
                                "$relative/${child.name}"
                            }
                        visit(child, childRelative)
                    }
                return
            }

            require(source.isFile) {
                "Desteklenmeyen build girdisi: $relative"
            }

            count += 1
            require(count <= MAX_FILES) {
                "Build proje dosya sayısı sınırı aşıldı."
            }

            totalBytes += source.length()
            require(totalBytes <= MAX_TOTAL_BYTES) {
                "Build proje toplam boyut sınırı aşıldı."
            }

            val target = File(targetRoot, relative)
            target.parentFile?.mkdirs()
            source.inputStream().buffered().use { input ->
                target.outputStream().buffered().use { output ->
                    input.copyTo(output, 64 * 1024)
                }
            }
        }

        visit(sourceRoot, "")
    }

    private fun zipProject(
        projectRoot: File,
        zipFile: File
    ) {
        val files = collectFiles(projectRoot)
        require(files.size <= MAX_FILES) {
            "ZIP dosya sayısı sınırı aşıldı."
        }

        val total = files.sumOf { it.length() }
        require(total <= MAX_TOTAL_BYTES) {
            "ZIP kaynak boyutu sınırı aşıldı."
        }

        zipFile.parentFile?.mkdirs()
        ZipOutputStream(
            zipFile.outputStream().buffered()
        ).use { zip ->
            files.forEach { file ->
                require(!Files.isSymbolicLink(file.toPath())) {
                    "ZIP symbolic link içeremez."
                }

                val name = file
                    .relativeTo(projectRoot)
                    .invariantSeparatorsPath

                zip.putNextEntry(ZipEntry(name))
                file.inputStream().buffered().use { input ->
                    input.copyTo(zip, 64 * 1024)
                }
                zip.closeEntry()
            }
        }
    }

    private fun collectFiles(root: File): List<File> {
        val canonicalRoot = root.canonicalFile
        val result = mutableListOf<File>()

        fun visit(current: File) {
            require(!Files.isSymbolicLink(current.toPath())) {
                "Symbolic link build ağacında yasak."
            }
            require(isInside(canonicalRoot, current.canonicalFile)) {
                "Build ağacı kök dışına çıkamaz."
            }

            if (current.isDirectory) {
                current.listFiles()
                    .orEmpty()
                    .sortedBy { it.name }
                    .forEach(::visit)
            } else if (current.isFile) {
                result += current
            }
        }

        visit(canonicalRoot)
        return result
    }

    private fun isInside(
        root: File,
        candidate: File
    ): Boolean {
        val canonicalRoot = root.canonicalFile
        val canonicalCandidate = candidate.canonicalFile
        return canonicalCandidate == canonicalRoot ||
            canonicalCandidate.path.startsWith(
                canonicalRoot.path.trimEnd(File.separatorChar) +
                    File.separator
            )
    }

    private fun writeText(
        file: File,
        content: String
    ) {
        file.parentFile?.mkdirs()
        file.writeText(
            content.replace("\r\n", "\n")
                .let { if (it.endsWith('\n')) it else "$it\n" }
        )
    }

    private fun xmlEscape(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun jsonEscape(raw: String): String = raw
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")
}
