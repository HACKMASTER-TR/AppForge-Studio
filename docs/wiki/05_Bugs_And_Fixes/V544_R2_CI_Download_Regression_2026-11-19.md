---
type: bug
status: active
project: AppForge Studio
created: 2026-11-19
updated: 2026-11-19
last_verified: 2026-11-19
confidence: high
tags:
  - v544
  - ci
  - r2
  - kotlin
  - maven
related:
  - "[[Bug_Index]]"
  - "[[Universal_Download_V4]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/ModuleArtifactBroker.kt"
  - "android-app/app/src/test/java/com/appforge/studio/build/V9R2ObjectTransferTest.kt"
  - "quality/tools/v9_pinned_kotlin_compiler.py"
  - "android-app/settings.gradle.kts"
  - ".github/workflows/v9-isolated3-inplace-upgrade-v544.yml"
  - ".github/workflows/appforge-stability-gate.yml"
---

# V544 R2 mobil aktarım ve Android CI bağımlılık hataları

## Kanıt (2026-11-19)

- Kaynak PR #89 SHA `0cc9537ab46e109a5254dc7591a2a34a76792308`; imzalı CI `44529201088` **FAIL**: sabit JetBrains SHA-256 ile alınan Kotlin 2.4.10 compiler JAR sonrasında resmî Maven Central POM isteği `HTTPError` döndü. İmzalı APK üretilmedi.
- Stability CI `44529217005` **FAIL**: R2 origin değiştiği halde `ModuleArtifactBroker.allowed()` ve `isR2` eski `workers.dev` hostunu kullanıyordu. `V9R2ObjectTransferTest` iki testte başarısız oldu; native transfer yeni hostu kabul etmiyordu.
- Android CI zaman zaman `com.google.zxing:core:3.5.3` ve `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4` çözemedi. Bu sürümler gerçek Maven bağımlılıklarıdır; DNS/ayna erişim durumu HTTP loguyla teyit edilmeden paket sürümü değiştirilmez.

## Sınırlı onarım

1. Tek onaylı HTTPS R2 hostu `packages.appforgecloud.com`; SHA-256 içeriğe adreslenen yol ve Range kontrolleri korunur. Eski host negatif testte reddedilir. Üretim veri yolu veya imzalı release manifest değiştirilmez.
2. Kotlin 2.4.10 compiler JAR'ın boyut ve JetBrains SHA-256 sabitleri korunur; resmî POM önce Maven Central'dan sınırlı yeniden denemeyle, kesinti varsa Google'ın belgelenmiş Maven Central GCS aynasından HTTPS, tam hostname ve XML GAV doğrulamasıyla alınır. POM verisi JAR'ın hash kontrolünün yerine geçmez.
3. GCS Maven Central deposu yalnız açık `appforgeCIMavenGcs` Gradle parametresiyle ve yalnız ZXing / kotlinx gruplarına izinli. Varsayılan mobil/prod Gradle bağımlılık akışına dokunulmaz.
4. Geçiş tamamlandı ilan edilmez: exact-SHA Stability, imzalı V544 build ve APK sertifika/version doğrulaması PASS, ardından mobil veride gerçek ARM64 Node.js kurulumu gerekir.

## Tekrarında ilk bakılacaklar

GitHub CI iş adımı, hatalı Maven koordinatı ve HTTP yanıtı, `V9R2ObjectTransferTest` ile broker allowlist'i, `python3 scripts/generate-second-brain-snapshot.py --check`, imzalı APK kimlik kanıtı. Android uygulamasını kaldırma veya verileri temizleme önerilmez.
