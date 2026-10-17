# AppForge Fast HTML V2 — 2026-10-17 çalışma raporu

## Kapsam ve sonuç sınırı

Bu görev P1/P2/P3'ü birlikte ele aldı; aşağıdaki gerçek uygulamalar eklendi.
**Bütün kapsam tamamlanmış değildir.** QR, yerel arka plan ses servisi, tek seferlik
yerel konum ve gerçek Play Billing SDK yolu eklendi. Yerel kamera/mikrofon
WebRTC, AdMob, Firebase ve UMP aşağıdaki sınırlar nedeniyle kapalıdır.
Engeller kaldırılmadı; yapılandırma bayrakları çalışan SDK entegrasyonu sayılmadı.
Fiziksel Android kabulü ve Play uygunluğu bu rapordan çıkarılamaz.

- Kaynak: `https://github.com/HACKMASTER-TR/AppForge-Studio.git`.
- Doğrulanan T21 başlangıcı: `a4bbd3cae2a6bcc28d4f24303f145fce8dfab972`.
- İzole dal: `feat/fast-html-v2-full-capabilities-20261017`.
- Değişiklikler yerel ve commit edilmemiştir; HEAD başlangıç SHA'sında kalır.
- T21 dal referansı değişmedi. Bu tek-dal klonunda yerel main/T20 referansı yoktur;
  bu dallara işlem yapılmadı. Push, PR, merge, release, Play, cihaz kurulumu veya
  gerçek kullanıcı/proje verisi üzerinde işlem yapılmadı.
- Bulut çalışma alanına test için JDK 21, Kotlin 2.2.20, Gradle 9.3.1 ve Android
  SDK/NDK hazırlandı. Bunlar üretilen uygulamaların veya telefonun gereksinimi değildir.

## Uygulanan değişiklikler

1. `TinyHtmlActivity.java`: Android sistem belge seçicisi, en çok 32 dosya, MIME
   ipuçları, tek/çoklu seçim, iptal ve eski sonuçları tüketmeden yeniden giriş
   yasağı. Yalnız `content://` URI döner; file URI, kendi paylaşım sağlayıcısı,
   geçersiz authority ve sınır aşımı reddedilir. Kalıcı/yazma/depolama izni alınmaz.
   Navigasyon/destroy callback'i iptal eder; süreç yeniden yaratılmasında bekleyen
   seçici durumu korunur. Seçici kullanıcıya açıktır; WebChromeClient isteğin alt
   çerçeve origin'ini bildirmez. Kullanıcı seçtiği dosyayı sayfaya vermeyi onaylar.
2. `AppForgeSelection.java`: Android'den bağımsız, çalıştırılabilir seçim durum
   makinesi; yinelenen URI'leri tekilleştirme, boyut/adet ve URI/MIME kontrolleri.
3. Mevcut T21 yetenek kanalı üzerinden yalnız yazılan pano ve 1–1000 ms titreşim.
   Ana köprü, ayrı özellik bayrakları, güvenilir ana sayfa, token, güncel dokunuş,
   origin ve önplan kontrolleri korunur. Uzak köprü ayrıca opt-in gerektirir.
   Kullanıcının mevcut panosu okunmaz; titreşim pause sırasında durdurulur.
4. Gerçek anlık yerel bildirim: kanal oluşturma, Android 13+ POST_NOTIFICATIONS
   isteği, izin reddi/uygulama veya kanal kapalı kontrolleri ve NotificationManager
   çağrısı. Düz metin, tek değiştirilebilir bildirim; Intent, alarm veya FGS yoktur.
   `await AppForgeFastNative.notify(title,text)` sonucu `PERMISSION_REQUESTED`
   ise bildirim gönderilmemiştir; kullanıcı izin kararından sonra yeniden dokunur.
   `SENT` Android'e gönderimdir; ekranda gösterim veya kullanıcıya teslim kanıtı değildir.
5. Metin/arka plan renkli açılış katmanı (sayfa bitişi veya 5 saniye sınırı),
   çalışma zamanı ekran yönü, API 26–29 ve 30+ tam ekran yolları. Mevcut WebView
   arka plan/sistem çubuğu renkleri korunur. Android'in uçtan uca pencere ve büyük
   ekran kuralları renk/yön isteğini geçersiz kılabilir; bunu UI açıklar.
6. Önplan HTML medya varlıkları: mp3/mp4/webm/ogg/wav/m4a, PDF/CSV dosyalarının
   statik pakete alınması ve iki çalışma zamanında doğru MIME türleri. Mevcut
   5 MiB HTML, 24 MiB proje, 250 dosya ve yol/symlink kontrolleri korunur.
   Activity pause, WebView ve HTML audio/video oynatımını durdurur.
7. `FastHtmlApkPolicy`, iki packager ve ortak runtime config: desteklenen P1
   seçenekleri artık normal motora gitmez. Varsayılan fileUpload/networkState/
   splash/clipboard/vibration seçimleri korunarak hızlı motor seçilebilir.
8. Gerçek APK binary XML ve AAB protobuf XML izin budaması. Derlenmiş şablonda
   sabit allowlist envanteri (kamera, ses, konum, medya, Billing dahil) beklenir.
   Seçilmeyen izinler çıktıdan çıkarılır; bilinmeyen, yinelenen
   veya eksik envanter reddedilir. Keyfi izin/bileşen sentezi yapılmaz. Sağlayıcı
   authority kimliği, exported=false ve salt okunur paylaşım korunur.
9. `MainActivity` seçilen motoru Build öncesi gösterir. Son onay adımında kaynak,
   gömülü şablon özeti ve normal motor önkoşulları I/O dispatcher üzerinde okunarak
   çevrimdışı önkontrol sonucu gösterilir. `OfflineBuildPackManager` ve
   `TemplateIntegrity.requireBundled` sınırlı okuma ve gerçek SHA-256 denetimi yapar.
   Yapım sırasında yeniden doğrulama sürer; önkontrol cihaz kabulü değildir.
10. CI wrapper kontrolüne yeni davranış testleri eklendi. Gerçek AGP çıktılarında
    izin/config eşliği birim testi ve bağımsız host packager/imza smoke aracı eklendi.
    `.gitignore` yerel Android modül çıktıları/Kotlin derleyici durumunu dışlar.
    Wiki sınırları ve üretilmiş Second Brain özeti güncellendi.

## Yetenek matrisi

FAST_SUPPORTED uygulanan yolu ifade eder; fiziksel cihaz kabulü anlamına gelmez.
NORMAL_ENGINE_REQUIRED telefon üzerinde ayrıca doğrulanmış yerel araç zinciri
ister; EXTERNAL_CONFIG tek başına çalışan SDK entegrasyonu anlamına gelmez.

