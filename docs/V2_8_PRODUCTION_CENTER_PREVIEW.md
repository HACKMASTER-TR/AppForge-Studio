# AppForge Studio v2.8 — Production Center + Live Preview

## App Preview

The Android editor can now preview a project before building.

Modes:
- local HTML/ZIP
- HTTPS URL

Device presets:
- phone 360 × 800
- large phone 412 × 915
- tablet 800 × 1280
- portrait / landscape

Security:
- no AppForge Native Bridge is injected into the editor preview
- remote navigation is restricted to the configured HTTPS host
- local preview allows file access only for local projects

## Production Center

Includes:
- local dashboard
- active project count
- lifetime Free trial usage
- build history statistics
- AppForge Check
- version manager
- automatic versionCode increment
- project ZIP backup export/import
- template catalog shortcut

## AppForge Check

Current checks:
- app name
- package name
- source
- version
- app icon
- Play signing
- privacy/Data safety warning
- remote Native Bridge warning

## Project backup

Backup ZIP includes:
- AppForge project metadata
- local HTML project source
- build options and feature configuration

Deliberately excluded:
- keystore passwords
- API keys
- sensitive signing secrets

## Automatic versioning

When enabled:
- each build starts with `versionCode + 1`
- the updated draft remains in the editor

Manual controls:
- versionCode +1
- semantic patch +1
