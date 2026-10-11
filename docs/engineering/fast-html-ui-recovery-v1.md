# Fast HTML/URL APK + local template and Quick Create recovery (isolated)

Scope: existing feature worktree from commit `8ff582f384e75349b0d667d38296b6865b8835f7`. No push, main, release, APK installation or existing user-data mutation.

- Fix HTML fast-build contract test for the repo's native Node.js ES module configuration.
- Preserve the HTML/HTTPS URL APK-only no-download path from the previous isolated patch. Do not remove SDK/JDK/Gradle used by other engines.
- Quick Create: auto-generate package name from app name only while package name remains derived/default; honor explicit manual override.
- Restore 18 existing local templates by cataloging **only** the actual slugs implemented in `TemplateProjectFactory`. The user's session must not gate these on-device sources; remote templates stay optional and never hide local templates when unavailable.
- Remove unnecessary 'Hesapsız' user-facing text; authentication/authorization policy remains unchanged.
- Excludes intentional removals: Excel Araçları, VideoForge, OtherApps. Cloud builds remain postponed.

Acceptance: Node.js target tests, full quality suite, Kotlin/Android compile, real-device no-download HTML + HTTPS URL APK creation/signature/install/launch, and manual screen checks. Node contract PASS is **not** proof of Android physical runtime acceptance.

HTML/CSS/JavaScript extension: `.html` entry plus relative `.css`, `.js`, `.mjs` and allowlisted static images/fonts are copied unchanged into the bundled APK under `assets/site/`. Android WebView runs JS using its system runtime, without introducing Node/npm or changing React/Vite build routing. File:// cross-origin permissions remain disabled and the HTML APK shell does not expose native JS bridges. Caps: 250 eligible files, 24 MiB project assets, 5 MiB HTML entry. HTTPS URL continues to load online at application runtime; APK generation needs no internet. This is not a compilation pipeline for CSS preprocessors, TypeScript, JSX, npm, or frameworks. Real-device APK acceptance pending.

Template restoration uses a distinct folder for each created template project; preexisting template projects are not wiped.

## Physical acceptance: local template package identity (2026-10-11)

On-device acceptance confirmed local templates appear and materialize, Quick
Create works, and account labels are corrected. The local template flow still
inherited `ProjectDraft.packageName` except when blank or `com.example.myapp`,
which misses the actual default `com.appforgestudio.myapp` and also carries
a previous project's name. The isolated repair makes each newly materialized
local template adopt `template.name`, derives a package name with the established
Quick Create normalizer, and appends a numeric suffix when a saved project's
package is already taken. This applies only to `generated != null` (local
template creation); remote configuration templates and manual Quick Create
overrides are unaffected. Physical regression acceptance remains required.
