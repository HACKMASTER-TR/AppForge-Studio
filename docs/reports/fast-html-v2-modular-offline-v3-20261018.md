# Fast HTML V2 + Modüler Çevrimdışı Derleme V3 — 2026-10-18

## Kabul durumu

**KISMİ UYGULAMA; tam V3 kabulü tamamlanmadı.** Gerçek kaynak değişiklikleri ve host testleri uygulanmıştır. Aşağıdaki eksikler nedeniyle bütün gereksinimlerin tamamlandığı veya telefon için yeni APK'nin hazır olduğu söylenmez. Fiziksel Samsung S24 Ultra / Android 16 ve canlı dış servis kabulü **NOT_RUN**.

Depo: HACKMASTER-TR/AppForge-Studio. Yalnız çalışma dalı:
`feat/fast-html-v2-full-capabilities-20261017`.
Başlangıç: `1e922b835b39f9f0d948e780ff4020f594124afd`.
Çalışma ağacı: `/workspace/AppForge-Studio-v3`.
Referans başarılı CI: **39072864903**; bu eski run yeni V3 değişikliklerini doğrulamaz.

## 1. Kök neden

- Kaynak analizi, dosya yükleme/capture/WebRTC ayrımını ürün desteğiyle ortak değerlendirmeden izinleri taslağa aktarabiliyordu.
- FastHtmlApkPolicy'nin uygun olmayan seçimlerde false dönmesi normal araç zincirine sessiz yönlendirme oluşturabiliyordu. Wizard'ın açıklaması gerçek motorla ayrışabiliyordu.
- `installReadyComponents()` bütün motorları ardışık kuruyordu; Windows Portable için bile genel SDK onayı akışı vardı.
- Eski v1 READY marker'ı gerçek sürüm/dosya bütünlüğü kanıtı değildi. Rastgele projelerin bağımlılık önbelleği genel araç kurulumu ile eşdeğer değildir.

## 2. Motor yönlendirmesi

`normalHtmlEngine` açık kullanıcı tercihi eklendi. Yerel desteklenen HTML APK/AAB/BOTH gömülü hızlı şablonları kullanır. Uyumsuz hızlı seçim merkezi doğrulamada engellenir; başka motora sessiz geçiş yapılmaz. Normal motor tercihi proje kitaplığında korunur.

WizardReadinessEvaluator, WebviewProjectSettings, modül çözümleyici ve DeviceBuildEngine aynı destek kararını uygular. Paketlenecek gerçek HTML/JS dosyaları da yeniden incelenir. Önceki kaydedilmiş uygunsuz seçimler otomatik silinmez; kullanıcı açıkça düzeltmelidir. İsteğe bağlı seçimleri temizlemek kaynak gereksinimini temizlemez.

Normal derlemede `allowDependencyDownloads` varsayılan false; kullanıcı oturum içinde açıkça onay vermeden proje npm/Gradle bağımlılık indirmesine izin verilmez. Bu onay proje dosyasından içe aktarılmaz ve kalıcı kaydedilmez. Bu ek güvence, proje bazında önbellek hazırlama ekranının tamamlandığı anlamına gelmez.

## 3. Merkezi destek matrisi

`FeatureSupportMatrix.kt`: SUPPORTED, MANIFEST_ONLY, UNSUPPORTED, EXTERNAL_CONFIG_REQUIRED, NORMAL_ENGINE_REQUIRED. Destek kararı kaynak/motor/çıktı/JS-köprü güveni ve mevcut politika kontrolleriyle birleştirilir. SDK sınırları, imza ve dış servis alanlarının ayrıntılı doğrulaması mevcut WebviewProjectSettings/FastHtml politikalarında korunur; tüm koşullar tek dosyaya taşınmamıştır.

UI anahtarları bu kararın selectable alanını kullanır; callback de korumalıdır. Toplu seçimler aynı kabul filtresinden geçer. Kaynak önerileri yalnız SUPPORTED için açılabilir. Eski açık/uyumsuz tercihler görünür ve derlemeyi engeller. UI sözleşme testleri kaynak düzeyindedir; gerçek Compose tıklama testi NOT_RUN.

