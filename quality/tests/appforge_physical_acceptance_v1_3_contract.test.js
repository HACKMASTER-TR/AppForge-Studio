import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = p => fs.readFileSync(new URL("../../" + p, import.meta.url), "utf8");
const main = read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt");
const build = read("android-app/app/src/main/java/com/appforge/studio/BuildProgressService.kt");
const home = read("android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt");
const dashboard = read("android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeDashboard.kt");
const pro = read("android-app/app/src/main/java/com/appforge/studio/ui/ProExperienceV2.kt");
const video = read("android-app/app/src/main/java/com/hackmaster/videoforge/VideoForgeActivity.kt");
const media = read("android-app/app/src/main/java/com/hackmaster/videoforge/MediaSourceCompat.kt");

test("terminal result notification is dismissed only after terminal result tap", () => {
  assert.match(build, /BUILD_TERMINAL_NOTIFICATION_PERSIST_V1_1/);
  assert.match(build, /BUILD_TERMINAL_NOTIFICATION_ACK_V1_3/);
  assert.match(build, /fun dismissTerminalResultNotification[\s\S]*\.cancel\(\s*NOTIFICATION_ID\s*\)/);
  const a = main.indexOf("ACTIVE_BUILD_NOTIFICATION_REBIND_V2");
  const b = main.indexOf("var conversionApkUri", a);
  const block = main.slice(a, b);
  assert.match(block, /setOf\("success", "failed", "cancelled", "canceled"\)[\s\S]*BuildProgressService\.clear\(context\)[\s\S]*dismissTerminalResultNotification/);
  assert.doesNotMatch(block, /BuildProgressService\.stop\(context\)/);
});

test("VideoForge keeps selected provider URI primary with verified local fallback", () => {
  const a = video.indexOf("private fun startSingle(");
  const b = video.indexOf("private fun startQueue()", a);
  const block = video.slice(a, b);
  assert.match(block, /VIDEOFORGE_PROVIDER_FIRST_PROCESSING_V1_3/);
  assert.match(block, /val processingUri\s*=\s*uri/);
  assert.doesNotMatch(block, /materializeForProcessing/);
  assert.match(block, /StorageGuard\.requireEnough[\s\S]*EXTRA_VIDEO_URI[\s\S]*processingUri\.toString\(\)/);
  assert.match(media, /direct\.setDataSource\(\s*context,\s*uri/);
  assert.match(media, /openAssetFileDescriptor/);
  assert.match(media, /VIDEOFORGE_LOCAL_COPY_INTEGRITY_V1_2/);
});

test("Home replaces PRO Plan tile with clickable Offline Pack entry", () => {
  assert.match(home, /onOpenOfflinePack: \(\) -> Unit/);
  assert.match(home, /ModernHomeHero\([\s\S]*onOpenOfflinePack = onOpenOfflinePack/);
  const a = dashboard.indexOf("internal fun ModernHomeHero(");
  const b = dashboard.indexOf("@Composable\\nprivate fun HomeStat", a);
  const hero = dashboard.slice(a, b);
  assert.match(hero, /OfflinePackHomeStat/);
  assert.doesNotMatch(hero, /"Plan"/);
  assert.match(dashboard, /HOME_OFFLINE_PACK_ENTRY_V1_3/);
  assert.match(dashboard, /"Tam Çevrimdışı"/);
  assert.match(dashboard, /"Derleme Paketi"/);
  assert.match(dashboard, /modifier\.clickable[\s\S]*onClick\(\)/);
  assert.match(home, /TextButton\(onClick = onOpenPro\)/);
});

test("Settings Offline Pack return state is isolated from workspace return state", () => {
  assert.match(main, /OFFLINE_PACK_RETURN_ISOLATION_V1_3/);
  assert.match(main, /var offlinePackReturnScreen by/);
  assert.match(main, /fun openOfflinePack\(\)[\s\S]*offlinePackReturnScreen\s*=\s*screen[\s\S]*screen\s*=\s*AppScreen\.OFFLINE_PACK/);
  const a = main.indexOf("AppScreen.SETTINGS ->");
  const b = main.indexOf("AppScreen.OFFLINE_PACK_FIRST_RUN ->", a);
  const settings = main.slice(a, b);
  assert.match(settings, /onOpenOfflinePack\s*=\s*\{[\s\S]*openOfflinePack\(\)/);
  assert.doesNotMatch(settings, /onOpenOfflinePack\s*=\s*\{[\s\S]*openWorkspaceScreen\(\s*AppScreen\.OFFLINE_PACK/);
  const c = main.indexOf("AppScreen.OFFLINE_PACK ->");
  const d = main.indexOf("AppScreen.LANGUAGE ->", c);
  const offline = main.slice(c, d);
  assert.match(offline, /onBack\s*=\s*\{[\s\S]*screen\s*=\s*offlinePackReturnScreen/);
});

test("PRO support removes bottom Play Console implementation note", () => {
  assert.match(pro, /Geliştiriciye Destek Ol/);
  assert.match(pro, /enabled\s*=\s*false/);
  assert.doesNotMatch(pro, /Destek ürünleri Google Play Console'da ayrı ürünler/);
});
