# AppForge Studio v2.4 — Pro Plans + Google Play Billing

The Pro center now supports two official Google Play plans.

## Plans

### Pro
One-time product:
`STUDIO_PRO_PRODUCT_ID=appforge_pro_lifetime`

Behavior:
- one purchase
- no expiry in AppForge entitlement
- server verifies ProductPurchaseV2
- backend acknowledges the purchase

### Pro Monthly
Subscription product:
`STUDIO_PRO_MONTHLY_PRODUCT_ID=appforge_pro_monthly`

Behavior:
- auto-renewing Google Play subscription
- server verifies SubscriptionPurchaseV2
- entitlement expiry follows Google Play expiryTime
- user can cancel through Google Play
- active / grace period / cancelled-but-not-expired states remain entitled according to the existing verifier rules

## Android Billing

App dependency:
`com.android.billingclient:billing-ktx:9.1.0`

The app:
- queries both products from Google Play
- displays localized Play prices when available
- launches the official Play purchase flow
- forwards only the purchase token to the AppForge server
- does not grant Pro locally
- does not acknowledge or consume Studio Pro on-device
- server remains authoritative

## Security

Both lifetime and monthly purchases retain v2.3 protections:
- authenticated AppForge account
- Play Integrity session in strict mode
- server-side Google Play verification
- server-side Pro entitlement
- Pro build gates on official Build Service
