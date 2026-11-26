# AppForge V9 — Tek paket üretim kabul sözleşmesi

**Verified source baseline:** `cddde90bb2a6062e018242f8c7db4a223c25fd89`, development branch `feat/v9-global-package-distribution-20261027`. `main` and Play Production are out of scope.

## Gerçek durum

- 400 Python + 8 Worker security tests have passed in user Termux (408 total). This covers **offline design**, not deployment.
- Stage 4D is a Python in-process **illustration**, not process isolation or an authenticated service.
- Stage 4A SQLite is on the same host; an attacker can restore an older SQLite file. It is **not** an independent anti-rollback anchor.
- Existing V9 verification accepts independent trust inputs in development. Real production root key management, audited enrollment and signing do not exist.
- GitHub Actions did not execute for the latest commit. A dedicated offline-only CI is included in this package; it must run and PASS remotely before evidence can be submitted.

## One release, eleven independent proofs

1. **Independent service deployment.** Separate trust domain (not device-local); immutable deployment record, configuration, TLS identity and protected access. A localhost listener, Termux service or same-device SQLite does not qualify.
2. **Monotonic durable witness.** Single-authority compare-and-swap and durable generation/release floor; backup restore drill must demonstrate no accepted rollback and no split-brain.
3. **Actual Phase 3G server verification.** Server verifies real canonical signed root/release, expiration, threshold, evidence digest and prior checkpoint itself. Do not trust client `verified=true` claims or a caller-supplied signing callback.
4. **Device enrollment, authentication, revocation.** Proof of key possession, identity bound to key, server-side registration, replay-safe challenge and revocation. TLS alone is not enrollment.
5. **Protected signer.** Production key stored outside repository/device/client; nonexportable key/KMS/HSM where possible, rotation/revocation/epoch rules, audit trail, backup and restore.
6. **Process and key separation.** Verifier, witness store, key use, and client separated by process and trust boundary; no `prepare_model(authorize=...)` reachable remotely.
7. **Android signed receipt enforcement.** Client pins trust, validates fresh Ed25519 receipt, device/challenge/generation/epoch, performs crash-recovery and fails closed offline or on tamper. Test debug APK only; no existing release mutation.
8. **Physical Android acceptance.** First install, update, downgrade, reboot, airplane mode, tampered state, wiped app-data, two devices and revocation, with on-device evidence.
9. **Power-failure and concurrency.** Real process kill, storage crash/power interruption, simultaneous device requests and replica failover, with audited consistent checkpoints.
10. **Remote security CI.** Dedicated V9-only workflow on development branch passes with zero skip and zero resource warning. Its success does not trigger signing/release/Play.
11. **Independent security review.** External threat model and evidence review, strict source SHA and sign-off before **separate** production permission.

`quality/tools/appforge_v9_final_release_gate.py` inventories these eleven categories and verifies local evidence hashes. **An inventory cannot prove remote attestations.** It never prints `READY`, and returns a nonzero exit status even when every file has been submitted. Actual production permission requires independent external review and a separately authorized release decision.

## Hard stop gates

`PRODUCTION_ROOT_KEY=NOT_PRESENT`
`INDEPENDENT_WITNESS=NOT_DEPLOYED`
`PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED`
`PROCESS_ISOLATION=NOT_IMPLEMENTED`
`R2_UPLOAD=NO`
`PUBLIC_DISTRIBUTION=DISABLED`
`ANDROID_CLIENT_ENABLED=NO`
`V9_PRODUCTION_READINESS=BLOCKED`
`ALL_ENGINES_READY=false`

## Deployment boundary

No external service provisioning, credentials, root key creation, GitHub `main` changes, Play Production action, R2 upload, APK or public traffic is authorized by this package. This package adds isolated CI and a review gate; it does not claim the production tasks above have been performed. Save the evidence with your independent deployment and device acceptance and have the remote authority verify it before any future release.
