# AppForge Studio V5 — AI Application Studio

V5 adds a working Quick/Advanced application scaffold flow to the shared Studio: responsive visual UI, live preview, CRUD data schema, Node.js backend, authentication, notifications and Android/Windows/Web publishing metadata are generated together. HTML and ZIP source selection now includes an `Otomatik sürüm arttır` control; when enabled, semantic `versionName` and Android `versionCode` advance together.

Android Gradle builds use the balanced V5 performance profile by default: APK+AAB tasks share one warm Gradle invocation, two workers run in parallel, Kotlin incremental compilation stays enabled and the persistent Gradle build cache is reused. If the worker reaches its memory limit, the same build automatically falls back to isolated low-memory tasks without discarding completed outputs. Set `GRADLE_PERFORMANCE_PROFILE=low-memory` only for constrained workers.

## ✨ Yerel AI Asistan
AppForge now has an on-device AI assistant for questions about:
- the current AppForge project
- Android build settings
- APK/AAB
- Preview / Test Lab
- signing
- project limits
- Pro plans
- Native Bridge
- PWA
- versioning
- Play preparation

## Local inference
The assistant uses Google AI Edge LiteRT-LM with a user-imported `.litertlm` model.

No cloud LLM API key is required and AI prompts are not sent to AppForge Build Service for inference.

## Project-aware answers
The user can optionally give the local model a safe summary of the current project. Passwords and Build API keys are deliberately excluded.

## Model Manager
- `.litertlm` import
- private app storage
- SHA-256
- CPU default
- experimental GPU
- unload/delete
- catchable GPU → CPU fallback

## Built-in AppForge knowledge
A local retrieval layer supplies the model with relevant AppForge product documentation before each question.

## Existing features remain
- Preview Console / Network / Performance / Security
- Test Lab
- APK/AAB analyzer
- Build Compare
- Release Notes
- PWA Inspector
- Native Module Center
- Production Center
- project ZIP backup
- auto versionCode
- lifetime Free quota
- Pro / Pro Monthly
- server-authoritative watermark and Pro
