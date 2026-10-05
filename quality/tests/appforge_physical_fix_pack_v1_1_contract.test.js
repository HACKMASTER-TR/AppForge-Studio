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

const media = read(
  "android-app/app/src/main/java/com/hackmaster/videoforge/MediaSourceCompat.kt"
);

const video = read(
  "android-app/app/src/main/java/com/hackmaster/videoforge/VideoForgeActivity.kt"
);

test("completed build notification survives Service teardown", () => {
  assert.match(
    build,
    /BUILD_TERMINAL_NOTIFICATION_PERSIST_V1_1/
  );

  assert.match(
    build,
    /preserveTerminalNotificationOnDestroy\s*=\s*true[\s\S]*stopForeground\(\s*STOP_FOREGROUND_DETACH\s*\)[\s\S]*stopSelf\(\s*startId\s*\)/
  );

  const destroy =
    build.slice(
      build.indexOf("override fun onDestroy"),
      build.indexOf("override fun onBind")
    );

  assert.match(
    destroy,
    /if\s*\(\s*preserveTerminalNotificationOnDestroy\s*\)/
  );

  assert.match(
    destroy,
    /STOP_FOREGROUND_DETACH/
  );

  assert.match(
    destroy,
    /else[\s\S]*STOP_FOREGROUND_REMOVE[\s\S]*\.cancel/
  );
});

test("same selected VideoForge media gets a stable local retry", () => {
  assert.match(
    video,
    /VIDEOFORGE_MEDIA_RETRY_V1_1/
  );

  assert.match(
    video,
    /materializeForProcessing/
  );

  assert.match(
    media,
    /VIDEOFORGE_STABLE_LOCAL_SOURCE_V1_1/
  );

  assert.match(
    media,
    /VIDEOFORGE_MEDIA_PROVIDER_SIZE_TOLERANCE_V1_1/
  );
});

test("PRO entry is visible directly on Home", () => {
  assert.match(
    home,
    /HOME_PRO_ENTRY_V1_1/
  );

  assert.match(
    home,
    /ModernProCard/
  );

  assert.match(
    dashboard,
    /Pro'ya Yükselt/
  );

  assert.match(
    dashboard,
    /AppForge PRO Aktif/
  );
});

test("active PRO gets celebration and support flow", () => {
  assert.match(
    main,
    /PRO_HOME_EXPERIENCE_V1_1/
  );

  assert.match(
    main,
    /PRO_SUPPORT/
  );

  assert.match(
    pro,
    /Tebrikler!/
  );

  assert.match(
    pro,
    /PRO Dünyasına Hoşgeldin/
  );

  assert.match(
    pro,
    /Hadi Başlayalım/
  );

  assert.match(
    pro,
    /Geliştiriciye Destek Ol/
  );
});

test("approved developer note is present", () => {
  assert.match(
    pro,
    /AppForge büyümeye devam ediyor/
  );

  assert.match(
    pro,
    /Destekler isteğe bağlıdır/
  );

  assert.match(
    pro,
    /erişim haklarını değiştirmez/
  );
});

test("support cards do not fake an unconfigured payment flow", () => {
  assert.match(
    pro,
    /Google Play Console'da ayrı ürünler/
  );

  assert.match(
    pro,
    /enabled\s*=\s*false/
  );

  assert.doesNotMatch(
    pro,
    /launchBillingFlow|consumeAsync/
  );
});

test("existing lifetime PRO billing contract remains in MainActivity", () => {
  const start =
    main.indexOf(
      "private fun ProUpgradeScreen("
    );

  assert.ok(
    start >= 0
  );

  const block =
    main.slice(
      start,
      main.indexOf(
        "\\n@Composable",
        start + 20
      )
    );

  assert.match(
    block,
    /Pro Ömür Boyu/
  );

  assert.match(
    block,
    /launchLifetime/
  );

  assert.doesNotMatch(
    block,
    /launchMonthly|launchQuotaAddon/
  );
});