| Yetenek | Durum | Gerçek davranış / sınır / kanıt |
|---|---|---|
| Yerel statik HTML → APK/AAB/BOTH | FAST_SUPPORTED | Aynı runtime config; paket, izin, şablon ve imza denetimleri. AAB/BOTH SDK 26/37 sınırı korunur. |
| HTTPS URL → APK | FAST_SUPPORTED | Sarmalayıcı çevrimdışı paketlenebilir; uzak sayfa/servis için internet gerekir. |
| HTTPS URL → AAB/BOTH | NORMAL_ENGINE_REQUIRED | Mevcut kaynak/çıktı kapısı korunur. |
| Windows veya karışık Android+Windows | NORMAL_ENGINE_REQUIRED | Kendi motorları; Fast HTML çıktıları yutmaz. |
| Dosya seçme / çoklu seçim | FAST_SUPPORTED | SAF, 32 dosya, kullanıcı seçimi; OFF, foreign top origin, pause, stale callback ve iptal testleri. |
| Kamera / WebRTC | FAST_SUPPORTED (HTTPS APK); yerel NORMAL_ENGINE_REQUIRED | Güvenilir başlangıç HTTPS origin'i, son dokunuş, istek başına açık onay ve Android CAMERA izni. Yerel file origin değiştirilmedi; ACTION_IMAGE_CAPTURE / input capture yazılabilir sağlayıcı yok. |
| Mikrofon / WebRTC | FAST_SUPPORTED (HTTPS APK); yerel NORMAL_ENGINE_REQUIRED | Açık consent ve RECORD_AUDIO; bilinmeyen/kısmi/tekrarlı kaynak, foreign origin ve eski callback reddi. Fiziksel WebRTC NOT_RUN. |
| Konum | FAST_SUPPORTED (yerel APK/AAB/BOTH; HTTPS APK) | Yerel dosya origin değiştirilmeden tek güncel konum: açık onay, COARSE/FINE kullanıcı kararı, GPS varsa çevrimdışı gerçek fix; 30 s sınır ve lifecycle iptali. HTTPS mevcut coarse yolu; watch/background yok. Fiziksel konum NOT_RUN. |
| Pano | FAST_SUPPORTED | Yalnız yazma; ana köprü + özellik opt-in, origin/token/dokunuş/önplan. |
| Titreşim / haptik | FAST_SUPPORTED | 1–1000 ms; VIBRATE yalnız ana köprü ve seçim birlikte açıkken; cihazda motor yoksa hata. |
| Anlık yerel bildirim | FAST_SUPPORTED | Önplan kullanıcı dokunuşu, köprü ve bildirim opt-in, kanal ve Android 13+ izin kontrolü. |
| Zamanlanmış/background bildirim | BLOCKED | Alarm/zamanlayıcı/persisted job/arka plan bileşeni yoktur. |
| Push bildirimi | BLOCKED + EXTERNAL_CONFIG | Firebase Messaging entegrasyonu yapılmadı; internet ve proje/account yapılandırması gerekir. |
| Ağ durumu | FAST_SUPPORTED | Gerçek INTERNET+VALIDATED kontrolü, önplan callback, güvenli networkState API ve bağlantı olayı; URL için offline uyarı ve geri bağlantıda aynı origin yeniden yükleme. |
| Ekran yönü / tam ekran | FAST_SUPPORTED | Çalışma zamanı Android API'leri; işletim sistemi kısıtları geçerlidir. |
| Splash metni / arka plan | FAST_SUPPORTED | Uygulama içi metin katmanı; platform launch splash/özel ikon kaynak yeniden yazımı değildir. |
| WebView/background/status/navigation renkleri | FAST_SUPPORTED | Mevcut setter'lar; Android 15+ sistem çubuğu görünümünü OS belirleyebilir. |
| Primary color / kategori | FAST_SUPPORTED | Gerçek APK ARSC/AAB resources.pb tema rengi ve kaynak paket kimliği; application appCategory game=0, auto/none=-1. OEM Gaming Hub garantisi değildir. |
| Özel uygulama ikonu | FAST_SUPPORTED | Kullanıcı content URI, statik PNG ≤1 MiB ve 1024×1024; CRC/zlib/scanline kontrolü. Gerçek drawable baytları APK/AAB'ye yazılır. APK optimize yolu ARSC'den çözülür; adaptif ikon XML sentezi yok. |
| JS/DOM/zoom/media/cache kontrolleri | FAST_SUPPORTED | Mevcut runtime setter'lar korunur. JavaScript kapalıyken köprüler çalışmaz; UI bildirir. |
| Deep link | FAST_SUPPORTED | Sabit intent-filter slotu scheme/host/pathPrefix ile yeniden yazılır; OFF iken tamamen kaldırılır. Bounded, credential/port/traversal korumalı URI yalnız appforge-deeplink olayıdır; web uygulaması yönlendirir. |
| HTTPS App Links doğrulaması | EXTERNAL_CONFIG | assetlinks.json/site kontrolü gerekir; offline build bu doğrulamayı sağlamaz. |
| Wake lock | FAST_SUPPORTED | Önplanda FLAG_KEEP_SCREEN_ON ve 10 dakika üst sınırlı PARTIAL_WAKE_LOCK; pause/destroy bırakır. Arka plan işi sağlamaz. |
| NFC | FAST_SUPPORTED (etiket ID alt kümesi) | Güvenilir kullanıcı dokunuşuyla scanNfc(); bir ID okur, appforge-nfc olayında hex id gelir; 30 s, pause/navigation/destroy iptal eder. Yazma/NDEF/ödeme yok. Donanım testi NOT_RUN. |
| Ek allowlist izinleri | FAST_SUPPORTED (yalnız beyan) | Sabit 12 ek izin slotu seçime göre budanır; alias/allowlist ve duplicate kontrolleri korunur. Takvim/kişi/biometri gibi ek yerel API veya otomatik Android runtime izni eklenmez. |
| Üretilmiş PDF / UTF-8 CSV SAF save | FAST_SUPPORTED | T21 16 MiB/24576 byte chunk, token, zaman aşımı, dokunuş, origin ve UTF-8/PDF denetimleri. |
| PDF / CSV direct ACTION_SEND | FAST_SUPPORTED | Mevcut salt okunur süreli provider, MIME, ClipData ve geçici read grant; “chooser-launched” teslim kanıtı değildir. |
| HTTP indirme | FAST_SUPPORTED (online çalışma) | Mevcut güvenilir sayfa/dokunuş kapılı DownloadManager. İnternet olmadan uzaktaki dosya alınamaz. |
| Blob/data PDF/CSV ve yerel varlıklar | FAST_SUPPORTED | Mevcut belge kanalı ve legacy storage origin korunur; üçüncü taraf web bağımlılıkları otomatik indirilmez. |
| Önplan HTML audio/video | FAST_SUPPORTED | Paketlenen yerel medya, doğru MIME; pause sırasında durur. Codec ve seek cihazda ayrıca test edilmeli. |
| QR / barcode | FAST_SUPPORTED | CI içine derlenen ZXing 3.5.3; önplan kamera UI, Android izin/retry, 120 s zaman aşımı, tek sonuç, pause/navigation iptali. QR/EAN13/Code128 ve döndürülmüş kod gerçek decoder testinde geçti; kamera donanımı NOT_RUN. |
| Arka plan medya | FAST_SUPPORTED (paketlenmiş yerel ses) | Gerçek MediaPlayer + MediaSession + private mediaPlayback FGS, bildirim kontrolleri, audio focus, kulaklık çıkarma/timeout/iptal. AppForgeMedia API gerekir; DOM audio otomatik taşınmaz; uzak akış normal motor gerektirir. Donanım/OS kabulü NOT_RUN. |
| Özel APK keystore | NORMAL_ENGINE_REQUIRED | Mevcut APK imza sınırı değiştirilmedi. |
| AAB/BOTH upload key | FAST_SUPPORTED + EXTERNAL_CONFIG | Mevcut doğrulanan PKCS12/alias/parola ve imza eşliği yolu korunur. Debug AndroidKeyStore Play upload key taşınabilirliği sağlamaz. |
| AdMob | BLOCKED + EXTERNAL_CONFIG | Template Gradle bağımlılığı/runtime initialization/resource kimliği yok; App ID ve unit ID'leri yetmez. Ağ, account ve reklam politikası/consent gerekir. |
| Firebase Analytics | BLOCKED + EXTERNAL_CONFIG | Google/Firebase init/options ve per-app yapılandırma yok; google-services config tek başına entegrasyon değildir. Online veri gönderimi offline vaat edilmez. |
| Firebase Crashlytics | BLOCKED + EXTERNAL_CONFIG | SDK, build-time eşleme/ID ve yükleme zinciri yok; source/plugin/CI entegrasyonu gerekir. |
| Firebase Messaging | BLOCKED + EXTERNAL_CONFIG | SDK, service/token ve per-app Firebase yapılandırması yok; internet/Google servisleri gerekir. |
| Play Billing | FAST_SUPPORTED + EXTERNAL_CONFIG | Billing 9.1.0 shell içine derlenir; gerçek offer/query/purchase/restore/ack/consume. Paket/ürün/token özeti/adet eşleşen HTTPS backend doğrulaması olmadan COMPLETE yok. Play ürünleri/uygun dağıtım ve doğrulama sunucusu gerekir; gerçek Play işlemi NOT_RUN. |
| UMP | BLOCKED + EXTERNAL_CONFIG | Consent SDK/form akışı yok; uygulama/account/privacy ve bölgeye bağlı yapılandırma gerekir. Varsayılan canlı monetizasyon eklenmedi. |

P3 sonuçları template'in Gradle bağımlılıkları (ZXing 3.5.3 ve Billing 9.1.0), sabit manifest/runtime ve
WebviewProjectSettings/WizardReadinessEvaluator/DeviceBuildEngine kapıları
incelenerek belirlenmiştir. Bu entegrasyonlara sahte success, token,
servis veya booleandan üretilmiş çalışıyor iddiası eklenmedi. BLOCKED satırları teorik olarak hiç yapılamaz iddiası değil,
bu görevde tamamlanmamış ve kapalı tutulan kapsamdır.

