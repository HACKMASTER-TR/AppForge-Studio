# Project Library

v0.8 introduces a local project-library layer in AppForge Studio.

Current implementation:
- Saves project summaries as JSON under the app's private storage.
- Stores app name, package name, source mode, version, build output, and main feature flags.
- Does not persist keystore passwords.
- Can be expanded into a full Room-based project editor in the next version.
