package com.appforge.studio.build

internal enum class DeviceArtifactKind(
    val label: String
) {
    APK("Android APK"),
    AAB("Android AAB"),
    WINDOWS_EXE("Windows Portable EXE")
}

internal enum class DeviceBuildSupport {
    READY,
    EXPERIMENTAL,
    PLANNED,
    EXTERNAL_TOOL_REQUIRED
}

internal data class DeviceBuildCapability(
    val engine: String,
    val technologies: Set<String>,
    val readyOutputs: Set<DeviceArtifactKind>,
    val support: DeviceBuildSupport,
    val note: String
)

internal object DeviceBuildCapabilities {

    val all:
        List<DeviceBuildCapability> =
        listOf(

            DeviceBuildCapability(
                engine =
                    "webview-static",
                technologies =
                    setOf(
                        "web-static",
                        "html",
                        "css",
                        "javascript",
                        "webview"
                    ),
                readyOutputs =
                    setOf(
                        DeviceArtifactKind.APK,
                        DeviceArtifactKind.AAB,
                        DeviceArtifactKind.WINDOWS_EXE
                    ),
                support =
                    DeviceBuildSupport.READY,
                note =
                    "Statik Web / WebView cihaz üzerinde APK, AAB ve doğrulanmış generic host ile Windows Portable EXE üretebilir."
            ),

            DeviceBuildCapability(
                engine =
                    "node-web",
                technologies =
                    setOf(
                        "node-web",
                        "react",
                        "vue",
                        "svelte",
                        "vite"
                    ),
                readyOutputs =
                    setOf(
                        DeviceArtifactKind.APK,
                        DeviceArtifactKind.AAB,
                        DeviceArtifactKind.WINDOWS_EXE
                    ),
                support =
                    DeviceBuildSupport.READY,
                note =
                    "npm tabanlı statik web çıktıları cihaz üzerinde APK/AAB ve Windows Portable EXE olarak paketlenebilir."
            ),

            DeviceBuildCapability(
                engine =
                    "android-gradle",
                technologies =
                    setOf(
                        "android",
                        "android-gradle",
                        "kotlin",
                        "java"
                    ),
                readyOutputs =
                    setOf(
                        DeviceArtifactKind.APK,
                        DeviceArtifactKind.AAB
                    ),
                support =
                    DeviceBuildSupport.READY,
                note =
                    "Native Android Kotlin/Java projeleri cihaz üzerinde Gradle ile derlenir."
            ),

            DeviceBuildCapability(
                engine =
                    "python-android",
                technologies =
                    setOf(
                        "python",
                        "python-android"
                    ),
                readyOutputs =
                    setOf(
                        DeviceArtifactKind.APK,
                        DeviceArtifactKind.AAB
                    ),
                support =
                    DeviceBuildSupport.READY,
                note =
                    "Python projeleri Chaquopy Android paketi üzerinden derlenir."
            ),

            DeviceBuildCapability(
                engine =
                    "android-ndk",
                technologies =
                    setOf(
                        "c",
                        "cpp",
                        "c++",
                        "cmake",
                        "android-ndk"
                    ),
                readyOutputs =
                    emptySet(),
                support =
                    DeviceBuildSupport.EXPERIMENTAL,
                note =
                    "C/C++ Android NDK katmanı V3 mimarisinde ayrıldı; NDK/CMake device acceptance tamamlanmadan READY olmayacak."
            ),

            DeviceBuildCapability(
                engine =
                    "react-native",
                technologies =
                    setOf(
                        "react-native"
                    ),
                readyOutputs =
                    emptySet(),
                support =
                    DeviceBuildSupport.EXPERIMENTAL,
                note =
                    "React Native native Android build için Node + Android + NDK/CMake/Hermes gerekir. Android-hosted ARM64 Linux üzerinde resmi NDK host kabulü bulunmadığından APK/AAB çıktıları fiziksel toolchain kabulüne kadar kapalıdır."
            ),

            DeviceBuildCapability(
                engine =
                    "expo",
                technologies =
                    setOf(
                        "expo"
                    ),
                readyOutputs =
                    emptySet(),
                support =
                    DeviceBuildSupport.EXPERIMENTAL,
                note =
                    "Expo prebuild + React Native native Android pipeline gerektirir. ARM64 device-host NDK/CMake yolu fiziksel olarak doğrulanmadan APK/AAB çıktıları açılmayacak."
            ),

            DeviceBuildCapability(
                engine =
                    "flutter",
                technologies =
                    setOf(
                        "flutter",
                        "dart"
                    ),
                readyOutputs =
                    emptySet(),
                support =
                    DeviceBuildSupport.PLANNED,
                note =
                    "Flutter/Dart ARM64 device toolchain doğrulaması bekliyor."
            ),

            DeviceBuildCapability(
                engine =
                    "dotnet-android",
                technologies =
                    setOf(
                        "dotnet-android",
                        "csharp-android",
                        "c#-android"
                    ),
                readyOutputs =
                    emptySet(),
                support =
                    DeviceBuildSupport.PLANNED,
                note =
                    ".NET Android SDK/workload cihazda doğrulanmadan READY olmayacak."
            ),

            DeviceBuildCapability(
                engine =
                    "dotnet-maui",
                technologies =
                    setOf(
                        "dotnet-maui",
                        "maui"
                    ),
                readyOutputs =
                    emptySet(),
                support =
                    DeviceBuildSupport.PLANNED,
                note =
                    ".NET MAUI için Android ve Windows hedefleri platforma özel toolchain doğrulaması gerektiriyor."
            ),

            DeviceBuildCapability(
                engine =
                    "windows-web",
                technologies =
                    setOf(
                        "windows-web",
                        "portable-exe"
                    ),
                readyOutputs =
                    emptySet(),
                support =
                    DeviceBuildSupport.PLANNED,
                note =
                    "Windows Portable EXE cihaz-local host packager doğrulanınca WINDOWS_EXE çıktısı açılacak."
            ),

            DeviceBuildCapability(
                engine =
                    "unity",
                technologies =
                    setOf(
                        "unity",
                        "csharp-unity"
                    ),
                readyOutputs =
                    emptySet(),
                support =
                    DeviceBuildSupport.EXTERNAL_TOOL_REQUIRED,
                note =
                    "Unity Editor'ın cihaz Linux runtime'ında desteklenen resmi ARM64 build host'u yok; AppForge bunu çalışıyormuş gibi göstermeyecek."
            )
        )