## 4. Pasifleştirilen seçenekler ve gerçek destek

| Durum | Sonuç |
|---|---|
| Yerel Fast HTML kamera / mikrofon WebRTC | NORMAL_ENGINE_REQUIRED; otomatik açılmaz; uygun kaynak/motor gerekir |
| capture, audio capture, watchPosition, belirsiz getUserMedia constraints | UNSUPPORTED; kaynak gerektiriyorsa engel |
| Web Bluetooth, Contacts Picker, AppForgeCalendar, Web NFC NDEFReader | UNSUPPORTED; manifest izni API yerine geçmez |
| Ek izinler | MANIFEST_ONLY; çalışma zamanı API'si olarak sunulmaz |
| AdMob, Firebase Analytics/Crashlytics/Messaging, UMP | UNSUPPORTED; sahte SDK desteği yok |
| Fast HTML Play Billing | EXTERNAL_CONFIG_REQUIRED; gerçek Billing 9.1.0 yolu ve sunucu doğrulama sınırları korunur |
| Normal motor Billing / QR / medya köprüsü | Mevcut entegrasyon sınırına göre UNSUPPORTED |
| Normal motor NFC / ağ / Wake Lock / bildirim seçimi | MANIFEST_ONLY |
| JS/köprü/uzak köprü güven şartı eksik yerel API | UNSUPPORTED; güvenlik kontrolleri korunur |
| Fast APK özel keystore yolu | NORMAL_ENGINE_REQUIRED; uygun AAB/BOTH mevcut kontrolleri korunur |

Geçerli HTTPS WebRTC yolu mevcut HTTPS/izin politikasına tabidir. Yerel capture/WebRTC için yeni güvenli kamera implementasyonu bu görevde tamamlanmadı; SUPPORTED yapılmadı. Destek kararları bütün normal Native kaynak projelerinin kendi uyguladığı API'leri analiz ederek ispatlamaz; kaynak sahibinin uygulaması ile AppForge tarafından enjekte edilen özellik ayrımı korunmalıdır.

## 5. Korunan entegrasyonlar

Mevcut dosya/çoklu dosya seçici, PDF/CSV oluşturma-kaydetme-paylaşma, pano yazma, titreşim, bildirim, ağ, konum, QR, NFC, yerel arka plan ses, Wake Lock, Deep Link, ikon/splash/tema/yön/tam ekran ve APK/AAB/BOTH kodları kaldırılmadı. Mevcut origin/token/gesture/foreground güven kapıları ve Billing receipt sınırları korunur. Yeni üçüncü taraf servis SDK'sı eklenmedi. Host regresyonları cihaz üzerindeki tüm API davranışlarının fiziksel kabulü değildir.

## 6. HTML analizi

`HtmlCapabilityFindings` dosya inputunu izin gerektirmeyen seçici olarak tanır; capture ve audio accept ayrılır. Literal getUserMedia video/audio, belirsiz değişken constraints, current/watch geolocation, QR, NFC ve diğer API bulguları ayrı tutulur. Yorumlar ayıklanır; tarama sınırlı statik bulgudur, tam JavaScript AST veya dinamik yürütme analizi değildir. Dinamik/obfuscate edilmiş kaynak için eksiksiz özellik tespit garantisi yoktur.

Algılanmamış ek tehlikeli izinler otomatik açılmaz. İzin sayısı gerçekte seçilen kanonik manifest izinleriyle hesaplanır.

## 7–9. Modül envanteri, sürümler ve dağıtım kaynakları

