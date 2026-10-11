import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = path =>
  fs.readFileSync(
    new URL("../../" + path, import.meta.url),
    "utf8"
  );

const build = read(
  "android-app/app/src/main/java/com/appforge/studio/BuildProgressService.kt"
);
const main = read(
  "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
);
const home = read(
  "android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
);
const dashboard = read(
  "android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeDashboard.kt"
);
const pro = read(
  "android-app/app/src/main/java/com/appforge/studio/ui/ProExperienceV2.kt"
);

test("completed build notification survives Service teardown", () => {
  assert.match(build, /BUILD_TERMINAL_NOTIFICATION_PERSIST_V1_1/);
  assert.match(
    build,
    /preserveTerminalNotificationOnDestroy\s*=\s*true[\s\S]*stopForeground\(\s*STOP_FOREGROUND_DETACH\s*\)[\s\S]*stopSelf\(\s*startId\s*\)/
  );

  const destroy = build.slice(
    build.indexOf("override fun onDestroy"),
    build.indexOf("override fun onBind")
  );
  assert.match(destroy, /if\s*\(\s*preserveTerminalNotificationOnDestroy\s*\)/);
  assert.match(destroy, /STOP_FOREGROUND_DETACH/);
  assert.match(destroy, /else[\s\S]*STOP_FOREGROUND_REMOVE[\s\S]*\.cancel/);
});

test("PRO entry is compact in Home TopAppBar instead of a large card", () => {
  assert.match(home, /HOME_PRO_TOP_APP_BAR_V1_2/);
  assert.match(
    home,
    /TextButton\(onClick = onOpenPro\)[\s\S]*Text\("PRO"\)/
  );
  assert.doesNotMatch(home, /ModernProCard\s*\(/);
  assert.match(dashboard, /"PRO"/);
});

test("active PRO gets real logo animated confetti and support flow", () => {
  assert.match(main, /PRO_HOME_EXPERIENCE_V1_1/);
  assert.match(main, /PRO_SUPPORT/);
  assert.match(pro, /Tebrikler!/);
  assert.match(pro, /PRO Dünyasına Hoşgeldin/);
  assert.match(pro, /Hadi Başlayalım/);
  assert.match(pro, /Geliştiriciye Destek Ol/);
  assert.match(pro, /PRO_CELEBRATION_CONFETTI_V1_2/);
  assert.match(pro, /rememberInfiniteTransition/);
  assert.match(pro, /Canvas\(/);
  assert.match(pro, /ic_launcher_foreground/);
  assert.doesNotMatch(pro, /<\/>|securityMessage|✦\s+•/);
});

test("approved developer note is present", () => {
  assert.match(pro, /AppForge büyümeye devam ediyor/);
  assert.match(pro, /Destekler isteğe bağlıdır/);
  assert.match(pro, /erişim haklarını değiştirmez/);
});

test("support cards stay disabled without showing implementation details", () => {
  assert.match(pro, /enabled\s*=\s*false/);
  assert.doesNotMatch(
    pro,
    /Destek ürünleri Google Play Console'da ayrı ürünler/
  );
  assert.doesNotMatch(pro, /launchBillingFlow|consumeAsync/);
});

test("existing lifetime PRO billing contract remains in MainActivity", () => {
  const start = main.indexOf("private fun ProUpgradeScreen(");
  assert.ok(start >= 0);
  const block = main.slice(start);
  assert.match(block, /Pro Ömür Boyu/);
  assert.match(block, /launchLifetime/);
  assert.doesNotMatch(block, /launchMonthly|launchQuotaAddon/);
});
