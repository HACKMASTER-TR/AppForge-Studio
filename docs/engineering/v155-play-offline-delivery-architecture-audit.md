# AppForge Studio V15.5 — Google Play Üzerinden Çevrimdışı Araç Teslimatı

**Durum:** Araştırma / salt okunur mimari kanıtı. **Uygulama, modül ve dağıtım yapılmadı.**

- **İncelenen repo:** `HACKMASTER-TR/AppForge-Studio`
- **Sabit kaynak SHA:** `63e496913332cf2e2ae96b2e67ae7414226f09fe`
- **Tarih:** 2026-10-14
- **Koruma:** `main`, Play Production, fiziksel cihazdaki AppForge, kullanıcı projeleri, anahtarlar ve diğer çalışma ağaçları değişmez.
- **Uzak dal:** `research/v155-play-offline-delivery-20261014` oluşturulamadı. Bağlı GitHub hesabı sadece okuma yetkili; `create_branch` isteği HTTP 403 döndürdü.
- **Kaynak testi:** 16 ilgili kaynak dosyası GitHub konektörüyle okundu; 25 statik kontrol eşleşti. Bunlar *Play kabulü, cihazda çalıştırma veya gerçekten indirilen bayt sayısı testi değildir*.

## 1. Problemin doğru tanımı

Hedef: Kullanıcı AppForge'u Google Play'den yükleyebilsin, hafif yerel statik HTML APK/AAB üretimini ekstra araç indirmeden kullanabilsin ve desteklenen daha ağır derleme araçlarını uygulama içinde **isteğe bağlı** edinebilsin. Talep edilen ikinci kısım, Google Play'in kod teslimatı kurallarını ve Android çalıştırma kısıtlarını aşmadan gerçekleşmeli.

**Önemli:** Google Play'in bir dosyayı teslim edebilmesi, o dosyanın Play politikası açısından dağıtılabilir olduğu veya Android'de yürütülebileceği anlamına gelmez.

## 2. Kanıtlanmış mevcut kaynak davranışı

1. `OfflineBuildPackManager.kt` içinde `offline-build-pack-v1` sürümü, **tahmini** 1.800.000.000 bayt indirme ve 4.500.000.000 bayt kurulu boyut bulunuyor. Bunlar fiziksel ölçüm değil, sabit tahminler.
2. `DeviceBuildRuntimeV3.kt`, `noBackupFilesDir/device-build/runtime-v3` altında terminalin Linux ortamından ayrı Ubuntu çalışma ortamını kuruyor. Kaynağı SHA-256 ile sabitlenmiş Ubuntu Base 24.04.4 arşivi; indirme, doğrulama ve açma ayrı adımlar.
3. `ProrootRuntimeContract.kt` paketlenmiş PRoot başlatıcısını yalnız `arm64-v8a` ABI için tanımlıyor. Linux manifest listesinde farklı ABI'lerin bulunması, aynı uygulama içi PRoot çalıştırmasının onlar için desteklendiğini **kanıtlamaz**.
4. `install-toolchain.sh`, Ubuntu içinden JDK 17, Gradle 9.3.1 / 8.14.3, Android SDK API 37, Build Tools 36, Node, Python ve Expo bileşenlerini indiriyor ya da `apt-get` ile kuruyor.
5. Expo için Node.js **22.23.3** ve NDK **27.1.12297006** sabitlenmiş. ARM64 Linux üzerinde resmi Linux SDK/NDK araçlarıyla ABI uyuşmazlığı görüldüğünden özel Ubuntu ARM64 CMake/LLVM köprüleri var.
6. Normal Node Web hattı Ubuntu `apt-get install nodejs npm` kullanıyor; `npm --offline` yalnız daha önce önbelleğe alınan projeye özgü bağımlılıklara uygulanabilir.
7. `prepare-offline-pack.sh` Android ve Python için örnek projeleri önceden Gradle ile derliyor; Node için `npm cache verify` çalıştırıyor. Bu, keyfi bir `package-lock.json` veya keyfi Gradle yapılandırmasının eksiksiz çevrimdışı çalışacağını kanıtlamaz.
8. Python şablonu `com.chaquo.python` 17.0.0 kullanıyor. Windows Native hattı MinGW-w64, CMake ve Ninja gerektiriyor.
9. Windows Portable Host dosyası ayrı bir GitHub Release kaynağından **375.039.759 bayt** olarak, boyut + SHA-256 kontrolüyle indiriliyor. Android içinde Windows EXE olarak çalıştırıldığı iddia edilmez. Bunun politika sınıflandırması yine bağımsız değerlendirme ister.
10. `OfflineBuildPackManager.status()` bileşen `.ready` marker dosyalarının sürümünü kontrol ediyor; her kontrolde derleyici dosyalarının tamamını SHA ile doğrulamıyor.
11. `FastHtmlApkPolicy` kapsamındaki statik Android çıktıları ek SDK/JDK/Gradle gerektirmeden paketlenmiş şablonları kullanıyor. Windows veya karışık çıktılar Fast HTML yolu üzerinden sessizce geçmiyor.
12. İlk açılış paket ekranında **ŞİMDİLİK ATLA** var. Opsiyonel teslimat tasarımında korunmalı.
13. `PlayPlatformCapabilities.kt`, `asset_delivery` ve `feature_delivery` durumlarını `CODE_READY` olarak gösteriyor. `PlayPlatformServices.kt` manager factory'leri sağlıyor. **Çalışan bir paket teslimat modülü tanımlanmamış:** `settings.gradle.kts` ve `app/build.gradle.kts` içindeki dinamik feature/asset-pack modülü yok.

