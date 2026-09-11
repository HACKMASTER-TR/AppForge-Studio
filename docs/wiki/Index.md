---
type: maintenance
status: active
project: AppForge Studio
created: 2026-09-11
updated: 2026-09-11
last_verified: 2026-09-11
confidence: high
tags:
  - appforge
  - second-brain
related:
  - "[[Index]]"
source_files:
  - "README.md"
---

# AppForge Studio — Second Brain Index

This vault is the durable project memory for AppForge Studio. Source code remains authoritative.

## Task Routing

| Task Type | Read First | Then Inspect Source |
|---|---|---|
| Android UI / Studio | [[Hot_Context]], [[Frontend_Map]] | `android-app/app/src/main/java/com/appforge/studio/MainActivity.kt` |
| Terminal | [[Hot_Context]], [[System_Architecture]], [[Recurring_Problems]] | terminal package under Android source |
| Build / APK / AAB | [[System_Architecture]], [[Deployment_Notes]], [[Caching_And_Performance]] | `build-service/`, Android Gradle files, workflows |
| GitHub / Railway / external connections | [[Integration_Index]], [[External_Services]] | build service env/config and connection code |
| Release / Play Store | [[Current_Status]], [[Deployment_Notes]] | Android Play workflow and Gradle config |
| Performance / scale | [[Caching_And_Performance]], [[Current_Status]] | worker Dockerfiles, autoscale workflow, terminal runtime |
| Security / credentials | [[Security_And_Privacy]], [[External_Services]] | env examples, auth/session/connection source |
| Bug investigation | [[Bug_Index]], [[Recurring_Problems]] | relevant tests and source files |
| Product roadmap | [[Roadmap]], [[Feature_Overview]] | README and active implementation |
| Wiki maintenance | [[Wiki_Maintenance_Prompt]] | `scripts/wiki-*` |

## Core Memory

- [[Hot_Context]]
- [[Log]]
- [[Project_Overview]]
- [[Current_Status]]
- [[Roadmap]]
- [[Open_Questions]]
- [[Important_Files]]
- [[System_Architecture]]
- [[Decision_Index]]
- [[Bug_Index]]
- [[Feature_Overview]]
- [[Integration_Index]]

## Memory Rule

Read only what the task needs. Verify implementation facts against the referenced source files before editing code.
