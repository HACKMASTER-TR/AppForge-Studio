# AppForge V15.1 physical signing diagnostics (candidate)

- Status: isolated draft PR #76, NOT merged, NOT released, no Play action.
- Trigger: physical AAB with a correct imported test PKCS12 fails at 15%; log visibility sanitization hides generic Java exception messages that contain `password`.
- This instrumentation does **not** assert a root cause or fix signing. Imported PKCS12, vault SHA-256, alias, key validation and APK/AAB output atomicity stay unchanged.
- `APPFORGE_FAST_AAB_STAGE_PKCS12_LOAD`: Android JCA could not load the imported PKCS12 (password, algorithm/provider compatibility or corruption still need differentiation).
- `..._PROVIDER`: provider unavailable. `..._ALIAS`: key alias lookup failed. `..._PRIVATE_KEY`: private key extraction failed. `..._CERTIFICATE`: certificate / algorithm / validity failed. `..._KEY_PAIR`: private/public signature mismatch. `..._SIGNER_SETUP`: APK signer configuration failed.
- On further progress, safe logs indicate `APPFORGE_FAST_AAB_STAGE_KEY_OPEN_OK`, `..._UNSIGNED_OK`, `..._SIGNED_OK`.
- No exception details, stack traces, credentials, key material or nested causes are included in the diagnostic message. Stage names are static enum values. Original failure handling and cleanup remain fail-closed.
- Acceptance: JS full quality, Android unit tests, isolated Debug CI, then physical AAB and BOTH with correct test key; wrong-password negative test and certificate comparison. Main merge requires separate final approval.
