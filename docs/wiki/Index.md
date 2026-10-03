---
type: context
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-02
last_verified: 2026-10-02
confidence: high
tags:
  - index
  - project-memory
related:
  - "[[Hot_Context]]"
source_files: []
---

# AppForge Studio Wiki Index

## Start Here

- [[Hot_Context]]
- [[Standing_Delivery_Authorization]]
- [[Current_Status]]
- [[Project_Overview]]
- [[Open_Questions]]

## Task Routing

| Task Type | Read First | Then Read | Then Inspect |
|---|---|---|---|
| Android UI or local feature | [[Android_App_Map]] | [[Feature_Overview]], [[Security_And_Entitlements]] | Android Kotlin and tests |
| Build, artifact, or engine | [[Build_And_Worker_Architecture]] | [[Worker_And_Artifact_Flow]], [[Device_Build_Runtime_V3]] | DeviceBuildEngine, BuildApiClient and build contracts |
| Windows artifact identity or download | [[Windows_Output_Artifact_Flow_V2]] | [[Device_Build_Runtime_V3]], [[Windows_Publisher_Authorization]] | BuildArtifactModel, BuildApiClient, DownloadedApkFolder and Windows artifact contracts |
| Auth, billing, or account | [[Security_And_Entitlements]] | [[Integration_Index]], [[Database_Map]] | Auth, Play, and client hardening code |
| Windows publisher signing | [[Windows_Publisher_Authorization]] | [[Security_And_Entitlements]], [[Deployment_And_CI]] | Publisher authorization client/provider, control plane and contracts |
| Control plane or deployment | [[Deployment_And_CI]] | [[Backend_API_Domains]], [[Current_Status]] | Cloudflare control plane and workflow files |
| Terminal or developer tools | [[Terminal_And_Developer_Tools]] | [[Android_App_Map]], [[Account_And_Security_Map]] | terminal Kotlin modules and contracts |
| Bug or test failure | [[Bug_Index]] | [[Current_Status]], [[Open_Questions]] | named test and its source files |
| Architecture change | [[System_Architecture]] | [[Decision_Index]], affected maps | related source, config, migrations, and tests |
| Wiki maintenance | [[Wiki_Maintenance_Prompt]] | [[Log]], [[Agent_Rules]] | wiki files and audit scripts |

## Core Pages

- [[Windows_Publisher_Authorization]]
- [[Pro_Code_Lifecycle_Staging]]
- [[Project_Overview]]
- [[Current_Status]]
- [[Feature_Overview]]
- [[System_Architecture]]
- [[Backend_API_Domains]]
- [[Build_Service_Map]] — historical / retired
- [[Android_App_Map]]
- [[Terminal_And_Developer_Tools]]
- [[Local_AI_And_Agent_Map]]
- [[Database_Map]]
- [[Integration_Index]]
- [[Bug_Index]]
- [[Decision_Index]]
- [[Device_Build_Runtime_V3]]
- [[Windows_Output_Artifact_Flow_V2]]
- [[Release_Integration_V1]]
