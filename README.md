# AppForge Studio — device-local build architecture

As of 2026-09-23 the remote Build Service, Workers, queue and backend
monthly quota have been retired from the repository. Builds run on device;
Cloudflare Pro, Windows Portable Host and Google Play have separate roles.
The earlier V5 release notes below are historical, not a live backend contract.

# AppForge Studio V5 — AI Application Studio

V5 adds a working Quick/Advanced application scaffold flow to the shared Studio: responsive visual UI, live preview, CRUD data schema, Node.js backend, authentication, notifications and Android/Windows/Web publishing metadata are generated together. HTML and ZIP source selection now includes an `Otomatik sürüm arttır` control; when enabled, semantic `versionName` and Android `versionCode` advance together.

## AppForge Terminal (V5.1)

AppForge Studio includes a project-aware terminal workspace with multi-session shell access, a safe file editor, embedded Git, SSH with host-key verification, runtime inspection and direct Builder/AI navigation. GitHub remains the active external connection flow from the Connections tab; Railway authorization and its user-facing connection card are retired. Connection persistence remains handled through the app's secure account store. See [the terminal and developer tools wiki](docs/wiki/02_Codebase_Map/Terminal_And_Developer_Tools.md).

Normal Android project compilation is device-local through `DeviceBuildEngine` and the dedicated device build runtime. APK/AAB production uses the local Gradle toolchain and verified caches. The active normal build path has no Railway, Render, remote Worker or remote autoscale fallback.

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

No cloud LLM API key is required; inference stays on device and does not depend on the retired Build Service.

## Project-aware answers
The user can optionally give the local model a safe summary of the current project. Passwords and credential material are deliberately excluded.

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
- accountless normal use
- Pro Ömür Boyu
- server-verified Pro state
