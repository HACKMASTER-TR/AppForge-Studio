# AppForge V9 Global Package Distribution — Phase 1 (offline prototype)

**State: PREPARED_ONLY / NO LIVE DEPLOY.** No Android APK change, GitHub push,
Cloudflare account mutation, or automatic traffic routing.

## Why

Ubuntu snapshot `20261018T000000Z` returned HTTP 503 repeatedly for signed
`InRelease` metadata and package indices on 27 Oct 2026. V8.5 bounded retries
are a useful temporary improvement but cannot create missing upstream data.

## Phase 1 — local immutable and verified content store

`quality/tools/appforge_package_mirror_v9.py` reads the app's frozen APT profile
and package allowlist. It enumerates the four pinned `InRelease` hashes and
all de-duplicated `.deb` SHA-256 objects for both host architectures. Local
objects live at `objects/sha256/ab/<64-byte-hex>` and are ingested only after
matching an allowlisted SHA-256 and exact `.deb` byte length. Duplicate hashes
are reused. Untrusted source URLs, unexpected architecture, corrupted files,
and symlinks fail closed. No package download happens automatically.

```
python3 quality/tools/appforge_package_mirror_v9.py --store "$HOME/appforge-v9-local-mirror" inspect
python3 quality/tools/appforge_package_mirror_v9.py --store "$HOME/appforge-v9-local-mirror" ingest --sha256 EXPECTED_64_HEX --file /path/to/a/pinned/object
python3 -m unittest discover -s quality/tests -p 'test_v9_package_mirror.py' -v
```

**Important**: `distribution_ready=false` and `apt_ready=false` are hardcoded
until signing, package-index closure, and client acceptance are implemented.
An `InRelease` SHA check alone does NOT verify its PGP signature; existing
`gpgv` verification remains mandatory. A production mirror also needs the
signed Release's referenced `Packages` indexes with exact hashes, not just
`InRelease` and `.deb` objects.

## Phase 2 — controlled R2 staging (not yet deployed)

1. Create a private R2 bucket `appforge-packages-staging` in the relevant
   Cloudflare account. Never commit R2 keys or private signing material.
2. Import **all** signed index and `.deb` bodies from authorized Ubuntu
   distribution sources. Retain upstream provenance, component/license data,
   signed metadata, exact SHA-256 and size. Ingestion fails if bytes are
   inaccessible or don't match pins: do not substitute current rolling packages.
3. Materialize `Packages` and signed `InRelease` closure. Verify Ubuntu archive
   signature using trusted Ubuntu keyring, release metadata expiry, the release's
   index hashes, downloaded package SHA-256, and expected architecture.
4. Sign a canonical release manifest with an offline-held AppForge Ed25519 key;
   ship the trust anchor via a reviewed Android update, with key rotation and
   anti-rollback policy. Never treat an R2 URL itself as trusted identity.
5. Upload immutable objects only after validation. Read via a dedicated
   custom domain (not `r2.dev` for production) and Cloudflare CDN, using
   bounded Range requests and strong hash checks.
6. Run canary Android ARM64 installation, process-death/resume, rollback,
   tamper/error and host/WASM validation. Only after that allow opt-in routing.
7. Build CI must never publish package sets merely because ZIP/APK compilation
   passed. Keep `ALL_ENGINES_READY=false` until physical acceptance.

## Operational gates

- Do not expose a public mirror before provenance/licensing/security review.
- Do not change `as_stamp`, `AptModuleLocks`, URL-pinned catalog, `gpgv` or
  SHA validation as a shortcut for snapshot HTTP 503.
- Keep projects, local rootfs, cache, `main`, Play Production and installed
  Android APK unchanged. No automatic releases or DNS switch.
- Source snapshot is currently unavailable: **this phase alone does not fix
  Rust installation**. Seed the exact pinned objects first.
- Budget: R2 request/storage costs, edge-cache misses, abuse controls and
  upstream license compliance require a production review.
