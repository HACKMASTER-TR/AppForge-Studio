import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = path =>
  fs.readFileSync(new URL("../../" + path, import.meta.url), "utf8");

const main = read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt");
const fast = read("android-app/app/src/main/assets/device-build/FastActivity.java");
const engine = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt");
const vault = read("android-app/app/src/main/java/com/appforge/studio/io/KeystoreVault.kt");
const icon = read("android-app/app/src/main/java/com/appforge/studio/io/AppIconProcessor.kt");
const job = read("android-app/app/src/main/java/com/appforge/studio/terminal/LinuxTerminalJobService.kt");
const notifier = read("android-app/app/src/main/java/com/appforge/studio/terminal/LinuxSessionNotifier.kt");
const registry = read("android-app/app/src/main/java/com/appforge/studio/terminal/LinuxPtySessionRegistry.kt");
const workspace = read("android-app/app/src/main/java/com/appforge/studio/terminal/TerminalWorkspaceScreen.kt");
const ultimate = read("android-app/app/src/main/java/com/appforge/studio/terminal/TerminalUltimatePanel.kt");
const runtime = read("android-app/app/src/main/java/com/appforge/studio/terminal/LinuxRuntimePanel.kt");
const multi = read("android-app/app/src/main/java/com/appforge/studio/terminal/LinuxMultiSessionTerminalPanel.kt");

test("owner Terminal completion notification persists until tap and carries exact session", () => {
  assert.match(job, /OWNER_TERMINAL_RESULT_NOTIFICATION_V1_4/);
  assert.match(job, /EXTRA_OPEN_TERMINAL/);
  assert.match(job, /EXTRA_TERMINAL_SESSION_ID/);
  assert.match(job, /setOngoing\(true\)[\s\S]*setAutoCancel\(false\)/);
  assert.match(job, /dismissResultNotification/);

  assert.match(notifier, /sessionId:\s*String/);
  assert.match(notifier, /EXTRA_TERMINAL_SESSION_ID/);
  assert.match(notifier, /setOngoing\(true\)[\s\S]*setAutoCancel\(false\)/);
  assert.match(notifier, /dismissCompletedNotification/);
  assert.match(registry, /sessionId\s*=\s*id/);
});

test("notification tap routes only an active owner to the exact Linux session", () => {
  assert.match(main, /OWNER_TERMINAL_RESULT_NAV_V1_4/);
  assert.match(main, /terminalOwner\s*&&[\s\S]*requestedLinuxSessionId\s*=/);
  assert.match(main, /screen\s*=\s*AppScreen\.TERMINAL/);
  assert.match(main, /dismissResultNotification/);
  assert.match(main, /dismissCompletedNotification/);

  assert.match(workspace, /requestedLinuxSessionId/);
  assert.match(workspace, /TerminalWorkspaceTab\.ULTIMATE/);
  assert.match(ultimate, /requestedLinuxSessionId[\s\S]*TerminalUltimateMode\.LINUX/);
  assert.match(runtime, /requestedSessionId/);
  assert.match(multi, /requestedSessionId[\s\S]*activeSessionId\s*=\s*requested/);
  assert.match(multi, /onRequestedSessionConsumed\(\)/);
});

test("managed keystore supports real create save and recreate without persisting passwords", () => {
  assert.match(vault, /KEYSTORE_LOCAL_GENERATION_V1_4/);
  assert.match(vault, /install-toolchain\.sh/);
  assert.match(vault, /keytool/);
  assert.match(vault, /-genkeypair/);
  assert.match(vault, /-keysize 3072/);
  assert.match(vault, /-storetype PKCS12/);
  assert.match(vault, /recreateId/);
  const persist = vault.slice(vault.indexOf("private fun persist"));
  assert.doesNotMatch(persist, /storePassword|keyPassword|password/);

  assert.match(main, /YENİ KEYSTORE OLUŞTUR/);
  assert.match(main, /Dosyaya Kaydet/);
  assert.match(main, /Yeniden Oluştur/);
  assert.match(main, /canGenerate[\s\S]*proStatus\?\.active[\s\S]*terminalOwner/);
});

