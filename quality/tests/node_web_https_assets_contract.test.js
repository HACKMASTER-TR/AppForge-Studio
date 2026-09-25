import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = p =>
  fs.readFileSync(
    new URL("../../" + p, import.meta.url),
    "utf8"
  );

const engine = read(
  "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
);

const fast = read(
  "android-app/app/src/main/assets/device-build/FastActivity.java"
);

const loader = read(
  "android-app/app/src/main/assets/device-build/AppForgeLocalAssets.java"
);

test("node-web only HTTPS asset loading", () => {
  assert.match(engine, /nodeWebAssets = true/);
  assert.match(engine, /nodeWebAssets: Boolean = false/);
  assert.match(engine, /assetHttpsEnabled.*nodeWebAssets && draft.sourceMode == SourceMode.LOCAL/);
  assert.match(engine, /AppForgeLocalAssets\.java.*copyTo/);
  assert.match(fast, /file:\/\/\/android_asset\/site\/index\.html/);
});

test("bundled Vite modules use local HTTPS", () => {
  assert.match(fast, /shouldInterceptRequest\(/);
  assert.match(fast, /AppForgeLocalAssets\.START_URL/);
  assert.match(loader, /https:\/\/appassets\.androidplatform\.net\/assets\/site\/index\.html/);
  assert.match(loader, /assets\.open\("site\/" \+ relative\)/);
  assert.match(loader, /text\/javascript/);
  assert.match(loader, /text\/css/);
  assert.match(loader, /text\/html/);
  assert.match(loader, /\.wasm/);
});

test("local origin fails closed", () => {
  assert.match(loader, /getPort\(\) == -1/);
  assert.match(loader, /getUserInfo\(\) == null/);
  assert.match(loader, /%2f/);
  assert.match(loader, /%5c/);
  assert.match(loader, /"\.\."\.equals\(segment\)/);
  assert.match(loader, /404,/);
  assert.match(fast, /isLocalOrigin\(origin\)/);
  assert.match(fast, /isLocalPage\(actual\)/);
});
