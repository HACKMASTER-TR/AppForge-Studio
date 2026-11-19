---
type: bug
status: active
project: AppForge Studio
created: 2026-11-19
updated: 2026-11-19
last_verified: 2026-11-19
confidence: high
tags:
  - incident-runbook
  - connection-reset
  - mobile-data
  - cloudflare-r2
  - module-readiness
  - storage
  - second-brain
related:
  - "[[Bug_Index]]"
  - "[[Universal_Download_V4]]"
  - "[[Per_Module_Isolation_V1]]"
  - "[[Device_Build_Runtime_V3]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/V9SignedR2CandidateDownload.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/V9R2ObjectTransfer.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DownloadNetwork.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/IsolatedModuleInstallerV3.kt"
  - "android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt"
  - "cloudflare/package-distribution/worker.mjs"
  - "quality/tests/v543-reliable-module-ready-storage-network.test.js"
  - "scripts/generate-second-brain-snapshot.py"
---

# Tekrarlayan arızalar: R2 erişimi, READY durumu, depolama ve CI (2026-11-19)

> **İlk okunacak operasyon kaydı.** Yeni bir arızada önce belirtinin bu sayfadaki imzasıyla eşleşip eşleşmediğine bak. Eşleşiyorsa eski başarısız denemeleri tekrarlama; aşağıdaki **salt-okunur tanı -> kanıt -> sınırlı düzeltme -> yeniden kabul** sırasını kullan. Belgelenen bir geçici çözüm, cihazda doğrulanmış kalıcı çözüm değildir.

## A. Olay özeti ve doğrulanmış kanıtlar

- **2026-11-19 / V543 / mobil veri:** Telefonun Termux ortamında GitHub IPv4 `HTTP 200`, `CURL_EXIT 0`; `www.cloudflare.com` IPv4 `HTTP 200`, `CURL_EXIT 0`. Buna karşın `https://appforge-v9-packages-readonly.28550040284a.workers.dev/` için hem IPv4 hem IPv6 `curl: (35) Recv failure: Connection reset by peer`, `HTTP 000`, TLS süresi `0`. Sorun **genel internet erişimi değil**, eski alan adı/ağ yolu ile ilişkili bir HTTPS erişim sorunudur. Reset işlemini hangi ara bileşenin yaptığı **bilinmiyor**; operatörün engellediği kesin bilgi değildir.
- **Cloudflare tarafı:** Var olan `appforge-v9-packages-readonly` Worker'ı açık ve deploy edilmişti. Aynı Worker için **`packages.appforgecloud.com`** özel alan adı bağlandı (2026-11-19). Mobil veri üzerinden yeni adreste IPv4 `HTTP 404 / TLS 0.276708 s / EXIT 0`, IPv6 `HTTP 404 / TLS 0.256404 s / EXIT 0` görüldü. Worker ana dizinde paket sunmadığından **404 burada HTTPS erişim başarısıdır**, paket indirme başarısı değildir. **Gerçek imzalı nesne için HEAD=200, byte-range GET=206 ve bütünlük** testi henüz yapılmadı.
- **Önemli kaynak ayrımı:** V543 APK'daki `V9SignedR2CandidateDownload.ORIGIN` hâlâ `workers.dev` adresine sabitlenmişti. Cloudflare'da yeni alan adı açmak **mevcut APK indirme adresini otomatik değiştirmez**. Yeni adresi üretim kodunda kullanmak ayrı, testli ve imzalı bir sürüm çalışmasıdır; V544 burada yalnızca *önerilen sonraki iş*tir.
- **Modül/READY fiziksel kontrolü:** V543'te Fast HTML ve ortak Linux `HAZIR ✓`, indirme düğmesi gizli olarak gözlendi. Node.js + npm için `Bağlantı bekleniyor` / `Connection reset`, READY **değil**. Windows Native, Rust Android NDK ve Expo gerçek cihaz kurulum/fonksiyon testleri **NOT_RUN veya kabul edilmemiş**; CI başarısı bunları READY yapmaz.
- **Depolama:** Önceki AppForge kaldırılırken yaklaşık **35 GB** alan geri kazanıldı. Hangi klasörlerin bu alanı kullandığı doğrulanmadı; tek nedenin üç problemli modül olduğu ispatlanmadı. V543 Linux kurulumu sonrası Android uygulama depolama ekranında toplam **227 MB** (uygulama yaklaşık 84,78 MB, veri 142 MB, önbellek 233 KB) görüldü. Bunlar farklı kurulumlardır; doğrudan büyüme kıyaslaması yapılmaz.

## B. İlk 5 dakikada güvenli tanı

