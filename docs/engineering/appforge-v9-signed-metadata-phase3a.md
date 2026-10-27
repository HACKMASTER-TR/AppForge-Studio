# AppForge V9 Phase 3A — Signed Ubuntu metadata and package-index closure

State: **LOCAL VERIFIER PREPARED** — no R2 upload, no Worker deployment, no Android client routing, no publication.

## Scope

`quality/tools/appforge_signed_apt_closure_v9.py` independently confirms:

1. The original immutable V9 package catalog and Ubuntu snapshot profile have their exact **repository-reviewed SHA-256** (`CATALOG_PIN`, `PROFILE_PIN`). Snapshot `20261018T000000Z` is not changed.
2. All four pinned Ubuntu `InRelease` objects exist locally and match the four pinned SHA-256 values in `apt-snapshot-profile.tsv`.
3. `gpgv` authenticates each clear-signed `InRelease` against a **pre-existing, reviewed Ubuntu archive keyring** and requires the exact Ubuntu 2018 signing fingerprint `F6ECB3762474EDA9D21B7022871920D1991BC93C`. The verifier does not fetch or accept a key from the downloaded metadata.
4. The authenticated plaintext has `Origin: Ubuntu`, `Codename: noble`, and the correct suite; signed date and any `Valid-Until` are checked.
5. For either chosen architecture (`arm64` or `amd64`), all four components (`main`, `restricted`, `universe`, `multiverse`) in each of the four suites require a corresponding **uncompressed `Packages`** record in the authenticated Release SHA256 table. These 16 index files must be present in the hash-addressed local store and match the signed size and SHA-256. Compressed index objects must be decompressed by a separately reviewed, bounded procedure before import. No decompression is performed by this verifier.
6. Every pinned catalog package for the selected architecture must match an exact `(Package, Version, Architecture, Filename, Size, SHA256)` identity occurring in those authenticated `Packages` indexes.
7. All object paths are content-addressed. Missing objects, unexpected signer, index checksum mismatch, unsigned data and symlinked local object paths block the verification.

CLI usage **after the exact signed metadata and indexes have been seeded from authorized sources**:

```sh
python3 quality/tools/appforge_signed_apt_closure_v9.py \
 --store "$HOME/appforge-v9-local-mirror" \
 --keyring /PATH/TO/PREINSTALLED/UBUNTU_ARCHIVE_KEYRING.gpg \
 --arch arm64
```

The keyring must be provisioned from a trusted Ubuntu distribution channel outside the untrusted downloaded R2 objects. CLI fingerprints cannot be overridden; test-only APIs use ephemeral test keys and are not invoked by the Android app.

Tests:

```sh
python3 -m unittest discover -s quality/tests -p test_v9_signed_closure.py -v
```

The tests generate ephemeral GPG signing keys, authenticate 4 signed `InRelease` fixtures and 16 index references via the real `gpgv` executable if `gpg` and `gpgv` are installed. Without these executables the GPG-dependent fixture tests explicitly **SKIP**; pure parser and fail-closed tests still run. The tests never use test signing keys for production. Real pinned Ubuntu metadata is unavailable in the current empty store.

## Blockers

- At present, `0/218` original pinned objects are seeded. The 16 metadata index objects *per architecture* are additional to the 218-object initial catalog. Full metadata and package closure has **not** passed for the real Ubuntu snapshot.
- The snapshot upstream returned HTTP 503; **do not switch to the moving live Ubuntu repository** or accept alternative signing keys or expiry bypass.
- This verifier does not create AppForge Ed25519-signed release manifests; signed distribution manifest, anti-rollback, safe R2 publisher, Android CDN client and on-device acceptance remain independent future steps.
- R2 upload, Cloudflare Worker public routing, Android app changes, GitHub main, Play Production and the installed APK remain **untouched**. `ALL_ENGINES_READY=false`.

## Limitations and hardening needed before production

This Phase 3A prototype requires the repository-generated uncompressed Packages index hashes to be available in each signed Release table. Production import must handle authorized compressed indexes (`.xz`/`.gz`) with bounded decompression and verification against the *uncompressed* signed identity, include special files and package index architecture nuances, record metadata provenance and licensing, and test against the actual Ubuntu release. Since the signed indexes and packages are still absent, **no release/publish gate is opened** even if a synthetic fixture passes.