## Önceki doğrulama turlarının tarihçesi

Aşağıdaki tarihsel kayıtlar kendi kaynak/artifact sürümleri içindir. En güncel
uygulama ve test sonuçları raporun sonundaki devam turundadır; önceki APK güncel
kaynak yerine kullanılmamalıdır.


| Doğrulama | Sonuç | Kanıt / kapsam |
|---|---|---|
| Tam kalite (`npm --prefix quality test`) | PASS: 1079/1079, 0 skip | Tam paket; son kaynak kontrolü ayrıca çalıştırıldı. |
| Gradle `:html-apk-template:assembleRelease :html-apk-template:bundleRelease :app:testDebugUnitTest` | PASS: 326/326, 66 suite, 0 skip | Gerçek AGP 9.1.1 / Gradle 9.3.1 / JDK 21 / SDK 37.0; main Kotlin/Java kaynakları derlendi. |
| Derlenmiş şablon kimliği/izin eşliği | PASS: 2/2 JUnit testi | APK binary XML bağımsız element okuyucusu + AAB protobuf okuyucusu; 16 izin/config birleşimi, farklı authority, beklenmeyen/mükerrer şekil ve yanlış digest retleri. |
| Yeni statik medya/doküman kaynakları | PASS: 2/2 JUnit testi | Tam bayt eşliği; dış giriş yolu, symlink ve 250 dosya sınırı retleri. Bu testler 326'nın içindedir. |
| Java çalışma zamanı / belge / P1 hedefli paket | PASS: 26/26 | Gerçek Tiny Activity + doc/provider kaynakları host Android uçlarıyla çalıştırıldı; SDK 26/30/37 pencere dalları, seçici OFF/iptal/stale/foreign/pause ve MIME kontrolleri. 1079 içinde de yürütülür. |
| Üretim packager/imza host smoke | PASS: 2/2 birleşim | Özellikler açık/kapalı; gerçek AGP şablonları, APK v2/v3 + AAB JAR/CMS, beklenen test sertifikası, config/media eşliği. Java soket bağlantısı ve süreç başlatması yasakken çalıştı. |
| İki Java wrapper'ın Android API karşısında derlenmesi | PASS | `javac --release 8` ve gerçek SDK 37.0 android.jar; deprecation uyarıları var. |
| Stabilite kapısı | PASS: fail 0 | `APPFORGE_TMPDIR=/workspace/scratch/fast-html-v2-stability ./scripts/appforge-stability-gate`. |
| Wiki audit / secret scan / snapshot / diff | PASS (audit advisory Yellow) | 0 wiki error; coverage registry tavsiyeleri var. Snapshot gerçek generator ile üretildi; kontroller kaldırılmadı. |
| Studio debug APK paketleme | PASS | `:app:assembleDebug` başarılı; APK v2 imzası ve gömülü iki şablon + AAB manifest digest eşliği ayrıca doğrulandı. Kurulum/dağıtım yok. |
| Hosted GitHub CI | NOT_RUN | Push/PR/workflow tetikleme yetkisi verilmedi; yerel AGP çalışması hosted PASS sayılmadı. |
| Fiziksel Android 8–16 / emülatör kabulü | NOT_RUN | Cihaz/emülatör bağlanmadı, APK kurulmadı. Host uçları OS/WebView/izin UI kabulü değildir. |
| AndroidKeyStore cihaz kalıcılığı | NOT_RUN | Host smoke geçici enjekte edilmiş test kimliği kullanır; cihaz sağlayıcısı çalıştırılmadı. |
| Play / üçüncü taraf canlı servisler | NOT_RUN / BLOCKED | Kimlik/account/consent/SDK zinciri tamamlanmadı; monetizasyon, satın alma veya bildirim gönderimi yapılmadı. |

### Çalıştırma ve kanıt yerleri

- Tam kalite logları: `/workspace/scratch/fast-html-v2-quality-final.log` ve
  `/workspace/scratch/fast-html-v2-quality-verified.log`.
- Başarılı Gradle: `/workspace/scratch/fast-html-v2-gradle-verified.log`.
- Studio paketleme: `/workspace/scratch/fast-html-v2-studio-debug.log` —
  `BUILD SUCCESSFUL in 6m 2s`; 148 görev. İmza kontrolü:
  `/workspace/scratch/fast-html-v2-studio-signature.log` — Android Debug sertifikası,
  tek imzalayan, APK v2 doğrulaması başarılı. Studio APK'sında v3 imzası yoktur;
  host smoke ile üretilen HTML APK'larının v2 **ve** v3 imzaları ayrı doğrulandı.
- Yerel debug çıktı: `android-app/app/build/outputs/apk/debug/app-debug.apk`,
  55,600,668 byte; SHA-256
  `2279fe9cecd0074e25f673b36b9a84a6f5d14353b58dfda91741518bca981cdf`.
  İçindeki APK template 700,578 byte, AAB template 702,672 byte; ikisinin digest'i
  imzalı Studio metadata'sıyla eşleşir. Bu dosya Git'e eklenmedi ve kurulmadı.
- Gerçek JUnit XML'leri: `android-app/app/build/test-results/testDebugUnitTest/`.
- Host smoke aracı: `quality/tools/fast_html_v2_compiled_smoke.py`.
  PATH'te JDK 21 + kotlinc ile `--android-jar`, `--apksig-jar`, `--json-jar` ve
  `--assets-build android-app/app/build` verilir. Önce gerçek
  `:app:generateFastHtmlIntegrity` çıktıları bulunmalıdır; araç şablon/metadata
  uydurmaz. Context/AssetManager ve BuildConfig host sınırlarıdır; paketleme ve
  kripto gerçek üretim Kotlin kodudur. Test anahtarı geçicidir ve silinir.
- Smoke logu: `/workspace/scratch/fast-html-v2-compiled-smoke-final.log`.
  Ağ/exec retleri JDK 21 test SecurityManager'ı ile uygulanır; bu test API'si
  deprecated'dir ve ürün kodunda kullanılmaz. JDK 24+ için aynı koşul ayrı bir
  host sandbox ile yeniden kurulmalıdır.
- Java API derleme: `/workspace/scratch/fast-html-v2-javac.log`.
- Stabilite: `/workspace/scratch/fast-html-v2-stability-verified.log`.
- Binary manifest `aapt2 dump xmltree` çıktısı:
  `/workspace/scratch/fast-html-v2-compiled-manifest.txt`.
- Ham AGP şablonları `android-app/html-apk-template/build/outputs/` altında;
  gömülecek varlık/metadata `android-app/app/build/generated/html-*/assets/`
  altındadır. Derleme çıktıları Git'e eklenmez.

### İlk hatalar ve düzeltmeler

Son PASS önceki başarısız denemeleri silmez:

- İlk Java ortamı yalnız JRE içeriyordu; javac bulunamadı. Bulut test araçlarına
  tam JDK eklendi. Telefonda araç zinciri kurulmadı.
- İlk Gradle denemesi proxy JVM ayarları olmadan AGP'yi çözümleyemedi. Oturumun
  mevcut proxy/CA'sı kullanılarak düzeltildi; TLS doğrulaması kapatılmadı.
- SDK platformunun gerçek adı 37.0'dır; CI'daki mevcut 37 symlink düzeni izlenmiştir.
- İlk tam kalite 1076/1079 geçti: eski notification/normal-route beklentisi,
  güncel olmayan snapshot ve iki helper'ın byte eşliği yakalandı. Eski beklentiler
  gerçek V2 pozitif/negatif davranış kontrolleriyle değiştirildi; helper'lar yeniden
  eşitlendi; snapshot generator ile üretildi. Güvenlik testleri silinmedi.
- İlk AGP derlemesinde düzenleme sürerken kaynak fingerprint'i değişti; mevcut
  `Template sources changed during compilation` kapısı durdurdu. Sabit kaynakla
  tekrar üretim başarılı oldu. Kapı zayıflatılmadı.
