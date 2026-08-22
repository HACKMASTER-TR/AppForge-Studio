# v1.8 — Secure Google Play Billing Verification

The old generic purchase verifier has been replaced.

## Server-side allowlists

Configure:

```env
PLAY_VERIFIER_ENABLED=true
PLAY_ALLOWED_PACKAGES=com.example.app
PLAY_INAPP_PRODUCTS=premium_unlock,remove_ads,coins_100
PLAY_CONSUMABLE_PRODUCTS=coins_100
PLAY_SUBSCRIPTION_PRODUCTS=premium_monthly,premium_yearly
```

The client can no longer use the verifier for an arbitrary package or arbitrary product.

## One-time products

Verification uses Google Play Developer API:

`purchases.productsv2.getproductpurchasev2`

Entitlement is granted only when:
- returned line item contains the expected product ID
- purchase state is `PURCHASED`
- package/product are allowlisted

For non-consumables, the backend acknowledges the purchase.

For configured consumables, the backend consumes the purchase.

## Subscriptions

Verification uses:

`purchases.subscriptionsv2.get`

Entitlement is allowed only for a matching product with a future expiry time and these states:
- `SUBSCRIPTION_STATE_ACTIVE`
- `SUBSCRIPTION_STATE_IN_GRACE_PERIOD`
- `SUBSCRIPTION_STATE_CANCELED` (cancelled but not yet expired)

Entitlement is denied for:
- pending
- paused
- on hold
- expired
- pending-purchase-cancelled
- unspecified

Eligible initial subscription purchases are acknowledged server-side.

## Token storage

Raw purchase tokens are not stored.

PostgreSQL stores:
- SHA-256 token hash
- package
- product
- type
- state
- entitlement
- acknowledgement/consumption state
- expiry
- verification timestamps

A purchase token cannot later be reused for a different package/product tuple.

## Minimal response

The API returns only the entitlement result and safe state metadata.

Raw Google purchase resources and Order IDs are not sent back to the app.
