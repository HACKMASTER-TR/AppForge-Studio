# AppForge JavaScript Bridge

Bridge açıksa WebView içinde `window.AppForge` kullanılabilir.

## Platform

```js
const platform = AppForge.platform();
const version = AppForge.appVersion();
```

## Paylaşım

```js
AppForge.share("Başlık", "Paylaşılacak metin");
```

## Clipboard

```js
AppForge.copy("Kopyalanacak metin");
const text = AppForge.readClipboard();
```

## Titreşim

```js
AppForge.vibrate(80);
```

Uygulama tarafı süreyi 1–1000 ms arasında sınırlar.

## Billing Ready Event

Play Billing açıksa bağlantı hazır olduğunda:

```js
window.addEventListener("appforge-billing-ready", () => {
  console.log("Billing hazır");
});
```

> v0.6'da ürün sorgulama/satın alma akışının Android altyapısı başlatılıyor.
> Tam satın alma API'si v0.7'de JS bridge'e bağlanacak.