| Modül | Gerçek sürüm / içerik | Kaynak | Bilinen indirme boyutu |
|---|---|---|---|
| FAST_HTML | CI/AGP APK ve AAB şablonları, min26/target37 | Uygulamaya gömülü, TemplateIntegrity | **0 ek bayt**; APK içindeki şablonlar sıfır boyutlu değildir |
| LINUX_RUNTIME | Device Build Runtime V3; mimariye bağlı Ubuntu rootfs | LinuxRuntimeManifestRegistry mevcut doğrulanmış manifest | Bu raporda yeniden ölçülmedi; bilinmiyor |
| ANDROID_CORE | API37.0_r02; Build Tools36.0.0; JDK17.0.20.1+1; Gradle9.3.1 ve8.14.3; cmdline15859902 | Google, Adoptium, services.gradle.org; mevcut ARM build-tools kaynağı | Toplam bilinmiyor |
| NODE_WEB | Node22.23.3, dağıtımla gelen npm | nodejs.org, mimariye özel sabit SHA-256 | Toplam bilinmiyor |
| PYTHON_ANDROID | Python3.12 / Chaquopy17.0.0 | Ubuntu APT / Maven / mevcut prewarm | Toplam ve APT patch sürümü çözümlemeye bağlı |
| EXPO_REACT_NATIVE | Expo SDK54 / RN0.81 ailesi; NDK27.1.12297006 r27b; CMake3.22.1 | Google / Ubuntu ARM host / proje npm kilidi | Proje ve mimariye bağlı, bilinmiyor |
| WINDOWS_PORTABLE | windows-host-v1-c7e4b2a | Deponun doğrulanmış host release dosyası | **375039759 bayt**; sabit kaynak metadata'sı, bu görevde yeniden ağdan ölçülmedi |
| WINDOWS_NATIVE | windows-native-v1; MinGW-w64 POSIX / CMake / Ninja | Ubuntu APT | Dinamik paket sürümleri/toplam bilinmiyor; EXPERIMENTAL |

Disk tahmini doğrulanmamış modüllerde sayı verilmez. Bilinen host boyutu yalnız host dosyasının asgari alanıdır; indirme/staging alanı ayrıca gereklidir. UI gerçek kullanılabilir disk alanını okur. Eksiksiz mimariye özel depolama planlaması henüz yoktur.

Kaynak URL'leri:

- Node: `https://nodejs.org/dist/v22.23.3/node-v22.23.3-linux-{arm64|x64}.tar.xz`.
- JDK: `https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/` altındaki mimariye özel arşiv.
- Gradle: `https://services.gradle.org/distributions/gradle-{9.3.1|8.14.3}-bin.zip` ve mevcut .sha256 doğrulaması.
- Platform: `https://dl.google.com/android/repository/platform-37.0_r02.zip`.
- Build tools: `https://dl.google.com/android/repository/build-tools_r36_linux.zip`.
- NDK: `https://dl.google.com/android/repository/android-ndk-r27b-linux.zip`.
- Windows host: `https://github.com/HACKMASTER-TR/AppForge-Studio/releases/download/windows-host-v1-c7e4b2a/AppForge-Windows-Host-v1.exe`.
- ARM araçlar: mevcut `https://github.com/Commit451/android-arm-build-tools/releases/download/platform-tools-36.0.0` kaynağı.

Node arm64 SHA-256: `a44aeb94849a299b22df10b9e622ec2f605c2183501bc40590705131de7c740f`.
Node x64 SHA-256: `df450af89261115ef9f9e3830c3eeb2cc9213b63c720b1af623cb5dcbe2e02de`.
Windows host SHA-256: `f4aa9c8bee1b919cfb7e3cd6e8073ab4097b0b8e050bc8fb3758f5a198b98c1e`.
Cmdline SHA-256: `4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583`.
Bunlar mevcut sabitlerden doğrulanmıştır; yeniden indirildi iddiası yoktur.

## 10. Bağımlılık grafiği

- Fast HTML APK/AAB/BOTH → FAST_HTML; Linux/SDK/JDK/Gradle yok.
- Android Gradle → ANDROID_CORE → LINUX_RUNTIME.
- Node-Web Android → ANDROID_CORE + NODE_WEB; Node tek başına yalnız LINUX_RUNTIME ister.
- Python Android → PYTHON_ANDROID → ANDROID_CORE → LINUX_RUNTIME.
- Expo Android → EXPO_REACT_NATIVE → ANDROID_CORE + NODE_WEB → ortak LINUX_RUNTIME.
- Windows Portable → WINDOWS_PORTABLE; SDK lisansı ve Linux yok.
- Windows Native → WINDOWS_NATIVE → LINUX_RUNTIME.
- Karma çıktı → gerçek modüllerin tekilleştirilmiş birleşimi.

