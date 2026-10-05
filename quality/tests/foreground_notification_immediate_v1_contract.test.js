import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = path =>
  fs.readFileSync(
    new URL("../../" + path, import.meta.url),
    "utf8"
  );

const service = read(
  "android-app/app/src/main/java/com/appforge/studio/BuildProgressService.kt"
);

const main = read(
  "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
);

const api = read(
  "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
);

const videoService = read(
  "android-app/app/src/main/java/com/hackmaster/videoforge/DubForegroundService.kt"
);

const videoActivity = read(
  "android-app/app/src/main/java/com/hackmaster/videoforge/VideoForgeActivity.kt"
);

test("build notification starts from track without foreground visibility gate", () => {
  assert.match(
    service,
    /FOREGROUND_NOTIFICATION_IMMEDIATE_V1/
  );

  const track = service.slice(
    service.indexOf("fun track("),
    service.indexOf("fun trackBatch(")
  );

  assert.match(
    track,
    /startPending\(context\)/
  );

  assert.doesNotMatch(
    track,
    /!hostForeground/
  );

  assert.doesNotMatch(
    service,
    /private var hostForeground/
  );
});

test("completed build keeps exact restore reference until result persistence", () => {
  assert.doesNotMatch(
    service,
    /foregroundSuppressed/
  );

  assert.doesNotMatch(
    service,
    /hostForeground/
  );

  assert.doesNotMatch(
    service,
    /clear\(this@BuildProgressService\)/
  );

  assert.match(
    service,
    /if \(!active\)[\s\S]*stopForeground\(STOP_FOREGROUND_DETACH\)[\s\S]*stopSelf\(startId\)/
  );
});

test("notification tap hydrates before stopping build tracker", () => {
  const create = main.slice(
    main.indexOf("override fun onCreate"),
    main.indexOf("override fun onNewIntent")
  );

  assert.doesNotMatch(
    create,
    /BuildProgressService\.stop\(this\)/
  );

  const intent = main.slice(
    main.indexOf("override fun onNewIntent"),
    main.indexOf("override fun onResume")
  );

  assert.doesNotMatch(
    intent,
    /BuildProgressService\.stop\(this\)/
  );

  const rebind = main.indexOf(
    "ACTIVE_BUILD_NOTIFICATION_REBIND_V2"
  );

  assert.ok(rebind >= 0);

  const block = main.slice(
    rebind,
    main.indexOf("var conversionApkUri", rebind)
  );

  assert.match(
    block,
    /restoreFromEngine[\s\S]*BuildProgressService\.stop\(context\)/
  );
});

test("terminal build result is persisted before tracker clear", () => {
  const save = main.indexOf(
    "ProjectLibrary.saveBuild("
  );

  assert.ok(save >= 0);

  const clear = main.indexOf(
    "BuildProgressService.clear(",
    save
  );

  assert.ok(clear > save);
});

test("completed local build can restore exact persisted artifact availability", () => {
  assert.match(
    api,
    /NOTIFICATION_RESULT_RESTORE_V1/
  );

  assert.match(
    api,
    /ProjectLibrary[\s\S]*loadBuilds/
  );

  assert.match(
    api,
    /persistedDeviceArtifact\([\s\S]*"apk"/
  );

  assert.match(
    api,
    /persistedDeviceArtifact\([\s\S]*"aab"/
  );
});

test("VideoForge uses foreground service for all four user-started jobs", () => {
  const starts =
    videoActivity.match(
      /ContextCompat\.startForegroundService\(this,\s*i\)/g
    ) ?? [];

  assert.equal(
    starts.length,
    4
  );

  assert.doesNotMatch(
    videoActivity,
    /\bstartService\s*\(/
  );
});

test("VideoForge foreground notification is immediate and not hidden in foreground", () => {
  assert.match(
    videoService,
    /VIDEOFORGE_IMMEDIATE_FOREGROUND_V1/
  );

  assert.match(
    videoService,
    /FOREGROUND_SERVICE_TYPE_DATA_SYNC/
  );

  assert.match(
    videoService,
    /FOREGROUND_SERVICE_IMMEDIATE/
  );

  assert.match(
    videoService,
    /lastMessage = "VideoForge Studio hazırlanıyor…"[\s\S]*showProgressNotification\(\)[\s\S]*acquireWakeLock/
  );

  const visibility = videoService.slice(
    videoService.indexOf("override fun onAppForegroundChanged"),
    videoService.indexOf("override fun onDestroy")
  );

  assert.doesNotMatch(
    visibility,
    /removeProgressNotification\(\)/
  );
});