    fun forEngine(
        engine: String
    ): DeviceBuildCapability? {

        val normalized =
            engine
                .trim()
                .lowercase()
                .ifBlank {
                    "webview-static"
                }

        return all.firstOrNull {
            it.engine ==
                normalized
        }
    }


    fun forTechnology(
        technology: String
    ): DeviceBuildCapability? {

        val normalized =
            technology
                .trim()
                .lowercase()

        if (
            normalized.isBlank()
        ) {
            return null
        }

        return all.firstOrNull {
            normalized in
                it.technologies
        }
    }


    fun requestedOutputs(
        value: String
    ): Set<DeviceArtifactKind> =
        when (
            value
                .trim()
                .lowercase()
        ) {
            "",
            "both",
            "apk+aab",
            "android" ->
                setOf(
                    DeviceArtifactKind.APK,
                    DeviceArtifactKind.AAB
                )

            "apk" ->
                setOf(
                    DeviceArtifactKind.APK
                )

            "aab" ->
                setOf(
                    DeviceArtifactKind.AAB
                )

            "exe",
            "windows-exe" ->
                setOf(
                    DeviceArtifactKind.WINDOWS_EXE
                )

            "all",
            "apk+aab+exe" ->
                setOf(
                    DeviceArtifactKind.APK,
                    DeviceArtifactKind.AAB,
                    DeviceArtifactKind.WINDOWS_EXE
                )

            else ->
                error(
                    "Bilinmeyen build output: $value"
                )
        }


    fun outputLabels(
        outputs:
            Collection<DeviceArtifactKind>
    ): String =
        outputs
            .joinToString(
                ", "
            ) {
                it.label
            }
}
