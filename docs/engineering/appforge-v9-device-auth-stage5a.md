# AppForge Studio V9 — Stage 5A: device proof and single-use challenge core

**Status:** source + local verification only; **not** wired to Android UI, HTTP, production, or Cloudflare deployed Witness. Separate opt-in needed before any deployment or debug APK.

## Added code

- `V9WitnessDeviceProof.kt`: independent P-256 private key generated in Android Keystore, `SHA256withECDSA` challenge signature. No reuse of existing Pro activation key or `Settings.Secure.ANDROID_ID`.
- `V9DeviceProofWire.kt`: stable domain-separated UTF-8 message: `APPFORGE-V9-DEVICE-PROOF\\0` + operation + newline + SHA-256(SPKI) device ID + newline + server challenge ID + newline + server nonce + newline.
- `protocol.mjs`: Cloudflare Web Crypto P-256 verification of canonical SPKI and strict DER ECDSA; requires server-owned registration pin and server-owned challenge fields.
- `challenges.mjs`: private SQLite Durable Object transactionSync-backed one-use challenge store; only explicitly provisioned active devices may obtain challenges. `enroll` is intentionally absent: no caller may self-approve. TTL 90s; re-issued only after expiration/consumption; concurrent consumption is atomic; revocation is rechecked after signature verification.
- `protocol.test.mjs`: real P-256 signature attack tests and challenge storage tests.

## Required integration before a debug APK or public HTTPS service

1. Create a **separate admin-authenticated registration and revocation flow**. Only a trusted operator may provision `v9-auth:registered:<device_id>` in an isolated device-auth Durable Object. Device name or Android ID is never proof of possession.
2. The server must generate challenge IDs/nonces using `crypto.getRandomValues`, issue them over TLS with rate-limiting, and verify+consume them before privileged operations.
3. Add a separately deployed gateway with authentication and an allowlisted service binding to the private V9 Witness. Do not connect an unauthenticated public RPC or give callers direct access to internal DO methods.
4. Implement an independently pinned Ed25519 receipt signer with protected key custody. The Python `appforge_phase3h_witness_receipt_v9.py` is reference-only; this package does **not** issue signed witness receipts yet.
5. Implement Android fail-closed signed receipt validation with a trusted public SPKI key pin and recovery after process death; test physical debug APK on 2 devices **only after explicit approval**.
6. Fix independent external high-water and threat model for Cloudflare administrator/PITR restores. The current V9 Worker still forbids R2/public/Android release distribution.

**Security limitations:** Device proof does not imply device authorization, release approval or attestation of hardware-backed storage. `V9DeviceChallengeStore` is a helper used inside a private DO; its caller must be an independently authenticated Worker, and user-controlled registration objects must never be accepted. Store time/revocation depends on the service boundary and operational controls. No new Worker, API route, R2 binding, secret, main-branch change, or APK is included.

`V9_DEVICE_PROOF_STAGE5A=SOURCE_ONLY`
`V9_PUBLIC_WITNESS_API=DISABLED`
`V9_ANDROID_CLIENT_ENABLED=NO`
`V9_PRODUCTION_READINESS=BLOCKED`
