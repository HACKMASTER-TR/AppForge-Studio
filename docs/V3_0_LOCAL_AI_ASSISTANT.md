# AppForge Studio v3.0 — Local AI Assistant

AppForge now contains an on-device AI help assistant powered by LiteRT-LM.

## Runtime
- Android dependency: `com.google.ai.edge.litertlm:litertlm-android:0.11.0`
- user imports a compatible `.litertlm` model
- no cloud LLM API is required for inference
- the model is copied into private AppForge app storage

## Why the model is not inside the APK
On-device LLM files can be hundreds of MB or several GB. Keeping the model separate prevents the AppForge base APK from becoming extremely large and lets the user replace the model independently.

## Local grounding
The assistant includes a local AppForge knowledge base for:
- Free lifetime 5-project trial
- Pro / Pro Monthly
- watermark
- server-authoritative Pro
- Preview Inspector
- Test Lab
- backups
- signing
- versioning
- PWA
- Native Module Center
- Build Service
- Billing
- Local AI privacy

A lightweight local retriever injects relevant chunks into each question.

## Project-aware mode
Optional project context includes non-secret editor state such as:
- app name
- package
- source mode
- version
- output type
- signing mode
- Native Bridge flags
- camera/location/QR
- Billing/AdMob/Firebase flags

Build API keys and keystore passwords are not injected.

## CPU / GPU
CPU is the default backend.
GPU is shown as experimental and catchable GPU initialization failures fall back to CPU.

## Privacy
The Local AI feature does not send the user's question to AppForge Build Service or a cloud LLM provider for AI inference.

## Build toolchain
The Studio app requests a JDK 21 Kotlin toolchain while still targeting Java 17 bytecode, so current LiteRT-LM Maven classes can be consumed.
