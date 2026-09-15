---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - build
  - workers
  - artifacts
related:
  - "[[Build_And_Worker_Architecture]]"
  - "[[Database_Schema_Coverage]]"
  - "[[Backend_API_Domains]]"
source_files:
  - "build-service/src/workerRuntime.js"
  - "build-service/src/jobQueue.js"
  - "build-service/src/workspaceBuild.js"
  - "build-service/src/downloadTickets.js"
  - "build-service/src/storage.js"
  - "build-service/src/buildEngine.js"
---

# Worker and Artifact Flow

Build work is represented in the backend queue and claimed by workers whose capabilities satisfy the job requirement. `jobQueue.js` contains worker registration, claim, heartbeat, retry/requeue, cancellation, and queue-statistics paths. The code distinguishes capability-constrained jobs, including source-build isolation handling.

`workerRuntime.js` coordinates worker execution. Workspace build, build-engine, storage, and download-ticket modules cover adjacent submission and artifact-delivery responsibilities. Download tickets mediate artifact access; do not replace their behavior with raw storage-path assumptions.

Queue positions, worker availability, artifacts, and remote worker health are runtime facts. This map documents code responsibilities only, not a claim that any worker is currently available.
