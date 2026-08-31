# AppForge Studio v2.0 — Quick + Advanced Create

The Android client now starts with a creation-mode chooser inspired by a compact two-card flow.

## Quick Create

User provides only:
- app name
- content
- optional PNG icon

Content can be:
- local HTML / ZIP
- HTTPS website URL

AppForge fills the rest with secure defaults:
- package name generated from app name
- version 1.0.0 / code 1
- APK + AAB output
- debug signing
- safe WebView defaults
- splash enabled
- offline cache enabled
- file upload/download enabled
- deep links off
- AdMob off
- Billing off
- Firebase off
- remote Native Bridge off

For local HTML, the secure Native Bridge remains available.
For remote URL mode, the bridge is disabled by default.

## Advanced Create

Advanced Create opens the full existing 9-step builder:
- source
- features
- appearance
- Native Bridge
- monetization
- deep links
- signing
- build service
- build output

## Navigation

Advanced builder has a back arrow to return to the create-mode chooser.
Quick Create also has a direct "Advanced" shortcut.