Bu grafiğin çözümlemesi test edilir. Bütün modüllerin gerçek cihaz kurulumu NOT_RUN. `install-node-module.sh` Android araçlarını çağırmaz. Python ve Expo mevcut gerçek kurulum/prewarm betiklerini kullanır; Expo/Windows Native desteğinin mevcut deneysel sınırları korunur.

## 11. Yeni paket yöneticisi

Monolitik ekran yerine modül kartları, sürüm, bağımlılık, boyut/bilinmiyor, disk, hazır/doğrulanmamış/hata, ayrı onay, tekrar deneme ve doğrulama bulunur. Projeden açıldığında yalnız plan modülleri listelenir; proje kaybolmadan geri dönülür ve tekrar önkontrol yapılır. Settings üzerinden önceden kurulum mümkündür. İlk açılış araç indirmeye zorlamaz. Windows kabul EXE'si oluşturma/kaydetme akışı korunur.

Tam güvenli kaldırma ve güncelleme akışı tamamlanmadığı için aktifmiş gibi sunulmaz; dosya sahipliği/etkin derleme kontrolü olmadan kaldırma yapılmaz.

## 12. İlerleme, hız, ETA

Yapılandırılmış ModuleProgress: moduleId, operationId, phase, bytesDownloaded, bytesTotal?, hareketli hız, ortalama hız, elapsedMs, estimatedRemainingMs?, errorCode, detail. Yüzde yalnız doğrulanabilir bayt/toplam oranından hesaplanır. Bilinmeyen toplam ve sıfır/ölçülmeyen hız için yüzde/ETA uydurulmaz. %100 indirme READY değildir.

Node ve Windows host gerçek ağ baytları ölçülür. APT/Gradle/SDK ve rootfs betiklerinin tamamı bu olay protokolüne dönüştürülmedi; bu aşamalar belirsiz ilerleme ve metin gösterir. Ortak bağımlılık tekilleştirilir; ölçülemeyen toplam kurulum için sahte ağırlıklı yüzde yoktur.

## 13. Devam, hata ve yaşam döngüsü

Node .part kimliği URL+SHA'ya bağlıdır. HTTP206 Content-Range doğrulanır, sunucu200 dönerse baştan yazılır. Tam kısmi dosya SHA ile ağa çıkmadan yayımlanabilir. Yanlış SHA dosyayı hazır yapmaz; eski çalışan hedef korunur. Atomik move doğrulamadan sonra olur. Windows mevcut güvenli Range/digest/atomik host yolu korunur ve bayt telemetrisi eklenir. Node/host indirirken kullanıcı duraklatabilir/iptal edebilir; yeniden deneme kısmi dosyayı doğrular. Tüm kurulum betiklerinde resumable download tamamlanmadı.

Uygulama kapsamlı coroutine/state flows ekran gezinmesi/döndürmede süreç içinde devam eder. İşletim sistemi süreç ölümünde devam garantisi yoktur. Foreground download service, kalıcı iş planı, otomatik network retry/backoff ve bildirim ilerlemesi bu görevde tamamlanmadı; kullanıcı önplanda tutma uyarısını görür.

## 14. Güvenlik ve geçiş

TLS kapatılmadı; güvensiz curl seçeneği eklenmedi. Node yeni HTTPS downloader sabit SHA-256, symlink/path, boyut sınırı, disk kontrolü, partial kimliği, cancellation, fd.sync ve atomik dosya geçişi uygular. Node betiği pinned arşivi doğrular, traversal listesini reddeder ve stage alanına çıkarır; var olan çakışan kurulumu silmez. Windows/rootfs mevcut doğrulama kapıları korunur.

