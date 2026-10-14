# V15.4 local candidate — Vault-generated PKCS12 for Fast HTML AAB

Parent: V15.3 isolated CI-accepted SHA a3b4eae24b112f28ac93c07c6288b676182ead16.
Not committed, pushed, built, installed, merged or released.

- AppForge-generated RSA 3072 PKCS12 (`generated=true`) and vault-imported `.p12/.pfx` (`generated=false`) both pass origin-neutral eligibility.
- Preserve vault canonical directory/metadata registration, file length bounds, SHA-256 consistency, explicit alias, nonempty store/key passwords, Android KeyStore load, RSA certificate/key-pair check, fail-closed AAB and BOTH artifact publication, and fixed redacted diagnostics.
- Do not generate, rotate, recreate or replace signing keys in the build path.
- Require the user to keep an independent secure keystore backup and password before publishing. Existing Keystore Manager supports explicit export. This candidate does not verify external backup existence.
- An origin policy fix is not a fix for possible Android PKCS12_LOAD_IO_EXCEPTION or other stage failures; run physical tests with disposable generated and imported keys for AAB and BOTH, wrong-password no-output and certificate continuity.
- The Play offline-toolchain distribution proposal is a separate technical and policy audit, not implemented here. Do not ship executable code as a Play Asset Delivery asset pack.
- Main, PR 76, original 534, Play release, user projects and keystores untouched.
