# QR / Barkod Tarama

v0.7 Google Code Scanner kullanır.

Web:

```js
window.addEventListener("appforge-scan-result", (event) => {
  console.log(event.detail.rawValue);
});

AppForge.scanCode();
```

Eventler:
- `appforge-scan-result`
- `appforge-scan-cancelled`
- `appforge-scan-error`

Google Code Scanner tarayıcı arayüzünü Google Play services üzerinden sunar; AppForge'un ürettiği uygulamanın ayrıca CAMERA runtime izni istemesine gerek kalmaz.
