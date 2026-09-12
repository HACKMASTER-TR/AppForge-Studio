# AppForge Studio — HARD STABILITY POLICY V1

Status: MANDATORY
Scope: ALL APPFORGE

## Ana kural

Çalışan bir özellik, başka bir özelliği düzeltmek veya geliştirmek için bozulamaz.

Bir değişiklik herhangi bir korunan özelliği geriye götürürse:
STOP -> FAIL -> DÜZELT -> TEKRAR TEST

Yeni özellik eklenmesi mevcut davranışı bozmayı haklı çıkarmaz.

## Geliştirme akışı

1. main yalnız doğrulanmış stabil sürümdür.
2. Anlamlı değişiklikler ayrı branch üzerinde yapılır.
3. Değişiklikten önce mevcut davranış baseline kabul edilir.
4. Impact + risk + test kapsamı belirlenir.
5. Yalnız gerekli dosyalar değiştirilir.
6. Mevcut çalışan özellikler regresyon testinden geçer.
7. Yeni bug fix için mümkünse regresyon testi eklenir.
8. Testi silmek, gevşetmek veya skip ederek PASS üretmek yasaktır.
9. Compile SUCCESS tek başına çalışma kanıtı değildir.
10. Gerçek cihaz/runtime gerektiren özellik gerçek ortamda doğrulanır.
11. APK/AAB/EXE exact commit SHA ile eşleştirilir.
12. Her zorunlu gate PASS olmadan release aşamasına geçilmez.

## Korunan alanlar

- Terminal / PTY / IME / keyboard / paste / shortcuts
- Files / Downloads / AppForge Files / workspace
- Git / GitHub
- Connections / OAuth / Railway
- SSH
- Tools / toolchains / runtimes
- Builder / Gradle / APK / AAB / EXE
- AI / Second Brain
- Authentication / accounts / owner isolation
- Free / Pro / entitlement
- Billing / quota / Play products
- Build Service / API
- Database / migrations
- Worker / queues
- Railway / Render
- Web / PWA
- Desktop / Windows
- Excel Tools
- VideoForge
- Notifications
- CI / release / deployment
- Security
- Performance

## Yasak stage dosyaları

- *.apk
- *.aab
- *.save
- *.bak
- dashboard.sh
- dashboard.sh.bak

## Kaynak doğruluğu

Source code, configuration, migrations, tests, CI and runtime behavior
Second Brain hafızasından daha yetkilidir.

Second Brain bilinmeyen canlı durumu uyduramaz.

## Yetkilendirme

Commit / push / merge / deploy / publish yalnız kullanıcının açık talebiyle yapılır.