Modül v3 attestation JSON'u sürüm, kritik dosya yolları ve SHA-256'ları içerir; atomik yazılır ve bağımlılıklar tekrar kontrol edilir. Eski marker tek başına hazır sayılmaz. Eski dosyalar silinmez. Bu, tüm kurulumun kriptografik sahiplik envanteri değildir; Gradle/npm cache içeriğinin tamamını veya her dosyayı doğrulamaz.

**Kalan güvenlik işleri:** mevcut Android platform/NDK arşivlerinin bazıları SHA-1 pinlidir; hepsi SHA-256'ya geçirilmedi. APT sürümleri tam sabitlenmedi. Paylaşılan rootfs içinde tüm APT/Gradle değişikliklerini kapsayan atomik transaction/rollback ve modüller arası tam dosya sahipliği manifesti yoktur. Güvenli kaldırma/güncelleme bu yüzden sunulmaz. Başarısız işlem READY yazmaz; fakat tüm paylaşılan araç değişikliklerinin rollback garantisi verilmez.

## 15. Değiştirilen dosyalar

- Android UI: MainActivity, OfflineBuildPackScreen, AppForgeSettingsScreens.
- Motor/politika: FeatureSupportMatrix (yeni), FastHtmlApkPolicy, WebviewProjectSettings, DeviceBuildEngine, DeviceBuildRuntimeV3, FastHtmlApkPackager.
- Modüller: OfflineModulePlan (yeni), OfflineBuildPackManager, VerifiedModuleDownload (yeni), WindowsPortableHostStore, install-node-module.sh (yeni).
- Analiz/persistence: HtmlCapabilityFindings (yeni), SourceCapabilityAnalyzer, ProjectDraft, ProjectLibrary.
- Testler: ModularOfflineV3Test, VerifiedModuleDownloadTest, modular_offline_v3_ui_contract (yeni); mevcut yönlendirme/monolitik paket beklentileri yeni açık seçim ve modüler sözleşmeye güncellendi. Hiçbir test dosyası silinmedi.
- Host smoke derleme kaynak listeleri, Wiki V3 sayfası/Index/Fast HTML ve deterministik Second Brain snapshot güncellendi.

## 16. Test kanıtı

Son doğrulama özeti aşağıdaki son kanıt bölümüne eklenir. Önceki ara koşulardaki başarısızlıklar gizlenmez: eski monolitik sözleşmeler yeni ürün davranışına göre güncellendi; yeni test geçersiz buildOutput'un modül planını çökerttiğini buldu, çıktı doğrulaması candidate çağrısının önüne alındı. Test kaldırarak PASS elde edilmedi.

Fiziksel Android16, uçak modu gerçek normal motor kurulumu/derlemesi, arka plan süreç ölümü, disk dolması ve bütün modüllerin cihaz üzerinde kurulumu: **NOT_RUN**. Canlı Firebase/AdMob/UMP/Billing sunucu/Play ürünleri: **NOT_RUN**. Host testleri bu kabulün yerine geçmez.

## 17. CI ve teslimat sınırı

Referans CI39072864903 yalnız başlangıç commit'ini doğrular. Yeni V3 CI run ID'si henüz yok. İş akışı mevcut `find` kullanımını ve araç kontrollerini korur; ZXing için `rg` bağımlılığı geri eklenmedi. Debug dispatch `publish_latest_release=false` olmalıdır. Yeni CI PASS olmadan APK telefon için hazır diye sunulmaz.

Bu ortamın shell GitHub hesabı heyomert; önceki doğrulamada yalnız okuma yetkisi vardı. HACKMASTER-TR bağlı hesabının repository metadata'sı yazma izni bildirirken bu görevde yeniden yapılan, workflow değişikliği içermeyen gerçek kaynak tree oluşturma çağrısı da **403 Resource not accessible by integration** döndürdü. Yetki reddi başka endpoint ile aşılmadı. Push/dispatch engeli giderilmeden yeni run/artifact numarası uydurulmaz.

## 18. Fiziksel kabul listesi