- İlk JVM turu 325/326: Kotlin QuickHtmlConfigTest eski normal-motor varsayımını
  taşıyordu. Güncellenen test hem desteklenen seçeneklerin korunmasını hem kamera/
  mikrofon/konum/ikon/NFC/ek izin/derin bağlantı retlerini çalıştırır. Sonraki
  fixture'ın boş appName doğrulamasına takılması da düzeltildi; ürün doğrulaması
  kaldırılmadı. Nihai tur 326/326'dır.
- Studio debug DEX aşamasında mevcut `asset-delivery-ktx:2.3.0` bağımlılığından
  `Invalid stack map table ... Constructor mismatch` D8 uyarısı geldi. Derleme
  başarılı tamamlandı; bağımlılık veya güvenlik denetimi değiştirilmedi. Cihaz
  çalışma zamanı uygunluğu bu başarıyla iddia edilmez. Diğer deprecation/Kotlin
  uyarıları da loglarda korunur.
- İlk smoke gerçek org.json yerine android.jar'ın throwing stub'ını öne almıştı;
  host classpath sırası düzeltildi. Son tur gerçek org.json ve gerçek kripto ile
  iki APK/AAB çiftini doğruladı.

### Şablon provenance ve imza sınırı

Başarılı sabit-kaynak üretiminin SHA-256 değerleri:

```text
html-apk-template.apk a3a365568f70ef9e5021b0de955606fbf79763760d2d1914a676d779738d6a98
html-aab-template.aab 0c4f21cc54ef9ab4ccae0e9fda4bee0c50ab2b956228b3057d3618fae77160ed
html-aab-manifest 8572b7e3eb1378bceaead7361280eca091c55d57eb630b25fe16028aa1785b19
selected-template-source ccde253ad7ec5ca1d66f5fcf3dd3fa80b9a5588775582baa53687b114c78afc7
```

Mevcut mimaride ham şablon APK/AAB **ayrı ayrı imzalı değildir**: bunlar Studio
APK imzasının kapsadığı varlıklar ve aynı imzanın kapsadığı digest metadata ile
korunur. Cihazda yeniden paketlenen çıktı sonra ayrıca imzalanır/doğrulanır.
Bu görev bağımsız template sertifika/PKI zinciri eklemedi; hedefteki “imzalı
şablon” ifadesinin bu mimari farkı açık bir sınırlamadır. Özel upload kimliği
kullanılırsa APK/AAB sertifikası ortaktır. Debug varsayılanında mevcut APK ve AAB
AndroidKeyStore alias'ları ayrıdır; bu kimlikler değiştirilmedi ve build mesajı
artık ortak olduklarını iddia etmez. Play yükleme/üretim sertifikası kabulü yapılmadı.

## Kalan doğrulamalar ve eksikler

- Fiziksel Android 8/13/16: input chooser, farklı content provider'lar, gerçek
  permission deny/never-ask-again, süreç ölümü, döndürme, bildirim kanalı kapatma,
  panoya yazma, titreşim donanımı ve uygulamaya geri dönüş NOT_RUN.
- Gerçek WebView alt çerçeve/navigasyon, local file origin uyumluluğu, PDF/CSV
  SAF sağlayıcı ve doğrudan paylaşım alıcısı kabulü NOT_RUN. Host negatif testleri
  cihaz veya hedef uygulamada teslim kanıtı yerine geçmez.
- Medya codec/range-seek, ses odağı ve Android'in arka plan kısıtları fiziksel
  olarak denenmedi. Background media seçeneği kapalı kalır.
- İlk teslimde kalan P1 ikon/derin link/ek izin/NFC/wake ve HTTPS medya/konum daha sonra eklendi. Yerel file HTML kamera/mikrofon/konum ve capture sağlayıcısı hâlâ tamamlanmadı.
  Tema primary/category kaynak yazımı sonraki geliştirmede tamamlandı; normal
  motor gerektiren kalan özellikler ayrıca yerel araç zinciri ister.
- P2 QR decoder ve önplan kamera eklendi; background service ile P3 gerçek SDK, kaynak/bileşen,
  per-app config, account, consent ve servis testleri tamamlanmadı.
- Seçici Activity sonucu hiç dönmezse güvenli biçimde yeni seçici açılmaz;
  outstanding durumunu atlayıp eski sonucu başka sayfaya vermek tercih edilmedi.
- Offline build yalnız paketlenmiş statik bağımlılıklarla mümkündür. Uzaktaki
  CDN, URL, ödeme, reklam, Firebase veya diğer online servisler offline hâle gelmez.

## Korunan sınırlar

T21 PDF/CSV boyut/MIME/UTF-8, token/expiry, dokunuş, origin ve file namespace,
read-only provider, geçici read grant, APK/AAB doğrulaması ve atomik BOTH yayınlama
kodları korundu. Mevcut iptal/rollback/provider testleri çalıştırıldı. Main/T20/T21
üzerine commit, reset, stash, clean, cherry-pick veya merge yapılmadı. GitHub'a veri
itilmedi, PR açılmadı; üretim/Play veya telefondaki uygulama/kullanıcı verisi
üzerinde işlem yapılmadı. Çalışma yerel inceleme için özellik dalında bırakıldı.

## Değişen dosya envanteri

- `.github/workflows/android-debug.yml`
- `.gitignore`
- `android-app/app/src/main/assets/device-build/AppForgeDocuments.java`
- `android-app/app/src/main/assets/device-build/AppForgeLocalAssets.java`
- `android-app/app/src/main/assets/second_brain_snapshot.json`
- `android-app/app/src/main/java/com/appforge/studio/MainActivity.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlAabProtoManifest.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlAabUnsignedPackager.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPackager.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPolicy.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/HtmlBinaryManifest.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/TemplateIntegrity.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/WebviewProjectSettings.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/CompiledHtmlTemplateIdentityTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/FastHtmlV2AssetsTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/QuickHtmlConfigTest.kt`
- `android-app/html-apk-template/src/main/AndroidManifest.xml`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeDocuments.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeLocalAssets.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeSelection.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java`
- `docs/reports/fast-html-v2-full-capabilities-20261017.md`
- `docs/wiki/03_Architecture/Build_And_Worker_Architecture.md`
- `docs/wiki/06_Product_And_Features/Fast_HTML_V2.md`
- `quality/tests/fast_html_advanced_routing_runtime.test.js`
- `quality/tests/fast_html_v2_runtime.test.js`
- `quality/tests/fast_web_535_phase1_contract.test.js`
- `quality/tests/helpers/fast_html_policy_regression.js`
- `quality/tests/helpers/fast_html_v2_android_stubs.js`
- `quality/tests/html_document_bridge_runtime.test.js`
- `quality/tools/fast_html_v2_compiled_smoke.py`

## Ekran görüntüleri ve çevrimdışı sözleşmesi sonrası ek geliştirme

Derleme tamamen çevrimdışı olmalıdır; internet isteyen servisler bağlantı yokken
pasif kalıp bağlantı geldiğinde çalışabilmelidir. Uygulanan URL içerik davranışı
çevrimdışı uyarı ve aynı güvenilir HTTPS origin için yeniden yüklemedir.
`AppForgeFastNative.networkState()` gerçek doğrulanmış ağ durumunu döndürür;
`appforge-connectivity` olayı yalnız güvenilir, köprüye izinli belgeye gönderilir.
Dinleyici pause/destroy sırasında kaldırılır; eski kuyruk callback'leri nesil
kontrolüyle reddedilir. Sistem ağ izleme hatası bilinmeyen durum uyarısıdır.
Bu kontrol servis sunucusunun erişilebilirliğini veya SDK entegrasyonunu kanıtlamaz.
AdMob/Billing/Firebase hâlâ BLOCKED; onlar için otomatik aktivasyon yapılmış değildir.

Ekrandaki OLED `#BC9CF6` ve standart uygulama kategorisi artık normal motora
zorlama nedeni değildir. APK/AAB derlenmiş kaynaklarına gerçek primary renk,
paket namespace ve kategori yazılır. Renk codec'i yalnız sabit, doğrulanan template
slotunu değiştirir; bozuk/tekrarlı/beklenmeyen kaynaklar reddedilir. ARSC sabit
paket alanı nedeniyle paket adı üst sınırı 127 karakterdir. Mixed Content açıkken
COMPATIBILITY_MODE, kapalıyken NEVER_ALLOW uygulanır; ALWAYS_ALLOW kullanılmaz.
HTTP ana gezinme ve file/content erişim korumaları korunmuştur.

