import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = path =>
  fs.readFileSync(new URL("../../" + path, import.meta.url), "utf8");

const main = read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt");
const build = read("android-app/app/src/main/java/com/appforge/studio/BuildProgressService.kt");
const storage = read("android-app/app/src/main/java/com/hackmaster/videoforge/StorageGuard.kt");
const media = read("android-app/app/src/main/java/com/hackmaster/videoforge/MediaSourceCompat.kt");
const audio = read("android-app/app/src/main/java/com/hackmaster/videoforge/AudioMedia.kt");
const video = read("android-app/app/src/main/java/com/hackmaster/videoforge/VideoForgeActivity.kt");
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

test("VideoForge no longer lets Retriever block extractor-decodable media", () => {
  assert.match(storage, /probeForProcessing/);
  assert.doesNotMatch(storage, /MediaMetadataRetriever|openRetriever/);
  assert.match(media, /VIDEOFORGE_EXTRACTOR_PREFLIGHT_V1_2/);
  assert.match(audio, /VIDEOFORGE_MUX_ROTATION_WITHOUT_RETRIEVER_V1_2/);
  assert.doesNotMatch(audio, /MediaMetadataRetriever|openRetriever/);
});

test("single-video processing keeps provider URI primary with verified local fallback", () => {
  assert.match(media, /VIDEOFORGE_LOCAL_COPY_INTEGRITY_V1_2/);
  assert.match(media, /sourceStreamHash/);
  assert.match(media, /localHash/);
  assert.match(video, /VIDEOFORGE_PROVIDER_FIRST_PROCESSING_V1_3/);
  const start = video.indexOf("private fun startSingle(");
  const end = video.indexOf("private fun startQueue()", start);
  const block = video.slice(start, end);
  assert.match(block, /val processingUri\s*=\s*uri/);
  assert.match(block, /StorageGuard\.requireEnough[\s\S]*startForegroundService/);
  assert.doesNotMatch(block, /materializeForProcessing/);
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
