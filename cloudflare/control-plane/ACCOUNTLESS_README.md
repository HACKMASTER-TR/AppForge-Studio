# AppForge accountless Control Plane — development staging

The user opted for **no normal-user AppForge account**. Do not send normal users
through email/password registration or require email delivery for ordinary use.
The prior Phase 2 account migration was *never deployed* and is replaced by
`0001_accountless_control_plane.sql`. Do not apply the obsolete accounts schema.

## Identity, privileges and purchasing

- A Play Store account is **not** automatically exposed to AppForge's backend.
- Free usage can be device-local without signup. Any *server-enforced* quotas
  need a proof-of-possession installation credential; the user-supplied
  `X-AppForge-Device-ID` is untrusted and must not grant privileges.
- Pro is purchased/restored with Google Play Billing. The Worker must verify
  purchase tokens against the Google Play Developer API, check package,
  one-time product, purchase state, acknowledgements, replay and void/refund/revocation. Google
  verification and secure token refresh/RTDN are **not implemented** here.
- Admin signs in separately with a Google ID token. The server must verify
  signature, issuer, audience and expiry, then match the Google `sub` against
  an explicitly provisioned admin identity. Email and a local owner flag
  are never sufficient. **Not implemented** here.
- `admin_identities`, `installations`, `play_purchase_records`, `quota_events`
  and `audit_events` are reserved schema only. No account/email/password tables.

## Implemented staging API

- `GET /health` probes the actual D1 `DB` binding.
- `GET /api/client/android/policy?versionCode=N` returns a non-blocking,
  *staging-only* NORMAL policy without inventing a Play release.
- Former `/api/auth/*` endpoints return HTTP 410.
- Admin, Pro, purchase, device and quota endpoints fail closed with HTTP 503.
  The Worker does **not** issue Pro/admin rights, sell, restore, or validate
  purchases and is **not** a production service.

## Mandatory gates before rollout

1. Remove signup/login/password UX and legacy session assumptions across the
   Android Home, Pro, Admin, templates and dependent screens; do not bypass
   authorization checks or enable purchases just by deleting `session != null`.
2. Implement and test Google Play Billing/Play Developer API verification,
   purchase restoration, expiry/cancellation and server-side entitlement
   reconciliation. Avoid reusing one purchase token for unrelated devices.
3. Implement verified Google admin login and explicit admin bootstrap.
4. Decide and implement cryptographic installation credentials or limit free
   quotas to device-local, then wire Android to the new protocol.
5. Apply the reviewed D1 migration to a disposable database first, verify
   database bindings and rollback strategy, then separately test `workers.dev`.
6. Enable production only after end-to-end physical-device acceptance. Do not
   change `api.appforgecloud.com` or `appforge-failover` yet.

No commit, push, PR, migration execution, deploy or Play publication occurs
in this staging patch. Device Build V3 / Runtime V3 / AAPT2 remain untouched.

## 2026-09-19 — Single lifetime Pro (staging)

There is one **non-consumable one-time** Pro in-app product only.
Proposed Play Console ID: `appforge_pro_lifetime` (must be confirmed in the
actual Play Console and Android source before deployment). No monthly/annual
subscriptions, no quota add-ons, no automatic renewals, no consumption.
A valid purchase remains usable unless Google later reports a refund, void or
revocation; access follows the *Google-verified purchase*, not a user-provided
email, device ID or synthetic account. Multiple device restoration requires
verified Google Play Billing query + server reconciliation.

The existing Android code still contains legacy monthly/add-on/session UX and
must be refactored and device-tested separately. This package stages the
**server-side contract only**; it does NOT activate or verify any purchases.
Existing Google Play subscription obligations, if any, must not be silently
removed by hiding them in the new UI.
