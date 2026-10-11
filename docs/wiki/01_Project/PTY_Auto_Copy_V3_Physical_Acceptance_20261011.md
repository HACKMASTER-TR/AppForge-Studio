---
type: status
status: active
project: AppForge Studio
created: 2026-10-11
updated: 2026-10-11
last_verified: 2026-10-11
confidence: medium
tags:
  - terminal
  - physical-acceptance
source_files: []
---

# PTY Auto-Copy V3 — physical acceptance evidence

## Source and verification

- Current debug build source SHA: `0893806d86ca8154d634dced87b9d6a16dad2dba`.
- Android Debug CI [run 38133654003](https://github.com/HACKMASTER-TR/AppForge-Studio/actions/runs/38133654003): checkout and uploaded artifact both associated with this exact SHA.
- Device acceptance date: 2026-10-11. Observations were provided by the user in a ChatGPT session. A screenshot was reviewed for scrollback; clipboard verification was posted as text. The underlying screenshot/chat record is not independently hosted in this repository.
- Previous six physical Bash/PTY checks were reported on `47caec944fba2b64987eb02d8ca7e8579429d464`, a DIFFERENT source SHA. Do not transfer that evidence as a fresh retest of all six scenarios on the new SHA.

## Hardening checks on the new APK

| Scenario | Input | Observed outcome |
|---|---|---|
| Byte limit | `awk` 600 x 1,024-byte lines, ~600 KiB | User reported `OUTPUT_LIMIT`, earlier clipboard sentinel preserved and PTY responsive |
| Scrollback | `awk` emitted 20,100 numbered lines, below 512 KiB | Screenshot showed history-incomplete warning and returned Bash prompt; user posted the original `APPFORGE_V3_HISTORY_SENTINEL` from clipboard |

Both hardening scenarios were accepted from these device observations. They do not certify universal sensitive-data filtering, arbitrary interactive programs, or every PTY scenario.

## Limits / next action

- Targeted 15 Kotlin PASS and V1/V2 regressions were user-reported, but no independent execution log is anchored here; do not upgrade their state without evidence.
- Current B05-based evidence file is documentary only. The V3 application branch has not received B05, and no existing application installation or data was changed.
- Commit, push, new CI, PR, merge, release, deploy, Play Production and D1 migration are NOT RUN and NOT AUTHORIZED by this record.