1. Telefonda kurulu T21 APK paket/sürüm/sertifika hash'ini karşılaştır; doğrulanmadan yükseltme kurma. Yeni yerel debug anahtarı CI/T21 anahtarıyla aynı varsayılmaz.
2. Android16 S24 Ultra'da uçak modunda varsayılan HTML → APK/AAB/BOTH; hiçbir SDK indirme ekranı olmamalı.
3. Plain/multiple file input izin sayısı; capture/WebRTC/required unsupported uyarısı; eski açık tercihin korunması ve pasif anahtara tıklama.
4. Dosya/PDF/CSV paylaşım, token/origin/gesture redleri, bildirim/konum/QR/NFC/yerel ses/WakeLock/DeepLink/tema/splash/ikon.
5. Yalnız Android Core kur; Node/Python/Expo/Windows indirilmediğini ağ ve disk envanteriyle ispatla. Python/Expo ve Windows için bağımsızlık/dependency-union tekrar et.
6. Node/host Range/200restart, bağlantı kesme, iptal/devam, rotasyon, arka plan/OS kill, disk dolması, yanlış SHA/bozuk receipt/yanlış sürüm.
7. Normal projede gerekli gerçek bağımlılıkları onayla hazırla, sonra interneti kesip gerçek derle; yalnız araç READY olmasını yeterli sayma.

## 19. Tamamlanamayan işler

Tam V3 kabulü için: bütün dağıtımların sabit sürüm/SHA-256 manifesti, tüm araç indirmelerinde byte/ETA ve güvenli resume, kalıcı foreground download işi, tam transaction/rollback/ownership tabanlı update/uninstall, proje bağımlılıklarının ayrı onaylı hazırlık ekranı ve gerçek cihaz offline normal build kabulü gerekir. Yerel kamera/capture/WebRTC ve mevcut olmayan servis SDK entegrasyonları tamamlanmadı; güvenlik kapıları korunur ve destekmiş gibi gösterilmez.

Bu rapor kapsamlı görevin bütün maddelerini tamamlandı olarak işaretlemez.

## 20. Son host doğrulama kanıtları

- `npm --prefix quality test`: **1095/1095 PASS**, 0 failure/skip (son koşu 233392 ms).

- Android app JUnit: **341 test, 0 failure/error/skip**.
- Gerçek AGP HTML şablonu JUnit: **6 test, 0 failure/error/skip**.
- `generateFastHtmlIntegrity`, `testDebugUnitTest`, `assembleDebug`: **PASS**; son tekrar `--offline` ile **BUILD SUCCESSFUL**. Host cache kullanılır; cihaz üzerinde normal motor derlemesi değildir.
- Üretilen Java wrapper'ları ile gerçek HTML shell kaynakları `javac --release 8` kullanılarak Android37, ZXing3.5.3 ve Billing9.1.0 sınıflarıyla derlendi: **PASS**.
- Üretim Kotlin APK/AAB paketleyicileri, gerçek AGP şablonları, APK v2/v3 ve AAB imza doğrulaması, manifest/izin/renk/ikon/deep-link/media parity ve olumsuz digest/sertifika/unsigned kontrolleri: **PASS**. Paketleme sırasında ağ ve exec erişimi engellidir. Android Context/AssetManager sınırları host adaptörüdür.
- Aynı smoke tekrarında çıktılar korunmuştur; yalnız geçici çalışma dizini temizleme davranışı host runner'da değiştirildi. Üretim paketleme/doğrulama kodu değiştirilmedi.
- Son modül/önkontrol/UI sözleşme koşusu: **22/22 PASS**. İlk dar koşuda PATH içinde kotlinc yoktu; gerekli yerel Kotlin araç yolu verilerek tekrar çalıştırıldı, test atlanmadı.
- Stability gate: **PASS, fail 0**. Wiki audit: **0 hata, 3 önceden var olan uyarı**. Wiki secret/hygiene/deterministic snapshot ve diff kontrolü PASS.
- Android Core/Python kurulumuna örnek projenin gerçekten `--offline` ile tekrar derlenmesi eklendi. Bu cihaz-local akışın fiziksel yürütülmesi **NOT_RUN**; host Gradle PASS bunu ikame etmez.

