# AppForge Second Brain — Reference Projects

Updated: 2026-09-11

## Ana hedef

Renuvex mühendislik disiplini
+ VS Code modülerliği
+ Expo EAS build/release deneyimi
+ Turborepo build hızı
+ n8n entegrasyon mimarisi
+ Coder/code-server cloud workspace yaklaşımı
+ Sentry observability
= AppForge Studio hedef mimarisi.

---

## 1. Renuvex Product Reviews — Mühendislik disiplini

Renuvex, AppForge için kod kalitesi ve proje disiplini referansıdır.

AppForge'a uygulanacaklar:

- Net modül sınırları
- Feature bazlı kod organizasyonu
- Güçlü CI quality gate
- Unit, integration ve E2E testler
- Cross-browser / cross-platform testler
- Performans bütçeleri
- Production observability
- Generated APK, log ve .bak dosyalarını source tree dışında tutmak
- Büyük dosyalara sürekli özellik eklemek yerine parçalamak
- Yeni özellikten önce sürdürülebilir mimariyi korumak

Hedef:

Renuvex seviyesinde mühendislik disiplini
+ AppForge'un mevcut geniş özellik seti.

---

## 2. VS Code — Modüler IDE mimarisi

VS Code, AppForge çekirdek mimarisi için ana referanstır.

AppForge modülleri uzun vadede şu sınırlarla ayrılmalıdır:

- core
- editor
- terminal
- files
- git
- ssh
- build
- ai
- connections
- extensions
- production
- settings

Uygulanacaklar:

- MainActivity gibi aşırı büyüyen dosyaları parçala
- UI, state ve business logic'i ayır
- Özellikleri bağımsız modüller haline getir
- Plugin / Extension API oluştur
- Plugin yetki sistemi oluştur
- Güvenilmeyen projeler için Restricted / Trusted Workspace mantığı ekle
- Terminal ve dış komut çalıştırmayı permission sistemiyle kontrol et

Amaç:

AppForge büyüdükçe tek parça uygulama olmaktan çıkmalı.

---

## 3. Expo EAS — Builder ve release sistemi

Expo EAS, AppForge Builder için referanstır.

Build profilleri:

- development
- preview
- production
- play

AppForge build akışı:

Build
→ Test
→ Sign
→ Package
→ Verify
→ Publish / Submit

Uygulanacaklar:

- Merkezi appforge build config
- Build geçmişi
- Artifact geçmişi
- Signing yönetimi
- Play Store metadata yönetimi
- Environment profilleri
- Aynı doğrulanmış artifact'i yeniden build etmeden promote etme
- Failure reason ekranı
- Build Compare

Amaç:

AppForge Builder yalnızca uzak Gradle çalıştırıcısı değil,
tam bir release platformu olmalı.

---

## 4. Turborepo — Build performansı ve cache

Turborepo, AppForge build performansı için referanstır.

Uygulanacaklar:

- Task dependency graph
- Input fingerprint
- Incremental build
- Değişmeyen modülleri tekrar build etmeme
- Gradle cache
- npm / pnpm cache
- Generated-code cache
- Worker'lar arasında güvenli cache reuse
- Cache hit / miss gözlemlenebilirliği
- Yalnız etkilenen build stage'lerini tekrar çalıştırma

Amaç:

APK/AAB build süresini düşürürken
reproducible build yapısını korumak.

---

## 5. n8n — Connector SDK

n8n, AppForge Connections mimarisi için referanstır.

Tüm servisler ortak Connector SDK üzerinden çalışmalıdır.

Örnek connectorlar:

- GitHub
- Railway
- Firebase
- Supabase
- Vercel
- GitLab
- Bitbucket
- AWS
- Cloudflare

Her connector:

- Ayrı modül olmalı
- Minimum gerekli yetkiye sahip olmalı
- Credential'ları şifreli tutmalı
- Versioned contract kullanmalı
- Bağımsız test edilebilmeli
- Ana UI'a hard-code edilmemeli

Amaç:

Yeni entegrasyon eklemek için uygulamanın her yerini değiştirmek gerekmemeli.

---

## 6. Coder / code-server — Terminal Ultimate

Coder ve code-server,
AppForge Terminal'in uzun vadeli referansıdır.

Uygulanacaklar:

- Kullanıcı başına izole Linux workspace
- Proje başına izole workspace
- Persistent terminal sessions
- Cihaz değiştirince terminal oturumunu geri yükleme
- Cloud compute
- Background build
- Workspace snapshots
- CPU / RAM quota
- Storage quota
- Project runtime metadata
- Güvenli multi-user isolation

Amaç:

AppForge Terminal yalnızca Termux alternatifi olmamalı.

AppForge Terminal:

Project-aware
+ Cloud-aware
+ AI-aware
+ Build-aware

bir geliştirme ortamına dönüşmeli.

---

## 7. Sentry — Production Center ve Release Health

Sentry, AppForge Production Center için referanstır.

Her AppForge sürümünde izlenecekler:

- Build success rate
- Build failure rate
- Crash rate
- Runtime error rate
- Terminal failure rate
- Worker health
- Queue latency
- Build duration P50 / P95 / P99
- AI response latency
- AI failure rate
- Memory usage
- CPU usage
- Version-to-version regression

Örnek:

5.0.21
vs
5.0.22

karşılaştırılmalı.

Production Center şu soruya cevap vermeli:

"Yeni sürüm önceki sürümden daha sağlıklı mı?
Değilse neden?"

---

# Öncelik sırası

1. VS Code tarzı modüler mimari
2. Expo EAS tarzı Builder / Release sistemi
3. Turborepo tarzı incremental cache
4. n8n tarzı Connector SDK
5. Coder tarzı persistent cloud workspace
6. Sentry tarzı Release Health

Renuvex mühendislik disiplini tüm aşamalarda temel kuraldır.

---

# SecondBrain karar kuralları

Yeni büyük özellik eklenmeden önce SecondBrain şunları kontrol etmeli:

1. Bu özellik hangi modüle ait?
2. Mevcut büyük bir dosyayı daha da büyütüyor mu?
3. Bağımsız test edilebilir mi?
4. Yeni dependency gerçekten gerekli mi?
5. Connector / capability sınırı gerekiyor mu?
6. Build aşaması cache edilebilir mi?
7. Performans etkisi nasıl ölçülecek?
8. Production'da sağlığı nasıl takip edilecek?
9. Mevcut çalışan özellikleri bozma riski var mı?
10. Aynı işi yapan mevcut bir altyapı var mı?

Öncelik:

Daha fazla özellik eklemek değil,
gelecekte özellik eklemeyi daha güvenli ve daha hızlı hale getiren mimari kurmak.