İlk uygulama bölümündeki hash ve test sonuçları ilk kilometre taşına aittir;
aşağıdaki son doğrulamalar ek değişiklikler için geçerlidir. Fiziksel cihaz
doğrulaması yapılmamıştır; tüm görünen anahtarların tamamlandığı iddia edilmez.

### Ek geliştirme son doğrulamaları

| Kontrol | Sonuç | Kanıt / sınır |
|---|---|---|
| Tam kalite turu | PASS 1080/1080, 0 skip | `/workspace/scratch/fast-html-v2-followup-quality.log`; son küçük Mixed Content/exception değişiklikleri ayrıca aşağıdaki odaklı tur ve güncel Gradle ile doğrulandı. |
| Son odaklı runtime/routing turu | PASS 27/27 | `/workspace/scratch/fast-html-v2-followup-focused.log`; gerçek Java sınıfları Android host stub sınırında, cihaz testi değildir. |
| Güncel Gradle integrity + JUnit + Studio assembleDebug | PASS 327/327, 0 failure/error/skip | `/workspace/scratch/fast-html-v2-followup-gradle.log`; BUILD SUCCESSFUL 1m2s. |
| Güncel üretim APK+AAB paketleyici/imzacı smoke | PASS iki ON/OFF durum ve dört bağımsız kaynak kontrolü | `/workspace/scratch/fast-html-v2-followup-smoke.log`; socket/exec yasaklı üretim paketleme; AAPT2 ve bağımsız AAB protobuf incelemesi. |
| Güncel Studio APK imzası | PASS APK v2 | `/workspace/scratch/fast-html-v2-followup-studio-signature.log`; yalnız yerel Android Debug imzası, telefona kurulmadı. |
| Studio içindeki template digest'leri | PASS üç eşleşme | Gömülü APK/AAB ve AAB manifesti SHA-256 kontrol edildi; ZIP tekrarlı ad yok. |
| Stability gate | PASS fail 0 | `/workspace/scratch/fast-html-v2-followup-stability.log` |
| Wiki audit / secret scan / snapshot / diff | PASS; wiki 0 error, 36 advisory warning | `/workspace/scratch/fast-html-v2-followup-wiki.log`; kapsam haritalama önerileri fiziksel kabul sayılmaz. |

Güncel yerel Studio APK: `android-app/app/build/outputs/apk/debug/app-debug.apk`,
56,298,592 byte; SHA-256
`ab751552d12ee52321a5adac2d5b1a3bd0c4ee0a5c1fa6359240112bb39a1594`.

Güncel template ve kaynak parmak izleri:

```text
html-apk-template.apk 1819971b7b85342f0867865baef413ed91d1dd2d22d1c327ba6bdf1ac0d82fae
html-aab-template.aab 5eb13fce1b9f69d7c0b8ecb70677c2e3e778f646c780f615c3374b0faaf93c36
html-aab-manifest 35cc77fa3325bfbcb6fd3c6264f6fa9163b39f4f493f43d400a91afe4d43163c
selected-source bb802d967e3bc210443a416d45805514048566f97186516eb79a6b90b6c9d02e
```

Ek değişen dosyalar: `FastHtmlResourceColor.kt`, `AppForgeConnectivity.java`,
`html-apk-template/src/main/res/values/styles.xml`. Mevcut envanterdeki config,
manifest, packager, normal motor, bridge, test ve wiki dosyaları da güncellendi.
Template appCategory başlangıç slotu AAPT tarafından kabul edilen game=0'dır;
auto/none çıktısına -1 yazılır. İlk undefined XML literal denemesi AAPT tarafından
reddedildi ve doğru derlenmiş slotla düzeltildi. Anonymous Sink içindeki Context
başvurusu ilk Java derlemesinde yakalandı ve TinyHtmlActivity.this ile düzeltildi.
Hiçbir doğrulama bu hataları gizlemek için kaldırılmadı.

**Teslim durumu: KISMİ.** Kullanıcının tüm anahtarlar ve tüm P1/P2/P3 talebi henüz
tam karşılanmadı. Yukarıdaki matris kalanları açıklar; desteklenmeyen özellikler
başarı gibi gösterilmez. Kaynak T21 ref'i ve HEAD başlangıç SHA'sında kaldı;
çalışma yalnız `feat/fast-html-v2-full-capabilities-20261017` dalının commit edilmemiş
dosyalarındadır. Push/PR/merge/yayın/cihaza yükleme yapılmadı.

## Önceki milestone: “Devam et ve teslim et” sonrası uygulananlar

Bu bölüm önceki ara teslimin eksik listesini günceller; en üstteki yetenek matrisi
son davranışı gösterir. Kaynak dal/HEAD değişmedi; commit/push/PR yapılmadı.

- **İkon:** `FastHtmlIcon` kullanıcının seçtiği content URI'yi okur, 1 MiB
  sıkıştırılmış boyut ve 1024×1024 sınırını okumada uygular. PNG chunk CRC,
  IHDR formatı, IDAT sırası, zlib bitişi, satır uzunluğu/filtreleri ve dosya sonu
  doğrulanır. APNG/interlace/bozuk veya aşırı veriler reddedilir. Varsayılan
  ikona sessiz düşme yoktur. AGP APK'de resmi `res/Pk.png` gibi optimize
  adlandırdığından yol derlenmiş drawable/appforge_icon resource ID'sinden
  çözülür; AAB'nin doğrulanan nodpi-v4 slotu kullanılır. Resource ID değişmez.
- **Wake:** gerçek PowerManager ve window flag; referans sayımı kapalı tek kilit,
  10 dakika CPU üst sınırı; release idempotent, pause/destroy cleanup.
- **NFC:** reader mode, tek bounded ID, 30 s iptal, eski generation reddi;
  `LISTENING` okuma başarısı değildir. NDEF/tag yazma/banka işlemi eklenmedi.
- **Deep link:** APK binary XML ve AAB protobuf filtre slotu ON/OFF; seçili
  scheme/host/pathPrefix yazılır. Dış bağlantı main WebView URL'sine yüklenmez;
  appforge-deeplink olayının `detail.url` alanı uygulamaya iletilir. 2048 karakter,
  credentials/port/unsafe scheme/path traversal/kontrol karakteri sınırları
  vardır. Rotation'da tüketilmiş intent yeniden oynatılmaz. autoVerify veya
  sunucu assetlinks.json işlemi yapılmadı.
- **HTTPS kamera/mikrofon/konum:** `AppForgeWebPermissions`, yakın dokunuş
  ve tam başlangıç origin'ini doğrular; açık native onay ve ardından Android
  runtime izni ister. Kamera/audio kaynakları all-or-nothing; unsupported veya
  duplicate resource reddedilir. Konum coarse-only, retain=false. Navigation,
  pause ve WebView cancellation bekleyen kararı iptal eder; eski sistem izin
  sonucu yeni isteğe atanmaz. Sistem izin UI'si Activity'yi durdurursa güvenli
  olarak istek reddedilebilir; kullanıcı geri dönüp yeniden denemelidir.
  **Yerel file HTML bu yola alınmadı:** origin/storage koruması değiştirilmeden
  Android secure-origin/WebRTC/geolocation davranışı kanıtlanmadı.
- **Ek izinler:** tüm mevcut allowlist slotları derlenmiş template'te vardır;
  APK/AAB'de seçilmeyen uses-permission elemanları kaldırılır. Bu seçim
  takvim/rehber/biyometri gibi native işlevleri otomatik sağlamaz; önkontrol
  bunu açıkça bildirir.
- **QR/barkod:** ZXing core 3.5.3 CI'de template dex'ine derlenir. Cihazda
  indirme/model/harici uygulama yoktur. `scanCode()` gerçek önplan kamera
  yüzeyi açar; Android izni gerekirse PERMISSION_REQUESTED döner ve tekrar
  kullanıcı dokunuşu gerekir. SCANNING başlangıç durumudur; yalnız decoded
  metin `appforge-scan` olayında `detail.text` olarak gelir. Kamera önizlemesi
  ≤1920×1080, metin ≤4096 karakter, süre ≤120 s; eski sonuçlar reddedilir.
  Otomatik URL açma yoktur. Kamera hardware/codec/OEM davranışı NOT_RUN.

### Önceki milestone sırasında kalan engeller (devam turu aşağıda günceller)

