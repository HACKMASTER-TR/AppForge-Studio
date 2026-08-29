# AppForge Video Downloader

AppForge ile APK'ya dönüştürülebilecek mobil video indirici ve ona ait ayrı backend servisi.

## Kapsam

- URL yapıştırma
- Video başlığı, süre ve küçük resim
- Mevcut çözünürlükleri listeleme
- MP4 indirme
- Ayrı video/ses akışlarını FFmpeg ile birleştirme
- yt-dlp tarafından desteklenen DRM'siz video sayfaları
- Playlist indirme kapalı
- Private/local IP engeli
- İndirme eşzamanlılık limiti
- İstek limiti
- Temp dosya temizliği

DRM, ödeme duvarı, üyelik veya erişim koruması aşılmaz. Yalnızca indirme hakkınız bulunan içeriklerde kullanılmalıdır.

## Çalıştırma

AppForge build-service klasöründe:

```bash
docker compose --profile video up -d --build video-downloader
```

Yerel test:

- Arayüz: `http://SUNUCU_IP:8081/`
- Health: `http://SUNUCU_IP:8081/health`

## APK

`public/index.html` dosyasını AppForge projesine ekleyin. APK ilk açıldığında Ayarlar bölümünden Video API adresini girin.

Üretimde HTTPS kullanın. Örnek:

```text
https://video.example.com
```

## Ortam değişkenleri

- `PORT=8081`
- `MAX_FILESIZE=750M`
- `MAX_DOWNLOADS=2`
- `PROCESS_TIMEOUT_MS=900000`
- `VIDEO_API_TOKEN=` (isteğe bağlı)
- `YTDLP_BIN=yt-dlp`

`VIDEO_API_TOKEN` tanımlanırsa AppForge uygulamasında Ayarlar > API anahtarı alanına aynı değeri yazın.
