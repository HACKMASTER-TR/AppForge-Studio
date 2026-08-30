# VideoForge V3

DRM/paywall aşmadan, kullanıcının indirme/dönüştürme hakkı bulunan videolar için indirme ve otomatik Türkçe dublaj servisi.

## Kaynaklar

- URL: yt-dlp destekli genel/DRM'siz kaynaklar.
- Yerel dosya: MP4, MOV, WebM, MKV, M4V.

Yerel dosyalar 8 MB varsayılan parçalar halinde yüklenir. Tam dosya sunucuda birleştirilir, ffprobe ile video+ses akışı doğrulanır ve dublaj kuyruğuna alınır.

## Dublaj

- Konuşmacı ayrımı: `gpt-4o-transcribe-diarize`
- Türkçe çeviri: `DUB_TRANSLATE_MODEL`
- TTS: `gpt-4o-mini-tts`
- Konuşmacı başına tutarlı yapay ses profili
- İsteğe bağlı Türkçe altyazı
- FFmpeg ile orijinal ses ducking + Türkçe dublaj mix

## Upload env

- `DUB_MAX_UPLOAD_MB=256`
- `DUB_UPLOAD_CHUNK_MB=8`
- `DUB_MAX_PENDING_UPLOADS=2`

`OPENAI_API_KEY` yalnız sunucuda tutulmalıdır; APK içine gömülmemelidir.