## 3. Kritik mevzuat ve teknik engeller

| Kod | Kanıt / engel | Sonuç | Çıkış koşulu |
|---|---|---|---|
| P0 | Google Play Device and Network Abuse: Google Play dışından DEX/JAR/`.so` gibi çalıştırılabilir kod indirilmesi kısıtlı; yorumlanan diller için koşullu istisna bulunuyor. | Ubuntu rootfs, JDK, Node, LLVM, Maven JAR ve npm native binary dağıtımlarını sıradan harici indirme gibi varsaymak uygun değil. | Bileşen türüne göre resmi politika uygunluk değerlendirmesi. |
| P0 | Play Asset Delivery dokümanı asset pack'lerin çalıştırılabilir kod içermediğini söylüyor. | Büyük toolchain arşivini `.zip` içine koyup PAD'ye taşımak güvenli varsayım değil. | Yalnız doğrulanmış kod olmayan varlıkları PAD adayı yap. |
| P0 | Android 10+ W^X: Yazılabilir app home içindeki dosyalara doğrudan `execve()` sınırlı. | PRoot içindeki Linux ELF ikililerinin *gerçekte hangi yürütme yolunu* kullandığı test edilmeden değişiklik yapılamaz. | Ayrı test package ID üzerinde API 29+ fiziksel cihaz deneyleri. |
| P1 | Play Feature Delivery, feature modüllerinin dex/resources/native kütüphanelerini sonradan yükler. | Bu, rastgele Ubuntu `apt` veya JDK CLI ikililerini otomatik olarak kurup çalıştıran bir paket yöneticisi değil. | Minimal on-demand feature + yerel native/JVM süreci için kanıt. |
| P1 | Mevcut PRoot yalnız `arm64-v8a`; Linux rootfs manifestleri daha geniş. | Gerçek uygulama destek ABI matrisi geniş varsayılamaz. | Paket/modülün gerçek ABI hedeflemesi ve cihaz testleri. |
| P1 | Paket kurulumu `apt`/`curl`/`npm`/Gradle indirmeleri yapıyor. | İnternetsiz derlemenin gelecekte çalışması, sadece bir defalık `.ready` dosyasına bağlanamaz. | Bağımlılık listesi/manifest + tam hash + offline build acceptance. |
| P1 | Kaynakta gerçek boyut telemetrisi yok. | PAD/PFD/ana paket limitlerine sığıp sığmadığı bilinmiyor. | Her dosyanın sıkıştırılmış/kurulu gerçek boyutunun ölçülmesi. |

