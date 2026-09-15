---
type: codebase
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
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
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentSessionStore.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/LocalAiModelStore.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/LocalAiModelDownloader.kt"
---

# Local AI and Agent Map

The Android `ai/` package contains the local assistant, assistant integration, Unified Agent UI/orchestration, autonomous-pipeline contracts, session persistence, project generation helpers, and local-model storage/download components. These are application features distinct from the removed repository Second Brain implementation.

Read the requested feature's UI entry point, orchestration or contract class, persistence class, and associated Android test before changing agent behavior. Model availability, provider configuration, user content, and live inference results are not derivable from this source map and are not recorded as facts here.

The removal of legacy repository-brain routes does not remove the local assistant or Unified Agent surfaces. Their current behavior remains source- and test-verified per task.
