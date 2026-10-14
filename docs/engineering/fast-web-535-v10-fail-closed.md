# Fast Web 535 V10 — internal verification scaffolding

## Outcome

AAB runtime production remains disabled. No new producer, signer, dependency,
UI option, success state or artifact discovery route was introduced. The existing
fast policy still rejects AAB/BOTH for local HTML and URL candidates before the
offline runtime. New projects retain APK as their default. Existing APK packaging,
AndroidKeyStore alias, signature behavior and installed applications are unchanged.

The wrapper supplied branch/base identity; this agent did not inspect Git metadata.
No commit, push, CI, installation or release was attempted.

## Discovery and scope

FastHtmlApkPolicy selects webview-static URL or HTML/web-static/webview local
projects; other engines keep their existing routes. Unsupported output, custom
signing, custom icon, permissions and SDK settings fail policy validation.
OfflineBuildPackManager returns fast-html-apk configuration errors; MainActivity
keeps those errors in Builder. DeviceBuildEngine sets only state.apk on this path
and returns before DeviceBuildRuntimeV3. Generic capability AAB declarations refer
to existing toolchain routes and are not evidence of fast AAB readiness.

The APK reader allows 250 files/24 MiB, a 5 MiB HTML entry, allowed static
extensions, canonical containment and symlink checks. No replacement reader was
introduced. The shell retains its compiled full Activity class name, no JavaScript
bridge, no cleartext traffic and no mixed-content relaxation. The generated AAB
asset is a shell, not a runtime producer.

## Added internal prerequisite

FastHtmlAabVerification consumes every JAR entry to EOF with verification enabled,
checks expected certificate SHA-256 on every payload entry, rejects unsigned
payloads and unsupported multiple-signature/certificate-chain identities, enforces
canonical ZIP names, duplicates, entry count and compressed/expanded size bounds,
and requires the minimum shell/config entry set. Only strict JAR bookkeeping names
may be unsigned. It does not extract files or return an artifact. It is not wired
into DeviceBuildEngine and cannot authorize output success.

This scaffold is not a complete bundle validator: ZIP external attributes/symlinks,
manifest protobuf semantics, resource/Dex identity and bundletool validation remain
separate prerequisites. No Play readiness or upload-key authority follows from a
matching local certificate. The existing persistent APK key is a local identity,
not evidence of a Play-registered upload certificate; no key was accessed here.

## Remaining blockers

- Bounded, schema-aware manifest protobuf editing and round-trip validation against
  the actual generated template, preserving Activity FQCN and resources/Dex.
- Shared strict local static-source eligibility/content validation, including
  rejecting build-required source projects mislabeled as static HTML.
- Audited Android-compatible JAR/CMS writer supporting a non-exportable approved
  AndroidKeyStore identity; no apksigner-for-AAB shortcut or throwaway key.
- Full safe AAB repackaging, signed-entry/tamper/mismatched-cert tests on Android,
  bundletool validation and generated APK identity/content/signer verification.
- Explicit signer UX distinguishing local test identity from registered upload key.

Until those gates pass, APK remains the only fast output. No unsigned or bogus
AAB can be reported by the new scaffold.

## Validation

Tool discovery: java and javac available; node, python3, kotlinc and gradle absent
from PATH. No dependencies installed, no toolchain downloads attempted.
Focused Kotlin tests were added for path rejection, unsigned signer rejection,
APK default, foreign-engine exclusion, AAB/BOTH rejection and HTTPS URL behavior.
They were not executed: an Android/Kotlin test environment is unavailable.
Existing quality tests were inspected but not run because Node is unavailable.

ANDROID_COMPILE=NOT_RUN
HOSTED_CI=NOT_RUN
PHYSICAL=NOT_RUN
AAB_SOURCE_STATE=PARTIAL_FAIL_CLOSED
AAB_ROUTING_USER_VISIBLE=NO
