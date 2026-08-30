# VideoForge Video + Turkish Dubbing Service

A secrets-isolated media export service for AppForge. It downloads only public/DRM-free media that the user is authorized to process.

## Existing endpoints

- `GET /health`
- `POST /api/info`
- `GET /api/download`

## Turkish dubbing endpoints

- `GET /api/dub/health`
- `POST /api/dub/jobs`
- `GET /api/dub/jobs/:id`
- `GET /api/dub/jobs/:id/download`

Dubbing is an asynchronous job so long videos do not keep a WebView request open.

## Required for dubbing

Set `OPENAI_API_KEY` only on the VideoForge backend service. Never put this key in the APK or `index.html`.

Optional environment variables:

- `DUB_TRANSCRIBE_MODEL=gpt-4o-transcribe-diarize`
- `DUB_TRANSLATE_MODEL=gpt-5.6-luna`
- `DUB_TTS_MODEL=gpt-4o-mini-tts`
- `DUB_MAX_JOBS=1`
- `DUB_MAX_DURATION_SECONDS=1800`
- `DUB_MAX_SEGMENTS=220`
- `DUB_ORIGINAL_VOLUME=0.55`
- `DUB_PROCESS_TIMEOUT_MS=2700000`

## Voice behavior

The service uses diarization to distinguish speakers and assigns each speaker a stable synthetic Turkish voice profile. It does not clone a real person's voice and does not infer a person's gender from their voice.

## Audio mix

The original soundtrack is retained and automatically ducked while Turkish synthesized speech is active. This is not full music/dialogue stem separation, so some original dialogue may remain faintly audible.
