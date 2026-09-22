import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../..");
const read = relative => fs.readFileSync(path.join(repo, relative), "utf8");

function engineBlock(source, engine, nextEngine) {
  const start = source.indexOf(`engine =\n                    "${engine}"`);
  assert.notEqual(start, -1, `missing engine ${engine}`);
  const end = nextEngine
    ? source.indexOf(`engine =\n                    "${nextEngine}"`, start + 1)
    : source.length;
  return source.slice(start, end < 0 ? source.length : end);
}

test("accepted web engines expose device-local Windows EXE", () => {
  const capabilities = read(
    "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
  );
  const staticWeb = engineBlock(capabilities, "webview-static", "node-web");
  const nodeWeb = engineBlock(capabilities, "node-web", "android-gradle");
  for (const block of [staticWeb, nodeWeb]) {
    assert.match(block, /DeviceArtifactKind\.WINDOWS_EXE/);
    assert.match(block, /DeviceBuildSupport\.READY/);
  }
  const androidNative = engineBlock(capabilities, "android-gradle", "python-android");
  const python = engineBlock(capabilities, "python-android", "android-ndk");
  assert.doesNotMatch(androidNative, /DeviceArtifactKind\.WINDOWS_EXE/);
  assert.doesNotMatch(python, /DeviceArtifactKind\.WINDOWS_EXE/);
});

test("normal DeviceBuildEngine carries and publishes EXE artifacts", () => {
  const engine = read(
    "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  );
  assert.match(engine, /val exe: File\?/);
  assert.match(engine, /@Volatile var exe: File\? = null/);
  assert.match(engine, /"windows-exe"[\s\S]*state\.exe/);
  assert.match(engine, /buildWindowsIfRequested/);
  assert.match(engine, /WindowsPortableExePackager[\s\S]*packageProject/);
  assert.match(engine, /WindowsPortableHostStore[\s\S]*isInstalled/);
  assert.match(engine, /state\.exe\s*=\s*target/);
});

test("node web output is reused for Windows and Android targets", () => {
  const engine = read(
    "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  );
  const start = engine.indexOf("private fun buildNodeWeb(");
  const end = engine.indexOf("private fun resolveStaticWebRoot(", start);
  assert.notEqual(start, -1);
  assert.notEqual(end, -1);
  const block = engine.slice(start, end);
  assert.match(block, /build-node\.sh/);
  assert.match(block, /buildWindowsIfRequested/);
  assert.match(block, /wantsAndroidOutputs/);
  assert.match(block, /buildWebWrapper/);
});

test("project packager supports LOCAL and HTTPS URL manifests", () => {
  const packager = read(
    "android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableExePackager.kt"
  );
  assert.match(packager, /suspend fun packageProject/);
  assert.match(packager, /SourceMode\.LOCAL/);
  assert.match(packager, /Windows URL modu HTTPS gerektirir/);
  assert.match(packager, /createProjectManifest/);
  assert.match(packager, /"webView"/);
  assert.match(packager, /"nativeBridge"/);
  assert.match(packager, /"project\.zip"/);
  assert.match(packager, /JSONObject\.NULL/);
});

test("BuildApiClient exposes local EXE through existing Studio UI contract", () => {
  const api = read(
    "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  );
  const main = read(
    "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  );
  assert.match(api, /exeAvailable = state\.exe\?\.isFile == true/);
  assert.doesNotMatch(api, /Windows EXE cihaz-build geçişinde devre dışı/);
  assert.match(main, /createDownloadTicket\([\s\S]*"exe"/);
});

test("complete offline pack stays gated until a real normal project EXE passes", () => {
  const offline = read(
    "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
  );
  const capabilities = read(
    "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
  );
  assert.match(offline, /windowsExeReady\s*=\s*false/);
  assert.match(
    capabilities,
    /engine\s*=\s*"windows-web"[\s\S]*DeviceBuildSupport\.PLANNED/
  );
});


test(
  "Gradle output selection does not collide with produced artifact file list",
  () => {
    const engine =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
      );

    const start =
      engine.indexOf(
        "private fun buildGradleProject("
      );

    const end =
      engine.indexOf(
        "private fun copyKeystore(",
        start
      );

    assert.notEqual(start, -1);
    assert.notEqual(end, -1);

    const block =
      engine.slice(
        start,
        end
      );

    assert.match(
      block,
      /val requestedArtifacts\s*=\s*[\s\S]*requestedOutputs/
    );

    assert.match(
      block,
      /val artifactFiles\s*=\s*project\.walkTopDown/
    );

    assert.match(
      block,
      /DeviceArtifactKind\.APK in requestedArtifacts/
    );

    assert.match(
      block,
      /DeviceArtifactKind\.AAB in requestedArtifacts/
    );

    assert.match(
      block,
      /artifactFiles\.filter/
    );

    assert.doesNotMatch(
      block,
      /\bval outputs\b/
    );
  }
);
