# V9 Phase 3B — signed Packages.xz probe (staging only)

**No Cloudflare deployment, R2 upload, Android upgrade, GitHub push, APT sources change, package installation, release publication, or production activation.**

The previously verified four pinned Ubuntu `InRelease` objects form the trust root together with the repository-reviewed Ubuntu archive keyring. This phase addresses two real archive parsing cases: a valid zero-length `Packages` index; and large unrelated files in the authenticated `SHA256` table. Only the selected 16 `Packages` index objects per architecture have an application-enforced 128 MiB uncompressed limit and 32 MiB compressed limit.

The bounded probe:

1. Revalidates all four upstream `InRelease` signatures with gpgv and exact pinned hashes.
2. Builds 16 uncompressed/`.xz` index pairs for `arm64` only, including `main`, `restricted`, `universe`, and `multiverse` in all four suites.
3. Materializes GPG-authenticated **zero-byte** indexes using the empty file SHA-256, without a network request.
4. Optionally requests **one** smallest missing `.xz` from the exact `https://snapshot.ubuntu.com/ubuntu/20261018T000000Z/` source with HTTPS, redirect refusal, a 15-second timeout, strict response length and signed `.xz` SHA-256.
5. Decompresses XZ with a hard output bound; checks the GPG-authenticated uncompressed hash and byte length. Only then atomically imports it into the same SHA-addressed local store, without overwriting existing objects.

Run:

```sh
python3 quality/tools/appforge_signed_index_seed_v9.py \
  --repo . --store "$HOME/appforge-v9-local-mirror" \
  --keyring android-app/app/src/test/resources/apt-snapshot/ubuntu-archive-keyring.gpg \
  --arch arm64 --max-downloads 1 --timeout 15
```

**Status semantics:** `SIGNED_INDEXES=n/16` counts separate Packages index identities, **not** original `218` catalog objects. It is expected that `PRESENT=4, MISSING=214` remains unchanged until `.deb` objects are imported. The existing 218-object catalog intentionally excludes uncompressed `Packages` metadata. `SIGNED_INRELEASE=4_PASS` is necessary but insufficient for catalog closure; full `verify_architecture` will be run only after all 16 index objects are present. An HTTP 503/network failure leaves the original 4/218 untouched and reports `SNAPSHOT_FETCH=BLOCKED`. The response is not silently substituted with a moving archive or unsigned material.

A single-index probe does not establish Android readiness or package redistributability. All distribution, R2, release and Android gates remain disabled.
