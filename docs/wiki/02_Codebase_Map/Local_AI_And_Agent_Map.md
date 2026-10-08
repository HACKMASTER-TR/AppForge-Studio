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
  - "android-app/app/src/main/java/com/appforge/studio/AdminAiRouterScreen.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AdminAiAgentSession.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AdminAiAgentRuntime.kt"
  - "cloudflare/control-plane/src/admin_ai_agent_contract.mjs"
  - "scripts/test-admin-ai-agent.py"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeLocalAssistant.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAssistantIntegration.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeUnifiedAgentStudioScreen.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentStudioOrchestrator.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentAutonomousPipeline.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentPromptProductClassifier.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentBlueprintRecovery.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentArtifactClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentSessionStore.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/LocalAiModelStore.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/LocalAiModelDownloader.kt"
  - "android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentPromptToProductV1Test.kt"
  - "android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentBlueprintRecoveryTest.kt"
  - "quality/tests/appforge_local_ai_prompt_to_product_contract.test.js"
  - "quality/tests/appforge_unified_agent_local_artifact_save_contract.test.js"
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

## Bounded local Blueprint recovery

If LiteRT-LM returns an invalid structured Blueprint, the strict schema parser
remains authoritative. One compact local inference retry is allowed; model
execution errors and platform mismatches do not trigger a template fallback.
If both JSON responses fail validation, only an explicit WEB game prompt may
open a validated deterministic game Blueprint. The UI labels this as a ready
web-game template, not a model-designed game. Ordinary application prompts
remain blocked rather than silently receiving generic screens. The original
user prompt is retained for generation and classification. Device acceptance
is required to verify inference and gameplay; tests alone do not establish it.

## Unified Agent local artifact delivery

The Unified Agent's APK, AAB and Portable EXE actions must handle device-local
`file://` tickets by validating the canonical build-artifacts file and copying
it through MediaStore Downloads/AppForgeStudio on Android 10+. DownloadManager
is only for HTTPS. Save success must mean bytes were copied and published; do
not label local saves as queued downloads. Android 8/9 uses an explicitly
labeled app-specific directory. Keep exact build-ID artifact resolution and
never upload sources or artifacts to a retired remote build service.

## Agent output after AppForge restart

Unified Agent local artifact tickets resolve only a successful saved session's
exact `local-...` build ID and advertised APK/AAB/EXE kind after the process
lost `DeviceBuildEngine.jobs`. Canonical nonempty artifact path and build number
must match; missing or ambiguous files fail closed. UI hides detailed quality,
checkpoint, release and raw log output behind explicit technical disclosure,
not by destroying diagnostic evidence. Device acceptance remains open.

## Admin AI controlled coding session (2026-10-08)

The Admin AI screen now selects only account-scoped saved `ProjectLibrary`
projects and updates MainActivity's actual draft/project ID through the existing
project navigation/build guard. Context, model replies, approvals and activity
are scoped to the selected project. Leaving the screen cancels the transient
session. Saved projects without a source folder retain legacy read-only chat
and cannot acquire a scratch workspace for code edits. Reset clears task history and recollects bounded context; local undo
checkpoints survive task reset within the same screen/project.

The local `AdminAiAgentSession` owns typed requests, narrow operation approval,
terminal task states, bounded evidence, stale hash conflicts and checkpointed
single-file mutations. It reuses `AppForgeAgentStructuredPatch`,
`AppForgeAgentWorkspaceTransaction` and the two-attempt repair policy. The
existing blueprint autonomous pipeline and Unified Agent project-memory store
were inspected but not wired: they generate projects or require their own
canonical Unified Agent workspace roots. Saved-project editing must not
silently enter those flows. Source edits are limited to 16,000-character files
and 120 changed lines; secret-like content is rejected rather than edited from
redacted context. Undo checks current hashes before restoring a checkpoint. Transactions retain
recovery checkpoints after partial commits and guard restoration of committed
paths; recovery conflicts remain failures. Generic WorkspaceFileService was
not used as an AI authority because it exposes unfiltered paths, a larger
editor bound and complete directory enumeration. Its existing UI remains
unchanged; the agent reuses the canonical workspace resolver and context
secret policy with stricter bounded reads.

`AdminAiAgentRuntime` uses existing `LinuxShellEngine`, `BuildApiClient` and
`DeviceBuildEngine`; it does not introduce a shell or remote build server.
Terminal requests currently permit only `pwd`. Git inspection uses a bounded
index-to-worktree adapter in `GitWorkspaceService` without parent discovery,
environment settings, hooks or external diff. This is not a full staged HEAD
diff. Build approval uses local debug sources, existing runtime preflight and
exact build-ID artifact resolution, with artifact size/SHA-256 evidence.
Publisher signing and native Windows agent requests remain blocked; the
existing Studio/native/publisher build flows are unchanged.

The packaged Linux launcher binds `/proc` and `/dev`; it is not proven to
isolate untrusted project scripts. Consequently arbitrary terminal commands
and project-script lint/test/typecheck operations fail closed. This is an
implementation limit, not a successful verification result. Source mutation
cannot complete as verified without a real successful guarded build and
requested artifact evidence. Builds themselves still require explicit approval
because the existing runtime executes project build code. Cloudflare only
validates/routs structured planner responses and keeps the verified admin gate
and AUTO fallback. Legacy read-only chat remains available. Streaming is not
implemented because the current gateway returns bounded non-streaming JSON.

Structured patch normalization preserves literal escaped `\r\n` source
strings while normalizing actual CRLF text.

Host JVM behavioral checks compile actual session/patch/transaction/repair and
shell sources with documented Android launcher/service fixtures; they are not
Android unit, APK, CI or physical acceptance. The Android UI and actual device
runtime remain pending external exact-SHA CI and physical testing. See
`scripts/test-admin-ai-agent.py`, `quality/kotlin/AdminAiAgentBehavior.kt`, and
`cloudflare/control-plane/tests/admin_ai_agent.test.mjs` for reproducible checks.