Tam kapsam hâlâ KISMİ: yerel file HTML için kamera capture sağlayıcısı ve
WebRTC/mikrofon/konum; arka plan MediaSession/foreground service; gerçek AdMob,
Firebase Analytics/Crashlytics/Messaging, Play Billing ve UMP entegrasyonları
tamamlanmadı. SDK bağımlılıkları, merger kaynakları/izinleri/provider/service
envanteri, runtime/per-app config, consent ve servis akışları birlikte test
edilmeden bool alanlarını açmak yanlış başarı olurdu. Mevcut katı provider ve
resource kimliği denetimleri rastgele SDK merger çıktısını kabul etmez;
cihazdaki çevrimdışı paketleme sırasında Gradle/google-services/Crashlytics
plugin çalıştırmak hedef sözleşmeye aykırıdır. Bunun için CI'de derlenen ve
per-app config alanları doğrulanan özel SDK template entegrasyonu gerekir.
Bunlar teorik olarak imkânsız değildir; bu teslimde yapılmamıştır. IDs, Firebase
config, Play ürünleri/account/consent ve gerçek online testleri ayrıca gerekir.
Uzak servislerin çevrimdışıyken pasif olup ağ geldiğinde etkinleşmesi sözleşmesi
korundu; **henüz entegre edilmemiş SDK'lar için uygulandı denmez.**

### Önceki milestone teslim doğrulamaları

- Güncel Gradle `:app:generateFastHtmlIntegrity :app:testDebugUnitTest
  :app:assembleDebug`: **PASS**, Studio **329/329**, template decoder **2/2**,
  failure/error/skip=0. Log: `/workspace/scratch/fast-html-v2-followup-gradle.log`.
- Üretim paketleyici/imzacı: **PASS iki ON/OFF durum**, ağ bağlantısı ve dış
  süreç çalıştırma reddedilirken gerçek APK+AAB üretildi. APK v2/v3 ve AAB JAR
  imzası/cert eşliği, ikon baytları, config/media eşliği; AAPT2 ile bağımsız APK
  primary/category/package/deep-link/permission ve AAB kaynak namespace/color
  kontrolleri geçti. Log: `/workspace/scratch/fast-html-v2-delivery-smoke-final.log`.
- Tüm normal ve hızlı Java kaynakları güncel Android 37 API ve paketlenen
  ZXing classpath ile `javac --release 8` derlemesinde **PASS**.
  Log: `/workspace/scratch/fast-html-v2-delivery-javac.log`.
- Güncel Studio APK v2 imzası **PASS**, Android Debug test kimliği. Gömülü
  APK/AAB/manifest SHA-256 değerleri ve snapshot eşliği ayrıca **PASS**.
  Log: `/workspace/scratch/fast-html-v2-delivery-studio-signature.log`.
- Stability gate **PASS fail 0**, wiki audit **0 error / 40 advisory warning**,
  wiki secret scan **Green**, snapshot check ve git diff whitespace **PASS**.
  Loglar: `/workspace/scratch/fast-html-v2-delivery-stability.log`,
  `/workspace/scratch/fast-html-v2-delivery-wiki.log`.

Son APK: `android-app/app/build/outputs/apk/debug/app-debug.apk`, **56,532,883 byte**.
SHA-256: `09bab7ac9740e290ee66ef5852619db076d7673fd4048521b2d813ac0595f3ff`.

```text
html-apk-template.apk 3615cc5eef6236c70e0397d43dd7d29baf82f4e7a82a2680cee22ff887be2ac2
html-aab-template.aab 3a97bb23551824c218d24d5fe40350cc61950bec565ca6ff617b47ca82a122b5
html-aab-manifest 5406be61fe4e143ee26251600a7e81f2be5330a698b5dc627c65f47e602b0c29
selected-source 02e26d99b0b25699ea522aa7ce27f27d2b0a5ef2bb6e681f2ee6383dfd2e4ffd
```

### Son turda yakalanıp düzeltilen sorunlar

1. Test kabuğunun PATH'inde javac/kotlinc yoktu; görev için kurulan derleyiciler
   PATH'e alınarak gerçek test çalıştırıldı. Eksik araç skip sayılmadı.
2. Genişleyen izin envanteri/ikon route'u eski negatif test beklentilerini
   geçersiz kıldı. Testler izinlerin iki formattaki gerçek eşliğini ve bütün
   alias varyantlarını denetleyecek şekilde genişletildi; unknown retleri kaldı.
3. Template release unit test görevi AGP'de bulunmadı; gerçek debug unit test
   görevi ana test zincirine bağlandı. Aynı production decoder derlenir.
4. Kaynak derleme sırasında değişince provenance kontrolü beklendiği gibi
   reddetti. Kontrol korunup sabit kaynak ağacı yeniden derlendi.
5. İmzalı paket smoke'u, APK'de AAPT optimize ikon yolu ve AAB'de whitespace
   XmlNode ayrımını yakaladı. Kaynak tablosu çözümlemesi ve kayıpsız text-node
   koruması düzeltildi; gerçek template regression testi eklendi.
6. T21 host SAF testi ByteArrayOutputStream yaratıldığı anda yazı bitmiş
   varsayıyordu. Gerçek bayt kontrolü korunup bekleme 7 byte tamamlanana kadar
   yapıldı; yalnız non-null olma başarı sayılmaz.

### Yerel teslim

İnceleme/test için APK, tüm tracked/untracked kaynak değişikliklerini içeren
binary patch, bu rapor ve SHA256SUMS `/workspace/deliverables/fast-html-v2-20261017/`
altına kopyalanır. Patch için reverse apply check, dosya değiştirmeden yapılır.
Bu yalnız yerel teslimdir; hiçbir GitHub/Play/telefon işlemi yapılmaz. APK test
imzalıdır ve fiziksel cihaz kabulü NOT_RUN olarak kalır.

### Önceki milestone tam kalite sonucu

`npm --prefix quality test`: **PASS 1085/1085**, failure/cancel/skip=0;
188.412 s. Log: `/workspace/scratch/fast-html-v2-delivery-quality-final.log`.
Önceki tur 1084/1085 idi: izin alias testinin eski normal-engine beklentisi
artık gerçek template izin/ikon desteğiyle uyuşmuyordu. Test quick eligibility
ve normal manifest/template izin eşliğini birlikte denetleyecek şekilde
güncellendi; ayrı alias turu 2/2 ve nihai tam tur 1085/1085 geçti.

Yerel kaynak patch reverse-apply kontrolü **PASS** olacak şekilde son çalışma
ağacından oluşturuldu; commit veya index değişikliği yapılmadı. Aşağıdaki
envanter teslimdeki tracked ve yeni dosyaların tamamıdır.

### Tam teslim dosya envanteri

