import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
const root = new URL("../../", import.meta.url);
const read = p => fs.readFileSync(new URL(p, root), "utf8");
const home = read("android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt");
const dashboard = read("android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeDashboard.kt");
const main = read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt");
const settings = read("android-app/app/src/main/java/com/appforge/studio/AppForgeSettingsScreens.kt");
const admin = read("android-app/app/src/main/java/com/appforge/studio/AdminOpsScreen.kt");
const draft = read("android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt");
const build = read("android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt");
const agent = read("android-app/app/src/main/java/com/appforge/studio/ai/AppForgeUnifiedAgentStudioScreen.kt");
test("new-project choices and package defaults are clear and editable", () => {
  assert.match(dashboard, /HIZLI OLUŞTUR/);
  assert.match(dashboard, /GELİŞMİŞ OLUŞTUR/);
  assert.match(draft, /packageName: String = "com.appforgestudio.myapp"/);
  const quick = main.slice(main.indexOf("private fun QuickCreateScreen("), main.indexOf("private data class PreviewPreset("));
  assert.ok(quick.indexOf("1. Uygulama adı") < quick.indexOf("2. İçerik"));
  assert.ok(quick.indexOf("2. İçerik") < quick.indexOf("3. İkon"));
  assert.doesNotMatch(quick.slice(0,quick.indexOf("LazyColumn(")), /TextButton[\s\S]*?"Gelişmiş"/);
  assert.match(quick, /label = \{ Text\("Paket adı"\) \}/);
  assert.match(quick, /extraFeaturesExpanded/);
  assert.match(quick, /if \(extraFeaturesExpanded\)/);
  assert.match(quick, /Bu paket adı başka bir projede kullanılıyor/);
});
test("version code can be cleared while its numeric build value remains positive", () => {
  assert.match(main, /var versionCodeInput by remember\(d.versionCode\)/);
  assert.match(main, /versionCodeInput = digits/);
  assert.match(main, /digits.toIntOrNull\(\)\?\.takeIf \{ it >= 1 \}/);
  assert.match(main, /versionCodeInput.toIntOrNull\(\)\?\.let \{ it >= 1 \} == true/);
});
test("admin discovery is private UI only and server verification remains mandatory", () => {
  assert.doesNotMatch(home, /TextButton\(onClick = onOpenAdmin\)|OwnerAdminCard\(/);
  assert.match(settings, /versionTapCount >= 7/);
  assert.match(settings, /onOpenAdmin\(\)/);
  assert.match(admin, /restoreStoredAdminSession/);
  assert.match(admin, /AdminOpsApiClient\(context, serverUrl\)\.systemStatus\(stored.first\)/);
  assert.match(admin, /SecureAccountStore\.loadVerifiedGoogleAdmin/);
  assert.match(main, /restoreStoredAdminSession\(context, DEFAULT_CONTROL_PLANE_URL\)/);
});
test("historical local agent outputs use a strict session and artifact match", () => {
  assert.match(build, /persistedUnifiedAgentArtifact\(buildId, kind\)/);
  assert.match(build, /AppForgeAgentSessionStore/);
  assert.match(build, /remote.status.equals\("success"/);
  assert.match(build, /remote.buildId == buildId/);
  assert.match(build, /matches|singleOrNull\(\)/);
  assert.match(build, /file.parentFile == directory && file.isFile/);
  assert.doesNotMatch(build.slice(build.indexOf("private fun persistedUnifiedAgentArtifact"),build.indexOf("fun projectQuota")), /deleteRecursively|Downloads\/AppForgeStudio|appforge-owner-vault/);
});
test("technical findings are disclosed, never silently deleted", () => {
  assert.match(agent, /Cihazda oluşturuldu/);
  assert.match(agent, /technicalDetails/);
  assert.match(agent, /Teknik ayrıntılar/);
  assert.match(agent, /ReleaseReviewCard/);
  assert.match(agent, /Build Logları/);
});
