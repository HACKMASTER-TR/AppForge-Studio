---
type: registry
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - coverage
  - source-verification
related:
  - "[[Index]]"
  - "[[Open_Questions]]"
source_files: []
---

# Coverage Registry

This is a routing and completeness register, not proof that every listed source has the same risk or maturity. `covered` means a source-verified map exists; `deferred` and `unverified` require explicit review before a claim is added to the wiki.

<!-- COVERAGE_REGISTRY_START -->
| Surface | Source paths | Wiki pages | Status | Follow-up |
|---|---|---|---|---|
| Android application | android-app/app/src/main/java/com/appforge/studio/; android-app/app/src/main/AndroidManifest.xml; android-app/app/build.gradle.kts | [[02_Codebase_Map/Android_App_Map]]; [[02_Codebase_Map/Terminal_And_Developer_Tools]]; [[02_Codebase_Map/Local_AI_And_Agent_Map]]; [[02_Codebase_Map/Account_And_Security_Map]] | covered | Inspect affected package and Android tests before behavioral claims. |
| Android tests and terminal emulator | android-app/app/src/test/; android-app/termux-terminal-emulator/ | [[02_Codebase_Map/Test_And_CI_Map]]; [[02_Codebase_Map/Terminal_And_Developer_Tools]]; [[01_Project/Open_Questions]] | deferred | The emulator is vendored infrastructure; inspect it only when terminal rendering behavior is in scope. |
| Backend API and modules | build-service/server.js; build-service/bootstrap.js; build-service/src/; build-service/package.json | [[02_Codebase_Map/Build_Service_Map]]; [[02_Codebase_Map/Backend_API_Domains]]; [[03_Architecture/Build_And_Worker_Architecture]]; [[03_Architecture/Security_And_Entitlements]] | covered | Read route, imported module, schema and contract test together. |
| Backend tests | build-service/tests/ | [[02_Codebase_Map/Test_And_CI_Map]]; [[05_Bugs_And_Fixes/Bug_Index]] | covered | A passing static test is not production or device acceptance. |
| Database migrations | build-service/sql/; build-service/src/db.js | [[02_Codebase_Map/Database_Map]]; [[02_Codebase_Map/Database_Schema_Coverage]] | covered | Migrations describe schema history, not authenticated live data or live schema state. |
| Build workers and artifacts | build-service/src/workerRuntime.js; build-service/src/jobQueue.js; build-service/src/downloadTickets.js; build-service/src/workspaceBuild.js | [[03_Architecture/Build_And_Worker_Architecture]]; [[03_Architecture/Worker_And_Artifact_Flow]] | covered | Verify worker capability and retry semantics against the active code path. |
| Operations and automation | build-service/docker-compose.yml; build-service/Dockerfile; .github/workflows/; .appforge/; scripts/; README.md | [[03_Architecture/Deployment_And_CI]]; [[03_Architecture/Operations_And_Automation]]; [[01_Project/Current_Status]] | covered | Configuration evidence does not prove deployed or live state. |
| Examples and historical backup files | examples/; android-app/app/src/main/java/com/appforge/studio/MainActivity.kt.fcm-dual-v2.bak; build-service/src/buildEngine.js.fcm-dual-v2.bak | [[01_Project/Open_Questions]] | unverified | Exclude from architecture claims unless a task explicitly needs provenance or cleanup. |
<!-- COVERAGE_REGISTRY_END -->

## Maintenance Rule

When a meaningful source change falls under a covered row, review its listed wiki pages. When it falls outside the registry, add a row or mark the area deferred before writing durable knowledge. Do not convert a deferred or unverified row to covered without source review.