1. **İndirme ve yeniden kurmayı durdur.** Özellikle `Connection reset`, `WAITING_CONNECTION`, `ISOLATED_CANDIDATE_REPAIR_REQUIRED`, `SDK_LICENSE_FILE_MISSING` veya `GXX_*` durumlarında `İndir ve Kur` düğmesine tekrar tekrar basma. Uygulamayı kaldırma; **Verileri temizle**, rootfs silme, `rm -rf`, dpkg `--force` veya güvenilmeyen APK kurma yok.
2. **Durumu ayır:** Uygulama sürüm/kod, modül kimliği, durum ve hata kodu, işlem ID, R2 doğrulanmış/eksik nesne sayısı, indirme süresi/byte, cihazdaki kullanılabilir alan ve Android uygulama verisi boyutu kaydedilsin. `HAZIR` yalnızca dosya+receipt+gerçek fonksiyon testlerinden sonra geçerlidir. Ortak Linux `HAZIR`, ayrı Node/Expo/NDK Ubuntu ortamlarını otomatik `HAZIR` yapmaz.
3. **Ağ yolunu ölç** (aşağıdaki komutlar yalnız kök dizinde küçük HTTPS kontrolü yapar; paket indirmez). Termux ayrı bir uygulama olduğundan sonucu AppForge'un Android ağ izniyle karıştırma.
4. **Sunucu durumunu doğrula:** Worker dağıtımı ve custom domain etkin mi? Hata yalnız eski host içinse alternatif hostu test et. Yeni host `404` dönüyorsa bunu paket bulunamadı diye yorumlama; yol `/` için normal olabilir.
5. **Değişiklik/yeniden deneme kapısı:** Önce gerçek imzalı nesne üzerinde read-only `HEAD` ve kontrollü `Range` testi; kodda host değişiyorsa kaynak izin listesi ve negatif güvenlik testleri. Ancak tam Ubuntu CI + imzalı APK + gerçek cihaz kabulü ile `PASS`.

### Termux ağ tanısı (mobil veri; panoya kopyalama isteği ve dosya yedeği)

```sh
REPORT="$HOME/appforge-r2-incident.txt"
{
  echo 'APPFORGE_R2_INCIDENT=START'
  for item in 'GITHUB|https://github.com/' 'CLOUDFLARE|https://www.cloudflare.com/' \
              'R2_OLD|https://appforge-v9-packages-readonly.28550040284a.workers.dev/' \
              'R2_NEW|https://packages.appforgecloud.com/'; do
    name=${item%%|*}; url=${item#*|}
    echo "CHECK=$name"
    curl -4 --noproxy '*' --connect-timeout 8 --max-time 15 -sS \
      -o /dev/null -w 'HTTP=%{http_code} IP=%{remote_ip} TLS=%{time_appconnect}\n' "$url"
    echo "CURL_EXIT=$?"
  done
  echo 'APPFORGE_R2_INCIDENT=END'
} >"$REPORT" 2>&1
cat "$REPORT"
if command -v termux-clipboard-set >/dev/null 2>&1; then
  timeout 12s termux-clipboard-set <"$REPORT" 2>/dev/null || true
fi
printf 'REPORT_FILE=%s\n' "$REPORT"
```

**Tanı yorumu:** `curl 35 + HTTP000 + TLS 0` eski hosta özgü bağlantı sıfırlanmasıyla uyumludur; kesin fail noktası için ağ kaydı gerekir. `curl 0 + HTTP404` yalnızca HTTPS bağlantısını kanıtlar. **Hiçbir durumda** `-k/--insecure`, sahte SHA, alternatif yetkisiz CDN, imzasız paket veya kontrolsüz yeniden deneme kullanılmaz.

## C. Tekrarlama önleme kuralları

| Tetikleyici / arıza imzası | Önce kontrol et | Güvenli davranış | Kalıcı regresyon kapısı |
|---|---|---|---|
| `Connection reset by peer`, `curl(35)`, `HTTP000` yalnız `workers.dev` | IPv4+IPv6 eski/yeni host TLS, Worker dağıtımı, custom domain | Kısmi SHA-adresli R2 verisini koru; paketleri tekrar tekrar indirme; kaynak adresi değişirse yalnız onaylı/sabitlenmiş kaynağı kullan | CI'de gerçek imzalı nesne `HEAD/Range`, TLS doğrulaması, 206/Content-Range/boyut/son SHA-256; mobil ağ fiziksel kabulü |
| Modül READY fakat `İndir ve Kur` var | UI readiness ile `IsolatedModuleInstallerV3.ready()` aynı kanıta dayanıyor mu? | READY modülde butonu gizle; bütünlük başarısızsa READY ilan etme | Fiziksel yeniden açılış, önbellek ve receipt yeniden kontrolü, tekrar indirme sıfır ağ baytı |
| `Yarım kalan bağımsız Ubuntu` veya artan depolama | Korunan candidate/stage, rootfs kopyaları, dosya sahipliği, app storage | Yeni indirme/kopyayı önce engelle; hiçbir güvenilmeyen klasörü otomatik silme | Hata/iptal/proses ölümü sonrasında depolama farkı ve güvenli recovery testi |
| Ortak Linux HAZIR ama Node/Expo/NDK `Kurulu değil` | Ortak `DeviceBuildRuntimeV3.isReady` ile hedefe özel receipt ve fiziksel testler | Durumu açıkça ayrı göster; ortak Linux'u diğer modül kabulü sayma | Her modülün gerçek çalıştırma/derleme testi |
| `dl.google.com UnknownHostException` ve `APPFORGE_ANDROID_SDK_LICENSE_FILE_MISSING` | SDK lisans dosyası ve gerçek `sdkmanager --licenses` işlemi; mobil DNS | Lisansı uydurma/manuel başarı yazma yok; önce resmi onay+DNS | Rust Android NDK ve Expo gerçek cihaz kurulumu; hâlen kabul edilmedi |
| Windows Native `update-alternatives ... gcc-posix ... doesn't exist` veya `GXX_*` | İmzalı R2 gcc/g++ sürücü SHA ve ELF mimarisi | Yetkisiz paketi/dpkg force kullanma; hasarlı sürücüyü READY sayma | Gerçek MinGW C/C++ derleme ve PE32+ çıktı; hâlen kabul edilmedi |
| Termux `ProtoLogNative` çökmesi, `/tmp` reddi, hardlink `EACCES` | Termux/Android hostunda tam Linux testi çalıştırılmış mı? | Ürün arızası gibi etiketleme; Ubuntu CI ile ayrı doğrula | Ubuntu üzerinde tüm kalite testleri PASS, yalnız hedefli Termux testiyle yetinme |
| `STOP: SNAPSHOT_OUT_OF_DATE` | `scripts/generate-second-brain-snapshot.py --check` | Snapshot'ı kaynak koddan yeniden üret; elle düzenleme | Deterministik `--check`, wiki audit, CI |
| Push sonrasında `REMOTE_SHA_MISMATCH` | PR head SHA ve ilgili GitHub CI aynı commit'te mi? | Önce taze API verisini oku; ikinci commit/force push yapma | PR head ve CI head SHA birebir kontrol |
| APK güncelleniyor | paket adı, versionCode, signer SHA, kaynak SHA ve başarılı CI | Kaldırmadan aynı imzayla yerinde güncelle; gerçek cihaz kabulünü ayrı tut | SHA-256 ve sertifika+APK metadata karşılaştırması |

