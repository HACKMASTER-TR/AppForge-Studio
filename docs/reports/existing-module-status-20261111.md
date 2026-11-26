# Recognizing existing offline modules — 2026-11-11

The offline module screen now recognizes previously verified installations from
local files and receipts without requiring an operation journal entry. It shows
an overview of every module and marks verified modules `HAZIR ✓` in the overview,
selector and selected module status. An inactive historical download/error event
cannot override a currently verified installation.

Read-only inspection runs on entry, activity resume and manual refresh. Shared
dependencies are verified once per refresh through a memoized dependency walk.
Readiness is published before slower diagnostic/history queries. Lifecycle work
in progress, missing dependencies, corrupt inspection and cycles fail closed.

The same existing template/host, version/recipe, witness SHA-256, namespace
ownership, APT lock and runtime checks still decide readiness; build preflight
is not bypassed. Local older files with missing/incompatible proof are shown as
present and needing verification rather than absent. File presence and download
completion alone do not grant READY. Inspection performs no installation,
download, metadata blessing or user-data removal.

Validation commands:

```sh
gradle -p android-app :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=2
python3 scripts/generate-second-brain-snapshot.py --check
node scripts/wiki-audit.mjs
python3 scripts/wiki-secret-scan.py
apksigner verify --verbose --print-certs AppForgeStudio-existing-modules.apk
adb devices -l
```

The five new unit cases cover recognition without an operation journal,
readiness after repeated refresh/removal/repair, shared-dependency verification
once, pending/invalid dependency rejection, independent module errors and cycles.
Final Gradle result: PASS (56 seconds); 690 tests, zero failures/errors/skips.
APK v2 verification and certificate agreement with the previously delivered
debug APK passed. Actual command output and APK signature evidence accompany
the delivery. Wiki audit retains its existing advisory warnings.

No device is attached: real persisted installations on the user's phone and
activity-return UI behavior are not claimed as physically tested. In particular,
the screenshot's historical READY labels cannot independently prove that its six
installations meet current integrity requirements. Existing files and project
state are preserved; do not uninstall the current application or clear its data
to overcome a signing mismatch. The generated APK is a local debug build.

Hosted publication remains blocked by the previously observed GitHub 403;
no PR, hosted CI, production deployment or main change is claimed for this fix.
