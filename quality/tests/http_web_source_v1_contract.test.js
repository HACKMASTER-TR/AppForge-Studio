import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
const read = p => fs.readFileSync(new URL("../../" + p, import.meta.url), "utf8");
const main = read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt");
const engine = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt");
const fast = read("android-app/app/src/main/assets/device-build/FastActivity.java");
const win = read("android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableExePackager.kt");

test("web source accepts HTTP and HTTPS", () => {
  assert.match(main, /isSupportedWebSourceUrl/);
  assert.match(main, /"https:\/\/"[\s\S]{0,300}?"http:\/\/"/);
  assert.match(main, /URL http:\/\/ veya https:\/\/ ile başlamalı/);
});

test("generated Android wrapper enables cleartext only for HTTP URL source", () => {
  assert.match(engine, /val usesCleartextTraffic[\s\S]{0,260}?SourceMode\.URL[\s\S]{0,220}?"http:\/\/"/);
  assert.match(engine, /android:usesCleartextTraffic="\$usesCleartextTraffic"/);
});

test("FastActivity loads HTTP and HTTPS", () => {
  assert.match(fast, /"https"\.equalsIgnoreCase\(scheme\)[\s\S]{0,120}?"http"\.equalsIgnoreCase\(scheme\)[\s\S]{0,180}?webView\.loadUrl/);
});

test("remote bridge remains HTTPS only", () => {
  assert.match(main, /Uzak Native Bridge yalnız HTTPS web kaynağında kullanılabilir/);
  assert.match(engine, /Uzak Native Bridge yalnız HTTPS web kaynağında kullanılabilir/);
  assert.match(fast, /nativeBridgeAllowRemote[\s\S]{0,1000}?!"https"\.equalsIgnoreCase/);
});

test("Windows Portable accepts HTTP and HTTPS but bridge remains HTTPS only", () => {
  assert.match(win, /Windows URL modu HTTP veya HTTPS gerektirir/);
  assert.match(win, /Windows uzak Native Bridge yalnız HTTPS URL ile kullanılabilir/);
});
