---
type: codebase
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-23
last_verified: 2026-09-23
confidence: high
tags:
  - local-ai
  - unified-agent
related:
  - "[[Android_App_Map]]"
  - "[[Terminal_And_Developer_Tools]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeLocalAssistant.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAssistantIntegration.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeUnifiedAgentStudioScreen.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentStudioOrchestrator.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentAutonomousPipeline.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentPromptProductClassifier.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentArtifactClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentSessionStore.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/LocalAiModelStore.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/LocalAiModelDownloader.kt"
  - "android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentPromptToProductV1Test.kt"
  - "quality/tests/appforge_local_ai_prompt_to_product_contract.test.js"
---

# Local AI and Agent Map

The Android `ai/` package contains the local assistant, assistant integration, Unified Agent UI/orchestration, autonomous-pipeline contracts, session persistence, project generation helpers, and local-model storage/download components. These are application features distinct from the removed repository Second Brain implementation.

Read the requested feature's UI entry point, orchestration or contract class, persistence class, and associated Android test before changing agent behavior. Model availability, provider configuration, user content, and live inference results are not derivable from this source map and are not recorded as facts here.

The removal of legacy repository-brain routes does not remove the local assistant or Unified Agent surfaces. Their current behavior remains source- and test-verified per task.

## Prompt to Application / Game V1

The Unified Agent keeps local LiteRT-LM as the blueprint provider and does not
restore a remote project-build backend. The first prompt-to-product slice
classifies the AI-preserved prompt as an application or game after blueprint
validation. Normal application Web rendering keeps the screen/action model.
Game intent renders a real offline-capable HTML/CSS/JavaScript play surface
with touch/keyboard input, score persistence and an explicit racing mode for
race/car/motorcycle prompts.

`WEB` remains the shared source target for device-local APK/AAB packaging. If
the verified Windows Portable Host pack is already installed, the same Web
source requests APK+AAB+EXE; otherwise it stays APK+AAB instead of failing the
whole product generation flow. No generated project source is uploaded to a
remote AppForge Worker.

This V1 is a bounded deterministic game renderer, not arbitrary unrestricted
AI code execution. Broader prompt-driven application behavior and additional
game genres require separate source/test/device acceptance.
