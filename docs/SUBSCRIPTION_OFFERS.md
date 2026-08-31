# Subscription Base Plans / Offers

v0.9 exposes subscription offers to JavaScript.

```js
window.addEventListener("appforge-products", (e) => {
  const products = e.detail.products;

  for (const product of products) {
    console.log(product.subscriptionOffers);
  }
});
```

Each subscription offer can contain:
- `basePlanId`
- `offerId`
- `offerToken`
- `tags`
- pricing phases

Purchase the default eligible offer:

```js
AppForge.purchase("pro_monthly");
```

Purchase a specific offer:

```js
AppForge.purchaseWithOffer(
  "pro_monthly",
  "OFFER_TOKEN_FROM_APPFORGE_PRODUCTS"
);
```

Do not hard-code an offer token permanently. Offer tokens are obtained from Google Play product details and can change.
