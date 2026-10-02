---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-02
last_verified: 2026-10-02
confidence: high
tags:
  - security
  - auth
  - pro
  - publisher-signing
related:
  - "[[Database_Map]]"
  - "[[Account_And_Security_Map]]"
  - "[[Windows_Publisher_Authorization]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/security/SecureAccountStore.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/OwnerAccessPolicy.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/GoogleAdminIdentityClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/AdminOpsScreen.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningAuthorizationClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningPolicy.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningProvider.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningStore.kt"
  - "cloudflare/control-plane/src/google_oidc.mjs"
  - "cloudflare/control-plane/src/index.mjs"
---
# Security and Entitlements

## Current identity model

Normal AppForge use is accountless. Legacy email/password state is not an
administrator authority.

Owner/admin access uses Google Credential Manager on Android and a signed Google
ID token. The control plane verifies Google signature, issuer, audience,
presenter, expiry and nonce where required.

The stable Google subject is SHA-256 hashed before D1 allow-list lookup.
Only an explicitly active `admin_identities` row grants administrator access.

`OwnerAccessPolicy` keeps active owner authority in memory. An encrypted stored
Google ID token is only a candidate until the HTTPS control plane verifies it
again.

## Pro authority

Administrator-issued lifetime Pro remains server authoritative. Local UI state,
a local installation identifier or a successful client action alone does not
grant Pro.

Device ownership uses private Keystore material that is not exported. Recovery
and reactivation require fresh server challenges and signed device proof.

Google Play billing is a separate entitlement path and is not proven by the
administrator-code lifecycle.

## Publisher signing

Windows publisher configuration is owner-only.

PKCS12/PFX material is encrypted at rest through Android Keystore-backed
storage. The password is transient. Signing material is never sent to the
Cloudflare authorization service.

A Windows EXE may be publisher-signed only after receiving and consuming a
short-lived server-verified artifact-bound grant. Replay, artifact mutation,
offline authorization failure, invalid certificate state and verification
failure all fail closed.

See [[Windows_Publisher_Authorization]].

## Credential hygiene

Do not persist raw Google ID tokens, activation codes, private keys, signing
passwords, challenge nonces or signatures in Second Brain documentation.

Repository and CI evidence may record hashes, commit identifiers, workflow
identifiers, safe error codes and PASS/FAIL outcomes.

## Evidence rule

Source and automated tests prove implementation behavior.

CI proves the tested revision.

Staging HTTP checks prove the deployed endpoint behavior that was exercised.

Physical-device evidence is required before a physical acceptance claim.