## D. Tekrarını gerçekten önleyecek henüz yapılmamış işler

1. **V544 aday gereksinimi:** İmzalı paket kaynağı için kontrollü `packages.appforgecloud.com` geçişi; sabit izin listesi; `workers.dev`/özel host seçiminde yetkisiz açık yönlendirme yok. İndirmeden önce gerçek imzalı içerik için erişim kontrolü. Host değişimi veya failover yeni güven sınırı oluşturmasın.
2. **Ağ sağlığı bildirimi:** `HTTP000/TLS reset`, `HTTP403/404`, imzalı nesne yok, R2 bağlantı bekleme ve SDK/DNS hatalarını ayrı kodlarla göster; hata kodunu ve yalnız gerekli teşhis verilerini (credential/PII olmadan) panoya kopyala.
3. **Depolama bütçesi:** Her bağımsız rootfs, stage, cache ve quarantine alanı için ölçüm ve üst sınır; kuruluma başlarken boş alan kabulü; korunan fakat başarısız kalan stage için kullanıcı onaylı, hash/owner doğrulamalı kurtarma. Aynı nesnenin ağdan indirilmesini sıfırla; ayrı kurulu dosyalar güvenlik gereği kalabilir.
4. **Fiziksel regresyon:** Mobil veriyle tek hedef Node.js, Windows Native, Rust Android NDK, Expo; süre, başlangıç/bitiş depolama boyutu, indirme GET sayısı, READY kaydı, gerçek derleme sonuçları, yeniden başlatmada READY kalıcılığı. Henüz tamamlanmadan `PHYSICAL_PASS` yazma.
5. **Arıza bilgi geri beslemesi:** Yeni arıza önce bu runbook'taki tetikleyici ile eşleştirilir; eşleşmiyorsa tam günlük ve kanıtla yeni alt başlık açılır. Çözüm sonrası tam CI ve fiziksel kanıt ile doğrulama tarihi güncellenir. Sırf CI geçişi cihaz hatasını kapatmaz.

## E. Değişiklik ve kabul durumu (2026-11-19)

- Kaynak: PR #86, imzalı V543 kaynak SHA `0db6e1e70682b07ce03d68f5df561f484d0b9efc`; imzalı APK CI `44511479352` ve Stability CI `44512653570` başarılı (GitHub kaydı). **V543 Linux/Fast HTML READY UI** ekranda gözlendi.
- **Açık kalanlar:** Gerçek R2 nesnesini yeni hostname üzerinden indirme, V544 APK adres değişimi, Node.js gerçek kurulum, Windows Native / Rust Android NDK / Expo fiziksel kabulü, depolama sınırı/stage kurtarma tam kabulü.
- **Güvenlik:** Prod Play sürümü, mevcut uygulama verileri, imzalar, R2 release-manifest, eski rootfs ve kullanıcı projeleri otomatik değiştirilmez. Bu sayfa bir bilgi kaydıdır; kodun otomatik kendini onarmasını sağlamaz.

**Sonraki sohbet başlangıcında:** `Bug_Index` → bu kayıt → ilgili Kotlin/Worker kaynakları → mevcut PR head/CI → fiziksel sonuçlar sırasını izle. Eski `workers.dev` adresine tekrar tekrar indirme denemesi veya kullanıcıya uygulamayı kaldırma önerisiyle başlama.