### Yerel doğrulama çıktıları

Dizin: `/workspace/deliverables/fast-html-v3-20261018-host-validation`.
Bu dosyalar yeni CI artifact'i veya telefon için kabul edilmiş kurulum değildir.

| Dosya | Bayt | SHA-256 |
|---|---:|---|
| AppForgeStudio-V3-local-debug.apk | 61174361 | `67fdaf7b8fcac7bb4711c396cef7805cbd00fa446cb6f618e737f495a896ce7a` |
| host-smoke-false.apk | 2948373 | `3e8006e8e2003ee808e8d931a86db8d1413775f3aad2daa751055863c19af5d8` |
| host-smoke-false.aab | 2853244 | `ba33be7163c75dae36e8508061a365fcf1681ff76aa7e12c2f56ff8873e8bacd` |
| host-smoke-true.apk | 2952470 | `09d964ed93a2bd741d7c8b9e9c8369dce1fae00a3c9bea5307051d366e947ccb` |
| host-smoke-true.aab | 2854942 | `d70b39f0a9a4b3049c55d0495b33231bb403c9ac5814e064a326fd6f88a8478f` |

Yerel AppForge APK: `com.appforge.studio`, 5.0.34 (534), min26/target37, APK v2 doğrulandı.
Yerel imza sertifikası SHA-256:
`6908acd060d131ad36178742b8a1f56397b9d8105079c8e762a0f791cf5b61c7`.
Referans T21 CI sertifikası:
`f04693e62080489f41554ff357bab0789bf87b864aa8c5a9927279667de9d91b`.
**İmzalar farklıdır; T21 üzerine yükseltme kurulumu uygun değildir.** Telefonun kurulu kimliği ayrıca doğrulanmadı. Kullanıcı verisi silme veya uninstall önerilmez/uygulanmaz.

Smoke APK/AAB çiftleri `com.example.v2testdisabled` ve `com.example.v2testenabled` test projeleridir; AppForge uygulamasının AAB'si değildir. Geçici test sertifikası SHA-256:
`c66c1ab99775a69001acb9991c6afd4a1c424193b1ab14d2cc7dedf24eac66d0`.
Bu kimlik CI/Play/telefon yükseltme kimliği değildir. Hiçbir özel anahtar teslimat dizinine veya repoya eklenmedi.

## 21. GitHub erişim engeli ve güvenli devam

2026-10-18 tarihinde shell hesabında repository `push=false` tekrar doğrulandı. HACKMASTER bağlı hesabı `push=true` metadata'sına rağmen gerçek `POST git/trees` isteği **FORBIDDEN/403** döndürdü. Bu nedenle GitHub ref güncellenmedi, push yapılmadı ve workflow dispatch denenerek eski kod için yanıltıcı run oluşturulmadı. Yetki reddini aşmak için başka yazma endpoint'i denenmedi. Yeni run ID: **NOT_RUN (entegrasyon yazma erişimi engelli)**.

Yetki düzeltildikten ve izinli dalın güncel başı yeniden karşılaştırıldıktan sonra yalnız fast-forward push ve aşağıdaki güvenli dispatch kullanılmalıdır:

```sh
gh workflow run android-debug.yml \
  --repo HACKMASTER-TR/AppForge-Studio \
  --ref feat/fast-html-v2-full-capabilities-20261017 \
  -f publish_latest_release=false \
  -f pro_recovery_test=false \
  -f pro_second_device=false \
  -f pro_process_death_test=false
```

Bu komut bu ortamda çalıştırılmadı. Yeni CI PASS ve imza eşleşmesi olmadan telefon APK'si hazır veya yükseltmeye uygun değildir. Main/T21/T20 için yazma, merge, force-push, release, Play veya telefon kurulumu yapılmadı. Salt okunur son kontrolde main `63e496913332cf2e2ae96b2e67ae7414226f09fe`, T21 `a4bbd3cae2a6bcc28d4f24303f145fce8dfab972`; değişmediler.
