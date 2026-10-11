---
type: codebase
status: draft
project: AppForge Studio
created: 2026-10-11
updated: 2026-10-11
confidence: low
source_files:
  - android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt
  - android-app/app/src/main/java/com/appforge/studio/terminal/TerminalPtyCopyCapture.kt
  - android-app/app/src/main/java/com/appforge/studio/terminal/TerminalSafeAutoCopy.kt
  - quality/tests/terminal_pty_autocopy_v3_test.py
---
# PTY Auto-Copy V3 — local experiment

V3 captures visible output from completed foreground commands in the interactive Bash
session using session-specific OSC begin/end protocol markers. The parser requires a
real start marker, matching end marker, valid exit status, bounded UTF-8 output, and
no cancellation; it can drop output instead of silently truncating it. Markers can be
forged by a sufficiently privileged process running inside the same shell. They are
not authentication and are never used to grant clipboard privileges.

The existing verified Google administrator session is freshly checked against the
HTTPS control plane before every clipboard write. A backgrounded app, switched PTY,
revoked admin, stale command, canceled operation, and incomplete capture must not
write clipboard data. Clipboard is visible to the platform and user-installed apps;
redaction is best effort, not guaranteed. This first release does not promise reliable
completion detection for background jobs, nested shells, non-Bash, remote SSH, tmux,
full-screen interactive programs, or malicious commands that forge shell markers.

Local host Kotlin tests and stability checks are not physical acceptance. Test real
Android Bash PTY on device prior to any merge or release. DO NOT weaken existing
PTY viewport, session, resource cleanup or security guarantees.