Resmi kaynaklar:

- [Google Play — Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646)
- [Play Asset Delivery overview — no executable code](https://developer.android.com/guide/playcore/asset-delivery)
- [Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery)
- [On-demand feature delivery; SplitCompat + native libraries](https://developer.android.com/guide/playcore/feature-delivery/on-demand)
- [Android 10 behavior changes — executing app home files](https://developer.android.com/about/versions/10/behavior-changes-10)
- [Test Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery/test)

**Yorumlama sınırı:** Script arşivleri/JAR/JS/Windows PE gibi dosyaların her biri için politika hükmünü tek satırdan kesinleştirmiyoruz. İstisnalar, yürütme şekli, kaynak ve güncelleme yöntemi ayrı incelenecek. İndirilmiş araç zincirinin şu an çalışması da Play onayı kanıtı değildir.

## 4. Gerçekçi mimari adayları

### A — Play tarafından sağlanan kod modülleri (PFD), küçük ve kontrollü

- Google Play'in kendi dağıttığı on-demand dynamic-feature modülünde derlenmiş Android kodu ve uygun native library'ler.
- Base APK'da yalnız kullanıcı arayüzü + statik hızlı derleme + kontrollü indirme deneyimi.
- `SplitInstallManager` durumlarını (pending, downloading, requires user confirmation, installed, failed) ve `SplitCompat` erişimini kontrol et.
- *Henüz kanıtlanmamış:* Temurin JDK, Node ve PRoot aracılığıyla işletilen genel amaçlı Linux binary/JAR/Gradle dağıtımının feature modülüne teknik ve politika açısından sığacağı.
- Daha önce indirilen modüllerin Play güncellemesi, kaldırma/yeniden yükleme ve ABI değişiminde nasıl ele alınacağını test et.

### B — PAD ile yalnızca çalıştırılmayan içerik

- Şablonlar, statik kaynak dosyaları, belgeler ve yürütülmeyen referans dosyaları için potansiyel aday.
- JDK/Node/Gradle binary/JAR/SDK executable gibi bileşenleri adını değiştirip PAD içine **koyma**.
- PAD birincil olarak oyun kaynaklarına odaklıdır; AppForge'ın ürün sınıfı ve kullanımına ilişkin Play uygunluğu ayrıca doğrulanmalı.

### C — Mevcut altyapının geçici korunması / destek kapsamını daraltma

- Fast HTML APK/AAB, ana uygulama içinde çalışmaya devam etsin.
- Henüz Play uyumu kanıtlanmayan gelişmiş çevrimdışı motorlara Play sürümünde başarılı/kurulabilir etiketi verme.
- Google Play sürümü için kullanıcıyı yanıltmayacak şekilde 'bu motor şu anda Play kanalında desteklenmiyor' uyarısı gösterilebilecek bir tasarım **önerisi**; henüz kod değişikliği yapılmadı.
- Cloud build seçenekleri istenirse ayrı değerlendirilir; **çevrimdışı derleme yerine geçtiği iddia edilmez**.

### D — Karma model (yalnızca ispatlanan parçalar)

- Play feature (uygun ve teknik olarak çalışan kod) + Play asset (yürütülmeyen veriler) + ana APK'da Fast HTML.
- Paket yönetimini manifest/sürüm bileşenleriyle yönet: `NOT_INSTALLED`, `DOWNLOADING`, `VERIFYING`, `READY`, `DEGRADED`, `BLOCKED_BY_POLICY`.
- Hazırlık durumunu yalnız marker değil, **dizin + beklenen yürütülebilir/asset dosyaları + hash + hafif smoke test** kanıtıyla belirle.

**Öneri:** D aday olarak çalışılsın, ama A ve B için kanıt kapıları geçilene kadar hiçbiri yayınlanmasın. C kullanıcı deneyiminin koruyucu fallback'i olarak planlansın.

## 5. Dosya düzeyinde kabul manifesti tasarımı

Sadece doküman tasarımı; çalışan kod değildir.

Her teslim edilebilir bileşen için:

```json
{
  "component_id": "example-non-executable-template",
  "revision": "v1",
  "source_sha": "PINNED_SOURCE_COMMIT_SHA",
  "artifact_type": "data-only",
  "delivery_channel": "play-asset-delivery-on-demand",
  "platform_abi": "universal",
  "compressed_bytes": null,
  "installed_bytes": null,
  "sha256": "REQUIRED_BEFORE_PUBLISH",
  "license_id": "REVIEW_REQUIRED",
  "policy_review": "PENDING",
  "physical_device_acceptance": "PENDING"
}
```

`data-only` sınıfı kod içeren arşivlere verilemez. Tüm `PENDING` alanları doğrulanmadan paket `READY` olmaz. SHA/size alanları repository yorumundan değil, doğrulanmış release artifact'ından ölçülür. Kullanıcı projelerinin içeriği asla bu manifestte yer almaz.

## 6. Deney matrisi (yalnız izole test uygulamasında)

| ID | Test | Başarı kanıtı | Fail-closed |
|---|---|---|---|
| T01 | `bundletool` on-demand feature istek/kurulum | Sistem paket listesinde feature ve uygulama event durumları | Modül yoksa özelliği açma |
| T02 | Feature içindeki JNI `.so` yükleme | `System.loadLibrary`/uygun native yükleme başarılı | Crashi değil net hata |
| T03 | Native CLI `ProcessBuilder` / `execve` davranışı | API 29+ fiziksel Android ARM64, exit code ve dosya yolu | Block / disable |
| T04 | Play içindeki JDK/Gradle çalışabilirliği | `java -version`, basit offline Gradle assemble | `BLOCKED` |
| T05 | Node/npm çevrimdışı gerçek proje | Sabit örnek proje lockfile ile network kapalı `npm ci` | Eksik bağımlılık listesi |
| T06 | SDK/NDK/CMake/Expo ARM64 | Sabit örnek gerçek native build + çıktı denetimi | ABI/NDK mismatch ret |
| T07 | Python/Chaquopy çevrimdışı | Örnek proje APK üretimi | Dep cache eksikse ret |
| T08 | Windows Portable / Native | Ayrı EXE doğrulama ve Windows fiziksel smoke | Sahte PASS yok |
| T09 | Play teslimat yaşam döngüsü | İndirme duraklat/sürdür, depolama yetersiz, Wi-Fi koşulu, yeniden başlat | Eski doğrulanmış paket korunur |
| T10 | Güncelleme uyumluluğu | App update sonrası modül + cache doğru revizyon | Yanlış sürüm kullanılmaz |
| T11 | Temiz kurulum | Yeni *ayrı paket ID'li* uygulama, gerçek Play kanalından indirir | Debug sideload Play teslimatı kanıtı değildir |
| T12 | Korunan ürün testi | Main / Production / mevcut kurulu AppForge / kullanıcı projelerinde değişiklik yok | İlk sapmada dur |

**Yerel test sınırlaması:** `bundletool --local-testing` dağıtımı ve durum makinesini simüle eder; Play sunucusundan gerçek indirmeyi, update davranışını ve bazı ağ koşullarını doğrulamaz. Son aşama internal test/IAS ile *ayrı ve onaylı* çalışma gerektirir.

## 7. Onay kapıları ve kapsam

- **Gate 0 — Kaynak doğrulaması:** Bu doküman ve yanında bulunan yalnız okuma yapan `audit-baseline.mjs`.
- **Gate 1 — Ölçüm ve lisans:** Gerçek dosya boyutu, lisans, sürüm, provenance ve SHA tam listesi. Üçüncü taraf ARM64 Build Tools ayrı denetlenecek.
- **Gate 2 — Teknik fizibilite:** Önce minik dynamic-feature/JNI deneyi, sonra `execve`, JDK, Node, Gradle. PRoot mevcut kurulu AppForge'a taşınmaz.
- **Gate 3 — Play kanal kabulü:** Gerçek Play delivery (internal/IAS), gerekirse Play politika açıklığının teyidi.
- **Gate 4 — Tasarım onayı:** Kanıtlara göre desteklenen motor listesi, kullanıcı arayüzü, geri alma ve yükseltme planı.
- **Gate 5 — Ürün kodu:** Yalnız açık onay verilmiş izole dal; CI/PR/release ayrı ayrı onaylanır. `main` ve Production dokunulmaz.

### Değişmeyecekler

1. Üretimdeki `main` commit ve Play yayınlama süreçleri.
2. Kullanıcıların projeleri, keystore'ları, eski çevrimdışı cache ve Terminal rootfs dosyaları.
3. Fast HTML APK/AAB ve V15.3 Windows EXE yönlendirme mantığı.
4. AppForge kurulu uygulama ve app data; deneyler yalnız ayrı test paketi / ayrı çalışma alanında.

## 8. Bu turun gerçek sonucu

**Kaynak incelemesi ve statik sözleşmeler:** tamamlandı. **GitHub uzak branch / commit:** hayır, connector yazma yetkisi vermiyor ve 403 dönüyor. **Çevrimdışı Play paket uygulaması:** yazılmadı. **Fiziksel test:** yapılmadı. **Play Console:** değiştirilmedi. Sonraki fiili adım, yetkilendirilmiş bir GitHub yazma kanalı açmak ya da burada verilen patch'i kullanıcının kendi izole çalışma ağacına uygulamaktır. Bu doküman tek başına politika onayı değildir.

## 9. Kaynak bağlantıları

- [Kaynak commit](https://github.com/HACKMASTER-TR/AppForge-Studio/commit/63e496913332cf2e2ae96b2e67ae7414226f09fe)
- [OfflineBuildPackManager.kt](https://github.com/HACKMASTER-TR/AppForge-Studio/blob/63e496913332cf2e2ae96b2e67ae7414226f09fe/android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt)
- [DeviceBuildRuntimeV3.kt](https://github.com/HACKMASTER-TR/AppForge-Studio/blob/63e496913332cf2e2ae96b2e67ae7414226f09fe/android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt)
- [install-toolchain.sh](https://github.com/HACKMASTER-TR/AppForge-Studio/blob/63e496913332cf2e2ae96b2e67ae7414226f09fe/android-app/app/src/main/assets/device-build/install-toolchain.sh)
- [prepare-offline-pack.sh](https://github.com/HACKMASTER-TR/AppForge-Studio/blob/63e496913332cf2e2ae96b2e67ae7414226f09fe/android-app/app/src/main/assets/device-build/prepare-offline-pack.sh)
- [LinuxRuntimeFoundation.kt](https://github.com/HACKMASTER-TR/AppForge-Studio/blob/63e496913332cf2e2ae96b2e67ae7414226f09fe/android-app/app/src/main/java/com/appforge/studio/terminal/LinuxRuntimeFoundation.kt)
- [ProrootRuntimeContract.kt](https://github.com/HACKMASTER-TR/AppForge-Studio/blob/63e496913332cf2e2ae96b2e67ae7414226f09fe/android-app/app/src/main/java/com/appforge/studio/terminal/ProrootRuntimeContract.kt)
- [PlayPlatformCapabilities.kt](https://github.com/HACKMASTER-TR/AppForge-Studio/blob/63e496913332cf2e2ae96b2e67ae7414226f09fe/android-app/app/src/main/java/com/appforge/studio/play/PlayPlatformCapabilities.kt)
- [WindowsPortableHostStore.kt](https://github.com/HACKMASTER-TR/AppForge-Studio/blob/63e496913332cf2e2ae96b2e67ae7414226f09fe/android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableHostStore.kt)
