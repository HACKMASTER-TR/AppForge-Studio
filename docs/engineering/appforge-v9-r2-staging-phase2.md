# AppForge Global Package Distribution — V9 Phase 2 (R2 staging, fail closed)

State: **READ-ONLY PROTOTYPE / NOT DEPLOYED / NOT READY**.

## What is implemented

- `quality/tools/appforge_r2_plan_v9.py` reads the original pinned V9 mirror and checks every present object using its SHA-256 and exact expected `.deb` byte length.
- It produces a deterministic JSON **dry-run** object upload plan, not an R2 upload. Object keys are immutable `objects/sha256/xx/<sha256>`.
- The plan always sets `r2_upload_allowed=false`, `public_distribution_allowed=false`, `android_client_enabled=false`, even if all 218 pinned objects have been imported.
- `cloudflare/package-distribution/worker.mjs` is an isolated **read-only** R2 gateway, default disabled even with an R2 binding. It accepts only content-addressed GET/HEAD; GET supports a single bounded 16 MiB byte range. It has no upload/delete/list/admin endpoints.
- `wrangler.example.toml` has `workers_dev=false` and a disabled staging gate. It is not a deployment instruction or active config.

## Blocking conditions before any real release

1. 218 pinned objects (214 `.deb` plus four Ubuntu `InRelease`) must be imported from authorized sources and verified; currently 0 are present.
2. Complete relevant Ubuntu signed `Packages` index closure must be obtained and checked against pinned InRelease files, using `gpgv`, Ubuntu's trusted keyring, SHA-256 and expiration checks. Four InRelease SHA-256 hashes alone do not authenticate or complete the repository.
3. Produce and independently verify an offline-signed AppForge Ed25519 release manifest. Android trust-anchor, key rotation, anti-rollback, download resume and hash verification must be reviewed/tested.
4. Perform R2 **staging** upload through an independently audited no-overwrite publisher. This prototype does not upload anything and must never use project-wide credentials.
5. Conduct real ARM64 Android, process-death, cache corruption, tamper, Range-resume, host/WASM probes, revocation and rollback acceptance before changing Android routing.
6. Respect Ubuntu package redistributability, license notices, package refresh policy and R2 costs. No production or Play Production changes before user approval.

### Developer commands (no Cloudflare writes)

```
python3 -m unittest discover -s quality/tests -p 'test_v9_r2_plan.py' -v
node --test cloudflare/package-distribution/worker.test.mjs
python3 quality/tools/appforge_r2_plan_v9.py --store "$HOME/appforge-v9-local-mirror" --out "$HOME/appforge-v9-staging-plan.json"
```

The staging plan describes verified **local** objects only and remains non-publishable. The gateway is not deployed; there is no `wrangler.toml` in this new directory. Do not enable `STAGING_READ_ENABLED` while metadata verification, signing and access policy remain incomplete.