- `.github/workflows/android-debug.yml`
- `.gitignore`
- `android-app/app/build.gradle.kts`
- `android-app/app/src/main/assets/device-build/AppForgeDocuments.java`
- `android-app/app/src/main/assets/device-build/AppForgeLocalAssets.java`
- `android-app/app/src/main/assets/second_brain_snapshot.json`
- `android-app/app/src/main/java/com/appforge/studio/MainActivity.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlAabProtoManifest.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlAabUnsignedPackager.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPackager.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPolicy.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlIcon.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlResourceColor.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/HtmlBinaryManifest.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/TemplateIntegrity.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/WebviewProjectSettings.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/CompiledHtmlTemplateIdentityTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/FastHtmlIconTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/FastHtmlV2AssetsTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/QuickHtmlConfigTest.kt`
- `android-app/html-apk-template/build.gradle.kts`
- `android-app/html-apk-template/src/main/AndroidManifest.xml`
- `android-app/html-apk-template/src/main/assets/appforge-third-party/NOTICE.txt`
- `android-app/html-apk-template/src/main/assets/appforge-third-party/ZXing-LICENSE.txt`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeAwake.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeBarcode.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeConnectivity.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeDeepLink.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeDocuments.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeLocalAssets.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeNfc.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeQrScanner.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeSelection.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeWebPermissions.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java`
- `android-app/html-apk-template/src/main/res/drawable-nodpi/appforge_icon.png`
- `android-app/html-apk-template/src/main/res/values/styles.xml`
- `android-app/html-apk-template/src/test/java/com/appforge/htmlstub/AppForgeBarcodeTest.java`
- `docs/reports/fast-html-v2-full-capabilities-20261017.md`
- `docs/wiki/03_Architecture/Build_And_Worker_Architecture.md`
- `docs/wiki/06_Product_And_Features/Fast_HTML_V2.md`
- `quality/tests/advanced_permission_route_runtime.test.js`
- `quality/tests/fast_html_advanced_routing_runtime.test.js`
- `quality/tests/fast_html_v2_runtime.test.js`
- `quality/tests/fast_web_535_phase1_contract.test.js`
- `quality/tests/helpers/fast_html_policy_regression.js`
- `quality/tests/helpers/fast_html_scanner_stubs.js`
- `quality/tests/helpers/fast_html_v2_android_stubs.js`
- `quality/tests/html_document_bridge_runtime.test.js`
- `quality/tools/fast_html_v2_compiled_smoke.py`

**Son durum:** Uygulanan özellikler ve yukarıdaki testler teslim edildi.
Tüm P1/P2/P3 ve tüm anahtarlar tamamlanmış değildir; kalan gerçek engeller
bölümü geçerlidir. Fiziksel Android/online SDK kabulü NOT_RUN; otomatik
test PASS bu eksikleri kapatmaz. Main, T20, T21, Play ve kullanıcı verisine
müdahale edilmedi. HEAD ve kaynak T21 SHA: a4bbd3cae2a6bcc28d4f24303f145fce8dfab972.


## Devam turu: yerel medya, konum ve gerçek Billing SDK

Bu bölüm önceki milestone sınırlarını günceller. Eski teslim klasörü ve SHA-256
kayıtları korunmuştur; güncel kaynak daha fazla değişiklik içerir.

### Gerçek entegrasyonlar

- `DeviceBuildEngine.validateCapabilities` hızlı yönlendirmeden önce çalışır.
  Burada kalan eski koşulsuz QR engeli giderildi; QR, yerel medya ve Billing
  yalnız gerçekten uygun hızlı kombinasyonda kabul edilir. Normal motor/eksik
  SDK kapıları korunur. Test üretimdeki Kotlin metotlarını derleyip çalıştırır.
- `AppForgeMediaService` gerçek private Android servisidir. Paketlenmiş yerel
  dosya en çok 24 MiB olarak uygulamanın özel geçici alanına kopyalanır; yol,
  uzantı, boyut, lifecycle ve generation kontrolleri vardır. Notification izin/
  kanal kapalıysa oynatma başlamaz. Hazırlanma 30 s, duraklatılmış oturum 10 dk
  ile sınırlıdır; audio focus kaybı, kulaklık çıkarma ve stop temizlenir.
  `await AppForgeMedia.play("music.mp3", "Başlık")` yalnız STARTING döndürür;
  gerçek durum `appforge-media` ve `AppForgeMedia.state()` üzerinden gelir.
  pause/resume/stop bildirimde ve API'de vardır. Mevcut HTML audio/video önplan
  yolu korunur; otomatik background handoff, stream, playlist veya Media3 yoktur.
- `AppForgeLocation` açık native onay ve gerçek Android COARSE/FINE kararıyla
  tek konum alır. GPS sağlanabiliyorsa internet gerekmez; provider yoksa/izin
  reddiyse/zaman aşımında hata verir. Monoton yaş ≤30 s, geçerli koordinat ve
  accuracy gerekir. Origin/storage değişmez. `AppForgeLocation.getCurrentPosition`
  ve destekleniyorsa yerel navigator.getCurrentPosition shim'i request ID ile
  gerçek fix döndürür. Sürekli izleme/background konum eklenmedi. HTTPS coarse
  WebChromeClient yolu ayrı kalır; yerel kamera/mikrofon WebRTC hâlâ normaldir.
- `AppForgeBilling` Google Play Billing 9.1.0 ile derlenir. İnternet yokken SDK
  bağlantısı kapatılır, OFFLINE olayı ve bağlantıda yeniden etkinleşme uyarısı
  gösterilir. İnternet bilgisi tek başına READY sayılmaz: SDK bağlantısı gerekir.
  AppForgeBilling.products/purchase/restore/state ve appforge-billing olayı
  gerçek SDK ürün/offer ve receipt sonuçlarını taşır. Ana köprü, güvenilir
  document/origin, token, güncel dokunuş ve önplan korumaları devam eder.
- Satın alma UI'sının açılması FLOW_OPENED'dır, ödeme başarısı değildir.
  PENDING/SUSPENDED, bilinmeyen ürün, yabancı paket, doğrulanmamış receipt ve
  başarısız acknowledge/consume hiçbir zaman COMPLETE olmaz. Tüketim token'ı
  eşleşmelidir. Receipt raw token'ı JS/olay/log'a verilmez; SHA-256 kullanılır.
  Ürün/adet/entitlement sunucu tarafında idempotent yönetilmelidir; uygulama
  yeniden kurulumu sonrası tüketilmiş ürün geçmişini istemci uydurmaz.
- İki manifest codec'i SDK'nın sabit iki proxy Activity'sini, Google Activity,
  korumalı profil receiver'ı, AndroidX provider, üç transport bileşenini,
  metadata ve signature permission'ı doğrular. Billing OFF bunları kaldırır;
  ON startup authority ve signature izni uygulamanın kendi paketine çevrilir.
  Runtime/dex kütüphaneleri sabit shell içinde kalır. Arbitrary component veya
  keyfi permission enjeksiyonu eklenmedi. MediaService de bağımsız budanır.

### Billing dış yapılandırma ve güven sınırı

En çok 64 benzersiz ürün/abonelik ID'si; consumable ID'ler ürün alt kümesi olmalı,
removeAds ürünü consumable olamaz. Varsayılan billingEnabled=false değişmedi.
Doğrulama endpoint'i HTTPS/default443, credentials/query/fragment olmadan
sağlanmalı. Backend Google Play üzerinden gerçek receipt doğrulamalı; istemci
SDK'sı sunucu yerine Google yetki anahtarı veya sahte doğrulayıcı içermez.

`AppForgePurchaseVerifier` POST JSON alanları: packageName, productId,
purchaseToken, quantity. Sunucu 200 application/json ile tam olarak verified
(Boolean true), packageName, productId, purchaseTokenSha256, quantity döndürmeli.
Yanıt aynı receipt/paket/ürün/adede bağlıdır; redirect, duplicate/eksik/fazla
alan, bozuk UTF-8, >16 KiB yanıt, TLS/network hatası reddedilir. Connect/read
7 s sınırları vardır. Bu endpoint'e canlı çağrı yapılmadı; testte HTTPS sınırı
stub'dır. Play ürün/account/license/dağıtım uygunluğu ayrıca gerekir.

### Devam turunun doğrulamaları

- Gerçek AGP: uygulama JUnit **331/331 PASS**, template JUnit **6/6 PASS**
  (2 gerçek ZXing decoder + 4 gerçek config/verifier testi). Studio debug
  `assembleDebug` PASS. İlk turlardaki manifest hataları giderilip tekrarlandı.
- Odaklı çalışma zamanı turu **10/10 PASS**: actual Kotlin motor/policy,
  HTTPS izin/picker, gerçek medya servis sınıfı, konum ve Billing kontrolörü.
  Android/codec/location/Play/network uçları stub; fiziksel kabul değildir.
- Gerçek offline packager/imza ve nihai kaynak/artifact kontrolleri aşağıdaki
  son doğrulama kaydındadır.

### Kalan kapsam

Yerel kamera capture/WebRTC ve mikrofon/WebRTC file origin değiştirilmeden
entegre edilmedi; ACTION_IMAGE_CAPTURE için yazılabilir güvenli provider yoktur
ve T21 salt okunur paylaşım provider'ı bunun için gevşetilmedi. HTTPS APK
getUserMedia yolu açık onayla mevcuttur. AdMob/UMP ve Firebase Analytics,
Crashlytics, Messaging SDK/runtime/per-app kaynak zinciri tamamlanmadı; gerçek
validator BLOCKED sonucunu korur. Bu kalan işler yalnız kullanıcı ID vermesiyle
çözülmüş sayılmaz: ek kod/SDK/manifest/CI/consent ve kabul gerekir.

Android 8–16 fiziksel cihaz, kameranın görüntüsü, mikrofon, gerçek GPS, NFC,
background codec/audio focus/OS FGS davranışı ve Play satın alımı **NOT_RUN**.
Telefon/hesap bağlantısı sağlanmadı; kurulum ve canlı hesap işlemleri ayrıca
bu görevin yasak kapsamındadır. Tüm anahtarların sorunsuz çalıştığı iddia edilmez.


### Güncel teslim ve bağımsız doğrulamalar

Teslim klasörü: `/workspace/deliverables/fast-html-v2-20261017-media-location-billing/`.
Önceki `/workspace/deliverables/fast-html-v2-20261017/` değiştirilmedi.

- APK: `AppForgeStudio-FastHTML-V2-debug-20261017.apk`, **61,179,707 bayt**.
- APK SHA-256: `d299f693110dba5f03759efd5bdf2a58cd4970641acf68418770065c28852fdd`.
- Studio debug APK apksigner doğrulaması PASS; v2=true, v1/v3/v4=false.
  Bu, generated APK smoke testinin v2+v3 imzasından farklı ve beklenen debug
  yapılandırmasıdır. Üretim/release/Play imzası yapılmadı.
- Gömülü APK template: 2,951,330 bayt,
  SHA-256 `8c4d31d8bbffd6cd485dfea15a2d25356de52f006672ab51df3cd573a555d621`.
- Gömülü AAB template: 2,841,511 bayt,
  SHA-256 `e80a36c693fa2f386d42abafba653032a3b8a518b0f16ebf3725e04d968b478b`.
- Gömülü integrity kaydı: 257 bayt,
  SHA-256 `cfea2df872622a5946d0346598fbe1ac07a3d398dd557af763085a106b5dfdbb`.
- APK içindeki iki template/integrity baytları generated AGP çıktılarıyla
  birebir eşleşir. Billing AAR'ın third-party license/index dosyaları iki
  template'in assets alanında korunur; ZXing lisansı ayrı tutulur.
- `compiled-smoke-final.log`: gerçek production packager ve signer ile OFF/ON
  iki kombinasyonda APK+AAB PASS. Java SecurityManager socket/exec çağrılarını
  yasaklar; Context/asset resolver host sınırıdır. Gerçek v2/v3 APK ve JAR/CMS AAB
  imzaları, beklenen sertifika, yanlış sertifika/unsigned/tamper retleri, config/
  medya/ikon bayt eşliği, AAPT2 bağımsız manifest/resource kimliği PASS.
- Gerçek Android 37 + Billing classes + ZXing üzerinde bütün wrapper/template
  Java dosyalarının `javac --release 8` derlemesi PASS (deprecation uyarıları var).
- Son AGP `generateFastHtmlIntegrity testDebugUnitTest assembleDebug` PASS:
  uygulama 331/331, template 6/6, skipped=0. Billing verifier'ın kaçışlı duplicate
  alan, trailing veri ve katı yanıt biçimi retleri de bu son tura dahildir.
- Stability gate PASS; snapshot deterministik check PASS; git diff --check PASS;
  iki AppForgeDocuments/LocalAssets kopyası package satırı dışında byte eşliği
  PASS. Wiki secret scan Green. Wiki audit errors=0, warnings=42 (Yellow):
  kapsam kayıtları/uzun sayfa/eksik frontmatter uyarıları release PASS sayılmadı.

İlk tam kalite turu 1088/1091 idi. İki eski regex beklentisi, artık iki yerde
kullanılan ortak candidate kapısı ve medya servisi için gereken bildirim izniyle
uyuşmuyordu; yeni davranışı denetleyecek şekilde güncellendi. Davranışı gerçek
Kotlin/Java ile çalıştıran ayrı testler korunur. Üçüncü failure, geliştirme
sırasında snapshot değiştirilirken yapılan check'ti; final kaynak sabit tutuldu.
Düzeltilmiş ayrı contract turu 11/11 PASS; nihai tam tur **1091/1091 PASS**,
failure/cancel/skip=0, süre 219.907 s. Log: `logs/full-quality-final.log`.

Kaynak patch HEAD'e göre tüm tracked/untracked değişiklikleri içerir; index
ve branch referansları değiştirilmeden reverse-apply check PASS. Güncel tam
envanter ve dosya SHA-256 değerleri `changed-files.json` içindedir. Aşağıdaki
68 dosya bu teslim kapsamındadır (önceki milestone listesinin güncel karşılığı):

- `.github/workflows/android-debug.yml`
- `.gitignore`
- `android-app/app/build.gradle.kts`
- `android-app/app/src/main/assets/device-build/AppForgeDocuments.java`
- `android-app/app/src/main/assets/device-build/AppForgeLocalAssets.java`
- `android-app/app/src/main/assets/second_brain_snapshot.json`
- `android-app/app/src/main/java/com/appforge/studio/MainActivity.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlAabProtoManifest.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlAabUnsignedPackager.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPackager.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPolicy.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlIcon.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlResourceColor.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/HtmlBinaryManifest.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/TemplateIntegrity.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/WebviewProjectSettings.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/CompiledHtmlTemplateIdentityTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/FastHtmlAabProtoManifestTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/FastHtmlIconTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/FastHtmlV2AssetsTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/QuickHtmlConfigTest.kt`
- `android-app/html-apk-template/build.gradle.kts`
- `android-app/html-apk-template/src/main/AndroidManifest.xml`
- `android-app/html-apk-template/src/main/assets/appforge-third-party/Billing-third_party_licenses.json`
- `android-app/html-apk-template/src/main/assets/appforge-third-party/Billing-third_party_licenses.txt`
- `android-app/html-apk-template/src/main/assets/appforge-third-party/NOTICE.txt`
- `android-app/html-apk-template/src/main/assets/appforge-third-party/ZXing-LICENSE.txt`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeAwake.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeBarcode.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeBilling.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeBillingConfig.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeConnectivity.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeDeepLink.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeDocuments.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeLocalAssets.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeLocation.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeMediaPaths.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeMediaService.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeNfc.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgePurchaseVerifier.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeQrScanner.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeSelection.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeWebPermissions.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java`
- `android-app/html-apk-template/src/main/res/drawable-nodpi/appforge_icon.png`
- `android-app/html-apk-template/src/main/res/values/styles.xml`
- `android-app/html-apk-template/src/test/java/com/appforge/htmlstub/AppForgeBarcodeTest.java`
- `android-app/html-apk-template/src/test/java/com/appforge/htmlstub/AppForgePurchaseVerifierTest.java`
- `docs/reports/fast-html-v2-full-capabilities-20261017.md`
- `docs/wiki/03_Architecture/Build_And_Worker_Architecture.md`
- `docs/wiki/06_Product_And_Features/Fast_HTML_V2.md`
- `quality/tests/advanced_permission_route_runtime.test.js`
- `quality/tests/fast_html_advanced_routing_runtime.test.js`
- `quality/tests/fast_html_apk_v1_contract.test.js`
- `quality/tests/fast_html_billing_runtime.test.js`
- `quality/tests/fast_html_location_runtime.test.js`
- `quality/tests/fast_html_media_runtime.test.js`
- `quality/tests/fast_html_v2_runtime.test.js`
- `quality/tests/fast_web_535_phase1_contract.test.js`
- `quality/tests/helpers/fast_html_billing_stubs.js`
- `quality/tests/helpers/fast_html_media_stubs.js`
- `quality/tests/helpers/fast_html_policy_regression.js`
- `quality/tests/helpers/fast_html_scanner_stubs.js`
- `quality/tests/helpers/fast_html_v2_android_stubs.js`
- `quality/tests/html_document_bridge_runtime.test.js`
- `quality/tools/fast_html_v2_compiled_smoke.py`


### Nihai sonuç

Tam kalite **1091/1091 PASS**; gerçek AGP uygulama **331/331** ve template
**6/6 PASS**; ağ/harici araç kapalı gerçek APK+AAB üretimi ve imza/kimlik
kontrolleri PASS. Desteklenen özellikler gerçek kaynakta entegredir. Kalan
BLOCKED/NORMAL_ENGINE_REQUIRED/EXTERNAL_CONFIG satırları geçerlidir; tüm
P1/P2/P3 veya tüm anahtarlar bitmiş değildir. Fiziksel ve canlı servis kabulü
NOT_RUN. Yerel kaynaklar commit edilmedi; HEAD/T21 başlangıç SHA'sında kaldı.
Main/T20/T21, Play/üretim, telefon ve gerçek kullanıcı verisine işlem yapılmadı.
