---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-30
updated: 2026-09-30
last_verified: 2026-09-30
confidence: high
tags:
  - universal-project
  - cross-platform
  - android
  - windows
  - device-build
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/io/ProjectTechnologyDetector.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "quality/tests/universal_cross_platform_project_contract.test.js"
---

# Universal Cross-Platform Project V1

`appforge.universal.json` bir proje içinde iki yerel hedef tanımlar:

- `android`: mevcut native Android Gradle projesi
- `windows`: statik web tabanlı Windows Portable EXE istemcisi

AppForge `universal-cross-platform` motorunda APK, AAB ve WINDOWS_EXE çıktılarını aynı build kaydında üretebilir. Android kaynak kodu Windows'a dönüştürülmez; iki hedef tek proje kontratında orkestre edilir.

Güvenlik: hedef yolları proje kökü altında kalmak zorundadır; `..`, mutlak yollar ve sürücü yolları reddedilir.

## Universal Output UI V1.1

- Universal proje içe aktarıldığında varsayılan çıktı `all` olur.
- Derleme ekranında Universal projeler için `TÜMÜ` seçeneği görünür.
- `TÜMÜ`, aynı Build ID altında APK + AAB + Windows Portable EXE ister.
- Native Android projelerde Windows EXE yanlışlıkla etkinleştirilmez; Universal motor kendi Windows hedefi nedeniyle EXE uyumlu kabul edilir.
- Çıktı seçimi proje kaydıyla birlikte kalıcıdır.

## Universal V1.2 — Gradle project CWD fix

Universal Android targets are nested under the universal source root.
DeviceBuildEngine now validates the exact Android target directory,
changes into that directory before Gradle starts, and invokes Gradle
with `-p .`. This prevents PRoot builds from accidentally resolving
the Gradle root as `/`.

Physical CAMForge universal APK/AAB/EXE acceptance remains pending.

## Universal V1.3 — exact Android project mount

CAMForge fiziksel kabulünde host tarafında Universal Android hedefi ve
`settings.gradle.kts` doğrulanmasına rağmen PRoot içinde
`APPFORGE_GRADLE_PROJECT_DIR_MISSING` görüldü.

V1.3, Universal Android Gradle hedefini parent build workspace yerine
doğrudan PRoot `/workspace` köküne bind eder.

`buildGradleProject` ayrıca project ve workspace yollarını canonical
biçimde doğrular ve relative yolu canonical köklerden üretir.

Beklenen fiziksel kabul logları:

- `APPFORGE_GRADLE_MOUNT_SCOPE=PROJECT_ROOT`
- `APPFORGE_GRADLE_PROJECT_REL=.`
- `APPFORGE_GRADLE_WORKSPACE_MOUNT=/workspace`
- `APPFORGE_GRADLE_PROJECT_CWD=/workspace`

CAMForge tarafındaki NC/G-code çıktısı ve makine transferi kapalı kalır.
