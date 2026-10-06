import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = path =>
  fs.readFileSync(new URL("../../" + path, import.meta.url), "utf8");

const main = read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt");
const build = read("android-app/app/src/main/java/com/appforge/studio/BuildProgressService.kt");
const home = read("android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt");
const pro = read("android-app/app/src/main/java/com/appforge/studio/ui/ProExperienceV2.kt");

test("active notification tap preserves the exact running foreground tracker", () => {
  const start = main.indexOf("ACTIVE_BUILD_NOTIFICATION_REBIND_V2");
  const end = main.indexOf("var conversionApkUri", start);
  const block = main.slice(start, end);
  assert.match(block, /ACTIVE_BUILD_NOTIFICATION_TAP_PERSIST_V1_2/);
  assert.match(block, /restoreFromEngine/);
  assert.doesNotMatch(block, /BuildProgressService\.stop\(context\)/);
  assert.match(build, /fun onHostResumed[\s\S]*= Unit/);
  assert.match(build, /fun onHostPaused[\s\S]*= Unit/);
});

test("terminal notification teardown persistence remains protected", () => {
  assert.match(build, /BUILD_TERMINAL_NOTIFICATION_PERSIST_V1_1/);
  assert.match(build, /STOP_FOREGROUND_DETACH/);
  assert.match(build, /preserveTerminalNotificationOnDestroy/);
});

test("Home and PRO celebration implement the physical UX request", () => {
  assert.match(home, /HOME_PRO_TOP_APP_BAR_V1_2/);
  assert.match(home, /Text\("PRO"\)/);
  assert.doesNotMatch(home, /ModernProCard\s*\(/);
  assert.match(pro, /PRO_CELEBRATION_CONFETTI_V1_2/);
  assert.match(pro, /Canvas\(/);
  assert.match(pro, /rememberInfiniteTransition/);
  assert.match(pro, /ic_launcher_foreground/);
  assert.doesNotMatch(pro, /<\/>|securityMessage|✦\s+•/);
  assert.match(pro, /Geliştiriciye Destek Ol/);
  assert.match(pro, /enabled\s*=\s*false/);
});
