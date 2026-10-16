import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import os from "node:os";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const root =
  path.resolve(
    here,
    "../.."
  );

const read =
  relative =>
    fs.readFileSync(
      path.join(
        root,
        relative
      ),
      "utf8"
    );

const main = read(
  "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
);

const engine = read(
  "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
);

const manager = read(
  "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
);

const screen = read(
  "android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt"
);

test(
  "normal device build is blocked before build allocation when pack is missing",
  () => {
    const gate =
      engine.indexOf(
        "requireReadyForBuild"
      );

    const buildNumber =
      engine.indexOf(
        "AppForgeBuildNumberStore.next"
      );

    assert.ok(
      gate >= 0
    );

    assert.ok(
      buildNumber >= 0
    );

    assert.ok(
      gate < buildNumber
    );
  }
);

test(
  "builder performs a user-visible offline-pack preflight",
  () => {
    assert.match(
      main,
      /checkBuildRequirements/
    );

    assert.match(
      main,
      /BUILD_BLOCKED=YES/
    );

    assert.match(
      main,
      /OFFLINE_BUILD_READY=NO/
    );

    assert.match(
      main,
      /MISSING=/
    );

    assert.match(
      main,
      /if \(preflightFailure == "dependency-missing"\)\s*\{\s*openOfflinePack\(\)/
    );
  }
);

test(
  "offline pack requirements are engine and output aware",
  () => {
    assert.match(
      manager,
      /Android SDK \+ JDK \+ Gradle/
    );

    assert.match(
      manager,
      /Node\.js \+ npm/
    );

    assert.match(
      manager,
      /Python \+ Chaquopy/
    );

    assert.match(
      manager,
      /Expo SDK 54 \+ NDK\/CMake/
    );

    assert.match(
      manager,
      /Windows Native C\/C\+\+ toolchain/
    );

    assert.match(
      manager,
      /Windows Portable EXE Host/
    );
  }
);

test(
  "Expo base toolchain is part of the installable offline pack",
  () => {
    assert.match(
      manager,
      /expoToolchainReady/
    );

    assert.match(
      manager,
      /expo\.ready/
    );

    assert.match(
      manager,
      /expo-toolchain/
    );

    assert.match(
      screen,
      /Expo SDK 54 \/ React Native 0\.81/
    );

    assert.match(
      screen,
      /status\.expoToolchainReady/
    );
  }
);

test(
  "complete target readiness includes Expo Windows native and portable",
  () => {
    assert.match(
      manager,
      /currentAndroidEnginesReady[\s\S]*expoToolchainReady/
    );

    assert.match(
      manager,
      /completeTargetReady[\s\S]{0,180}windowsNativeToolchainReady[\s\S]{0,180}windowsExeReady/
    );
  }
);

test(
  "offline pack returns to the exact screen that opened it",
  () => {
    assert.match(
      main,
      /OFFLINE_PACK_RETURN_ISOLATION_V1_3/
    );

    assert.match(
      main,
      /fun openOfflinePack\(\)[\s\S]*offlinePackReturnScreen\s*=\s*screen[\s\S]*screen\s*=\s*AppScreen\.OFFLINE_PACK/
    );

    assert.match(
      main,
      /AppScreen\.OFFLINE_PACK ->[\s\S]{0,320}screen\s*=\s*offlinePackReturnScreen/
    );
  }
);

test(
  "Terminal workspace is not modified by this feature",
  () => {
    assert.doesNotMatch(
      screen,
      /TerminalWorkspaceScreen/
    );
  }
);


// Execute the actual MainActivity gate with only Android/UI dependencies stubbed.
// This tests classification, diagnostics, navigation and the early return together.
test("Builder preflight keeps settings failures local and routes only missing dependencies", () => {
  const start = main.indexOf("        if (\n            !offlinePackCheck.ready");
  const end = main.indexOf("\n        buildBusy =", start);
  assert.ok(start >= 0 && end > start);
  const gate = main.slice(start, end);
  assert.doesNotMatch(gate, /openWorkspaceScreen/);
  assert.match(gate, /screen\s*=\s*AppScreen\.BUILDER[\s\S]*step\s*=\s*10[\s\S]*openOfflinePack\(\)/);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "appforge-preflight-"));
  try {
    fs.writeFileSync(path.join(dir, "Check.kt"), `
enum class AppScreen { HOME, BUILDER, OFFLINE_PACK }
data class Check(val ready: Boolean, val engine: String, val missing: List<String>)
fun verify(input: Check, classification: String?, redirect: Boolean = false) {
    val offlinePackCheck = input
    val normalRouteReasons = listOf("selected feature requires normal runtime")
    var status = ""
    var progress = 42
    var logs = listOf<String>()
    var preflight = listOf<String>()
    var buildProjectKey: String? = "old"
    var buildId: String? = "old"
    var buildNo: Int? = 42
    var apkUrl: String? = "old"
    var aabUrl: String? = "old"
    var exeUrl: String? = "old"
    var screen = AppScreen.BUILDER
    var step = 10
    var offlinePackReturnScreen = AppScreen.HOME
    var started = false
    fun openOfflinePack() {
        offlinePackReturnScreen = screen
        screen = AppScreen.OFFLINE_PACK
    }
    val startBuild = buildStart@{
${gate}
        started = true
    }
    startBuild()
    if (input.ready) {
        check(started && screen == AppScreen.BUILDER && step == 10)
        check(preflight.isEmpty())
        return
    }
    check(!started && progress == 0)
    check(buildProjectKey == null && buildId == null && buildNo == null)
    check(apkUrl == null && aabUrl == null && exeUrl == null)
    check("BUILD_BLOCKED=YES" in preflight)
    normalRouteReasons.forEach { check("NORMAL_ENGINE_REQUIRED: $it" in preflight) }
    check("PREFLIGHT_FAILURE=$classification" in preflight)
    check("ENGINE=\${input.engine}" in preflight)
    if (input.missing.isNotEmpty()) check(status == input.missing.first())
    input.missing.forEach { check(it in logs) }
    if (redirect) {
        check(screen == AppScreen.OFFLINE_PACK)
        check(offlinePackReturnScreen == AppScreen.BUILDER)
        screen = offlinePackReturnScreen
        check(screen == AppScreen.BUILDER && step == 10)
        check("OFFLINE_BUILD_READY=NO" in preflight)
        input.missing.forEach { check("MISSING=$it" in preflight) }
    } else {
        check(screen == AppScreen.BUILDER && step == 10)
        check("OFFLINE_BUILD_READY=NO" !in preflight)
        check(preflight.none { it.startsWith("MISSING=") })
        input.missing.forEach { check("ERROR=$it" in preflight) }
    }
    when (classification) {
        "settings-invalid" -> check("WEBVIEW_SETTINGS_INVALID=YES" in preflight)
        "fast-output-invalid" -> check("FAST_HTML_APK_SETTINGS_REQUIRED=YES" in preflight)
        "unclassified" -> check("PREFLIGHT_FAILURE_UNCLASSIFIED=YES" in preflight)
    }
}
fun main() {
    verify(Check(false, "webview-settings", listOf("Exact validation error")), "settings-invalid")
    verify(Check(false, "fast-html-apk", listOf("Exact fast output error")), "fast-output-invalid")
    // Settings-engine identity takes priority, even if an error resembles a dependency.
    verify(Check(false, "webview-settings", listOf("Android SDK + JDK + Gradle")), "settings-invalid")
    verify(Check(false, "fast-html-apk", listOf("Node.js + npm")), "fast-output-invalid")
    listOf(
        "webview-static" to "Android SDK + JDK + Gradle",
        "android-gradle" to "Android SDK + JDK + Gradle",
        "node-web" to "Node.js + npm",
        "python-android" to "Python + Chaquopy",
        "expo" to "Expo SDK 54 + NDK/CMake",
        "windows-native" to "Windows Native C/C++ toolchain",
        "universal-cross-platform" to "Windows Portable EXE Host"
    ).forEach { (engine, missing) ->
        verify(Check(false, engine, listOf(missing)), "dependency-missing", true)
    }
    verify(Check(false, "node-web", listOf("Android SDK + JDK + Gradle", "Node.js + npm")), "dependency-missing", true)
    verify(Check(false, "future-engine", listOf("Unknown failure")), "unclassified")
    verify(Check(false, "android-gradle", emptyList()), "unclassified")
    verify(Check(false, "android-gradle", listOf("Android SDK + JDK + Gradle", "Unknown failure")), "unclassified")
    listOf("fast-html-apk", "webview-static", "android-gradle", "windows-native", "node-web", "python-android", "expo", "universal-cross-platform").forEach {
        verify(Check(true, it, emptyList()), null)
    }
    println("preflight scenarios passed")
}
`);
    execFileSync("kotlinc", [path.join(dir, "Check.kt"), "-include-runtime", "-d", path.join(dir, "check.jar")], { timeout: 120000, stdio: "pipe" });
    assert.match(execFileSync("java", ["-jar", path.join(dir, "check.jar")], { encoding: "utf8" }), /preflight scenarios passed/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
