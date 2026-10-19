# V3 module lifecycle implementation

Status: PARTIAL — host-verified safe transaction primitives and fail-closed UI;
full physical-device and all real toolchain lifecycle acceptance remains NOT_RUN.

Baseline: 39cbe7e13e4539d539b9cf0959b726e3ada69c5e.
Complete series base: 1e922b835b39f9f0d948e780ff4020f594124afd.
No GitHub write or device installation is authorized or performed.

## Implemented

Central action policy, revision-2 ownership inventory (hashes, module/version,
dependencies, generation, previous version), live usage invalidation, verified
private namespace removal, generation disk preflight, digest-sealed activation,
verified rollback/recovery, unknown-content cleanup refusal, explicit UI consent,
and application-scoped lifecycle operations preserve existing download telemetry.
Portable removal is journaled archival. It intentionally retains old bytes.

## Actual action matrix

| Module | Update | Remove | Rollback |
| --- | --- | --- | --- |
| FAST_HTML | Embedded, blocked | Blocked | Blocked |
| LINUX_RUNTIME | No verified new release: blocked | Only existing verified unused runtime archive path | No module rollback promise |
| ANDROID_CORE | Changed trusted packaged recipe, verified staging/probes required | Blocked for shared APT/system ownership | Latest matching unchanged verified generation only |
| NODE_WEB | Same verified generation requirements | Only exclusive reversible private paths/aliases; shared writes block | Same latest-generation requirement |
| PYTHON_ANDROID | Same verified generation requirements | Shared writes block; exclusive proof required | Same latest-generation requirement |
| EXPO_REACT_NATIVE | Same verified generation requirements | Shared writes block; exclusive proof required | Same latest-generation requirement |
| WINDOWS_NATIVE | Same verified generation requirements | Shared writes block; exclusive proof required | Same latest-generation requirement |
| WINDOWS_PORTABLE | No verified new binary: blocked | Exact pinned host, private allowlist, no dependents; archival | No supported version rollback |

SAFE_LIFECYCLE_SUPPORTED is action-specific and conditional, never a blanket
promise for a module. LIFECYCLE_BLOCKED includes absent proofs, dependents, active
use, unknown files, changed trees, insufficient space, legacy receipts, and
pending recovery. Native recipe revision was advanced to ownership-probe-v7-lifecycle.
Package versions, sources and checksums are otherwise unchanged; no hashes invented.

## Evidence scope and failures

Five native module IDs run parameterized real filesystem install/update/failure,
rollback/removal/ownership tests with synthetic tool payloads. These are not
actual Android SDK/Expo/APT installs. Separate JVM processes are forcibly halted
at before-switch, old-moved, new-moved and committed-marker boundaries and then
recovered. Portable recovery covers interruption, foreign user data, archive
preservation and symlink rejection. Existing downloader hash/HTTP tests remain.

Initial ownership tests failed because four synthetic vendor fixtures did not
capture their baseline; fixtures now explicitly register original vendor files.
An initial acceptance-test compile failed on public exposure of an internal enum;
the fixture is now internal. A cleanup test run failed until known metadata parent
paths were included in the ownership allowlist. No tests or security gates removed.
Final exact counts and logs are in the delivered verification.json.

## Limits and NOT_RUN

Shared APT/rootfs cannot be selectively uninstalled without proven exclusive
ownership. Such removal stays disabled. Whole-root rollback is disabled after
any subsequent active-tree change, including user/cache writes. Unknown snapshot
content and quarantine/Portable archives are retained; no automatic deletion or
space-reclamation guarantee is made. Successful native removal has no separate
undo UI; failed activation restores the verified old generation.

The app uses its existing single-process exclusive leases. External unrelated
writers are detected by tree validation, not controlled by a filesystem snapshot.
File journals are synced; host process-death tests do not establish power-loss or
Android filesystem guarantees. Actual disk exhaustion is NOT_RUN (injected disk
preflight failure is tested). Android 12–16 rotation, foreground service timeout,
notification denial, force-stop/reopen, physical Android16 Samsung S24 Ultra,
real per-module toolchain upgrades/removals and full offline rebuild after each
lifecycle action are NOT_RUN. CI is NOT_RUN_USER_PROHIBITED.

No new APK is accepted for a T21 upgrade. Any locally assembled debug artifact
requires certificate comparison and independent new CI/device acceptance first.

Physical checklist: retain backup of projects/keystore; verify certificate;
install approved build manually; test each permitted action with dependent modules,
active build and low space; interrupt at download/stage/activate; restart and
verify journal/READY; validate project hashes and other module offline builds;
check background restrictions on Android12–16. Never use host PASS as device PASS.
