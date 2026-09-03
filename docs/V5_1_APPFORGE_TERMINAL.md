# AppForge Studio V5.1 — AppForge Terminal

AppForge Terminal, ayrı bir uygulama değil; Studio içindeki projeye bağlı komut merkezidir. Ana ekrandan açılır ve seçilen AppForge projesinin Builder ile AI Asistanına geri bağlanır.

## Özellikler

| Alan | Sağlanan yetenek |
| --- | --- |
| Terminal | En fazla 5 oturum, komut geçmişi, çalışma dizini takibi, iptal ve çıktı sınırı |
| Dosyalar | Proje ağacı, metin düzenleyici, yeni dosya/klasör ve geri alınabilir `.appforge-trash` silme |
| Git | Telefonda ayrıca Git kurulumu istemeyen JGit motoru; init, status, stage, commit, log, remote, clone, pull, push |
| SSH | JSch ile parola veya özel anahtar, bilinen sunucu kaydı ve ilk bağlantıda SHA-256 parmak izi onayı |
| Bağlantılar | GitHub Device Flow, Railway native OAuth + PKCE ve doğrulanan kişisel token yedeği |
| Araçlar | Shell/Git/SSH/Python/Node/npm/Java algılama ve AppForge proje kısayolları |

## GitHub ve Railway OAuth kurulumu

APK içine `client_secret` eklenmez. Uygulamaların genel istemci kimlikleri aşağıdaki Gradle özellikleri veya ortam değişkenleriyle verilir:

```text
APPFORGE_GITHUB_OAUTH_CLIENT_ID
APPFORGE_RAILWAY_OAUTH_CLIENT_ID
```

GitHub OAuth App içinde Device Flow etkinleştirilmelidir. Railway tarafında uygulama türü **Native (Public)** seçilmeli ve dönüş adresi tam olarak `appforge-studio://auth/railway` kaydedilmelidir. Railway bağlantısı Authorization Code + PKCE kullanır; native istemci token isteğinde client secret göndermez. `offline_access` için yetkilendirme isteği `prompt=consent` içerir. GitHub Actions iş akışları aynı adlardaki repository secret değerlerini Android derlemesine aktarır.

Resmî başvurular: [GitHub OAuth yetkilendirmesi](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps), [Railway OAuth](https://docs.railway.com/integrations/oauth) ve [Railway Public API](https://docs.railway.com/integrations/api).

Kimliklerden biri tanımlı değilse Bağlantılar ekranı kapanmaz; kullanıcı GitHub veya Railway üzerinde oluşturduğu kişisel erişim tokenını girebilir. Token sağlayıcının kimlik API'sinde doğrulandıktan sonra kaydedilir.

## Güvenlik modeli

- GitHub/Railway erişim ve yenileme tokenları `SecureAccountStore` içinde Android Keystore AES-256-GCM ile şifrelenir.
- OAuth parolası AppForge'a girilmez; kullanıcı sağlayıcının kendi HTTPS sayfasında onay verir.
- Railway yetkilendirmesi PKCE S256, rastgele `state`, tam dönüş adresi doğrulaması ve 10 dakikalık tek kullanımlık istek kaydıyla korunur. Bekleyen PKCE doğrulayıcısı da Keystore ile şifrelenir.
- Git kimlik bilgisi uzak depo URL'sine yazılmaz ve terminal çıktısında gösterilmez.
- Kullanıcı bilgisi, sorgu veya parça içeren Git uzak adresleri reddedilir; seçilen klasörün üst dizinindeki başka bir `.git` deposu yönetilmez.
- Kasadaki GitHub tokenı yalnız `github.com` uzak depolarına gönderilir; farklı Git sunucuları için kullanıcı tarafından açıkça girilen geçici kimlik bilgisi gerekir.
- SSH parola, özel anahtar ve anahtar parolası kalıcı olarak saklanmaz; yalnız ekranın belleğinde yaşar.
- İlk SSH parmak izi yoklaması kimlik bilgisi göndermez; parola veya özel anahtar yalnızca kullanıcı sunucu anahtarını onayladıktan sonra kullanılır.
- SSH komutu ilk kez çalışmadan önce sunucu anahtarının SHA-256 parmak izi kullanıcıya gösterilir. Onaylanan anahtar `known_hosts` ile sabitlenir.
- Yönetici/cihaz komutları engellenir; geri dönüşü zor dosya ve Git komutları ek onay ister.
- Build Service üzerinde genel amaçlı shell/terminal HTTP rotası açılmaz. Yerel terminal Android uygulamasının kendi işlem ve izin sınırlarında çalışır.
- Dosya ekranı yalnız seçilen proje kökü içindeki yolları kabul eder; arayüzden silinenler önce `.appforge-trash` içine taşınır.

## Çalışma zamanı sınırı

Android sistem kabuğunda bulunan komutlar doğrudan çalışır. Python veya Node.js cihaz kabuğunda kuruluysa otomatik algılanır. Kurulu değilse tam Linux araç zinciri, paket yöneticisi ve Docker gibi sunucu araçları SSH sekmesindeki kullanıcıya ait Linux ortamında kullanılabilir. JGit ve JSch uygulamanın içine gömülüdür; Git ve SSH için harici Termux kurulumu gerekmez.

## Sürüm

- `versionName`: `5.1.0`
- `versionCode`: `516`