test("icon editing keeps full-size no-double-padding processor and adds change/remove actions", () => {
  assert.match(main, /ICON_EDIT_ACTIONS_V1_4/);
  assert.match(main, /İkonu düzenle \/ değiştir/);
  assert.match(main, /İkonu kaldır/);
  assert.match(main, /iconUri\s*=\s*null/);
  assert.match(icon, /SAFE_CONTENT_SIZE\s*=\s*OUTPUT_SIZE/);
});

test("successful build completion surface remains the terminal UI instead of stale compile CTA", () => {
  assert.match(main, /builderBuildOutputReady/);
  assert.match(main, /✅ Derleme tamamlandı/);
  assert.match(main, /APK'YI TEKRAR İNDİR/);
  assert.match(main, /APK'YI KUR/);
});

test("generated HTML URL APK uses standard system bars unless fullscreen is explicitly enabled", () => {
  /*
   * V1.6 strengthens the original V1.4 behavior contract:
   *
   * - normal mode must explicitly restore BOTH physical bars;
   * - fullscreen alone may hide the combined system bars;
   * - normal mode must clear inherited FLAG_FULLSCREEN;
   * - the existing manual inset contract remains active.
   *
   * Do not lock this regression test to the retired single
   * controller.show(systemBars()) implementation.
   */
  assert.match(
    fast,
    /APPFORGE_SYSTEM_BARS_PHYSICAL_V1_6/
  );

  assert.match(
    fast,
    /controller\.show\([\s\S]*WindowInsets\.Type\.statusBars\(\)/
  );

  assert.match(
    fast,
    /controller\.show\([\s\S]*WindowInsets\.Type\.navigationBars\(\)/
  );

  assert.match(
    fast,
    /controller\.hide\([\s\S]*WindowInsets\.Type\.systemBars\(\)/
  );

  assert.match(
    fast,
    /clearFlags\([\s\S]*FLAG_FULLSCREEN/
  );

  assert.match(
    fast,
    /onWindowFocusChanged/
  );

  assert.match(
    fast,
    /protected void onResume\(\)/
  );

  assert.match(
    fast,
    /if \([\s\S]*"fullscreen"[\s\S]*content\.setPadding\([\s\S]*0,[\s\S]*0,[\s\S]*0,[\s\S]*0/
  );

  assert.match(
    fast,
    /setOnApplyWindowInsetsListener/
  );
});

test("fast build optimization is isolated to webview-static URL wrapper", () => {
  assert.match(engine, /APPFORGE_FAST_WEB_TOOLCHAIN_REUSE_V1_4/);
  assert.match(engine, /sourceEngine\s*==\s*"webview-static"/);
  assert.match(engine, /staticWebToolchainReady/);
  assert.match(engine, /fastWebBuild\s*=\s*true/);
  assert.match(engine, /useBuildCache\s*=\s*fastWebBuild/);
  assert.match(engine, /if \(useBuildCache\)[\s\S]*--build-cache/);

  assert.match(engine, /\.joinToString\("\\n"\)/);
  assert.doesNotMatch(engine, /\.joinToString\("\s*\n\s*"\)/);
  assert.match(engine, /org\.gradle\.caching=true\\n/);


  const expo = engine.slice(
    engine.indexOf("private fun buildExpoProject"),
    engine.indexOf("private fun buildPythonProject")
  );
  assert.doesNotMatch(expo, /fastWebBuild\s*=\s*true|useBuildCache\s*=\s*true/);

  const python = engine.slice(
    engine.indexOf("private fun buildPythonProject"),
    engine.indexOf("private fun buildGradleProject")
  );
  assert.doesNotMatch(python, /fastWebBuild\s*=\s*true|useBuildCache\s*=\s*true/);
});
