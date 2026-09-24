---
type: registry
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-19
last_verified: 2026-09-19
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

| Device feature contracts | quality/tests/ | [[02_Codebase_Map/Test_And_CI_Map]]; [[05_Bugs_And_Fixes/Bug_Index]] | covered | A passing static test is not production or device acceptance. |


| Operations and automation | .github/workflows/; .appforge/; scripts/; README.md | [[03_Architecture/Deployment_And_CI]]; [[03_Architecture/Operations_And_Automation]]; [[01_Project/Current_Status]] | covered | Configuration evidence does not prove deployed or live state. |
| Device build runtime | android-app/app/src/main/java/com/appforge/studio/build/; android-app/app/src/main/assets/device-build/ | [[03_Architecture/Device_Build_Runtime_V3]]; [[03_Architecture/Build_And_Worker_Architecture]]; [[03_Architecture/System_Architecture]] | covered | Review engine capability, toolchain pinning and real-device acceptance together. |
<!-- COVERAGE_REGISTRY_END -->

## Maintenance Rule

When a meaningful source change falls under a covered row, review its listed wiki pages. When it falls outside the registry, add a row or mark the area deferred before writing durable knowledge. Do not convert a deferred or unverified row to covered without source review.


Retired backend routes, schema and Docker details are kept in historical wiki pages, not active coverage paths.
