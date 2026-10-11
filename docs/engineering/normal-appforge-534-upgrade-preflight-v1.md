# Normal AppForge Studio 533 -> 534 upgrade preparation

Status: local feature branch only; no Android installation, GitHub push,
Play release, GitHub Release, Cloudflare deployment, or main merge.

## Source
- Baseline main commit: `070cc947562cc30aa9030d7ab2efbb533322d5fb`.
- App package remains `com.appforge.studio`. No `.prodevice2` package.
- Base `versionCode` changes from 529 to **534**; base `versionName`
  changes from 5.0.29 to **5.0.34**. The base number change would affect
  eventual release builds if merged, so no release build or Play operation
  is authorized in this step.
- Other special-version variants and the CI debug signing mechanism stay
  unchanged.

## Grounded physical evidence
ADB read-only inspection of the actually installed app confirmed package
`com.appforge.studio`, versionCode 533 and valid APK v2 signature.
Installed v2 signer SHA-256 equals the main CI debug signer:
`f04693e62080489f41554ff357bab0789bf87b864aa8c5a9927279667de9d91b`.
This is prerequisite evidence, not proof of actual update acceptance.

## Remaining approval gates
1. Local quality/stability tests, then CI Android debug build from this
   feature branch; separately confirm compiled APK identity `com.appforge.studio`
   and versionCode 534 and same signer SHA-256 as installed 533.
2. Explicit approval for an in-place upgrade if requested; *never* uninstall,
   clear data, or overwrite user projects. Take backup where feasible.
3. Independently test persisted data and project usability after any install.
4. Main merge and release policy are separate actions requiring review.

## Play Integrity contract alignment

The pre-existing Google Play Integrity physical-acceptance test pinned
`appforgeBaseVersionCode` to 529. Update this static expectation to 534
only because the source-of-truth base version changes to 534. The rest of the
test continues to enforce the single stable base-version resolver, and the
separate Play acceptance version override remains intact. No Play publishing,
release signing, or production roll-out is part of this preparation.
