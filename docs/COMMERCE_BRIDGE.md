# AppForge v0.7 — Commerce & Ads JavaScript API

## Ürünleri sorgula

```js
window.addEventListener("appforge-products", (event) => {
  console.log(event.detail.products);
});

AppForge.queryProducts();
```

## Satın alma başlat

```js
AppForge.purchase("premium");
```

Sonuç eventleri:

- `appforge-purchase-success`
- `appforge-purchase-pending`
- `appforge-purchase-cancelled`
- `appforge-purchase-error`
- `appforge-purchase-acknowledged`

## Satın almaları geri yükle

```js
AppForge.restorePurchases();
```

## Reklam kaldırıldı mı?

```js
const removed = AppForge.adsRemoved();
```

## Interstitial reklam

```js
AppForge.showInterstitial();
```

Event:
- `appforge-ad-ready`
- `appforge-ad-not-ready`
- `appforge-ad-skipped`

## Rewarded reklam

```js
window.addEventListener("appforge-reward-earned", (event) => {
  console.log(event.detail.amount, event.detail.type);
});

AppForge.showRewarded();
```

## Önemli

v0.7 satın alma durumunu cihazda işler ve satın almayı acknowledge eder.
Gerçek üretim ortamında satın alma tokenının güvenilir bir backend üzerinden Google Play Developer API ile doğrulanması önerilir.
