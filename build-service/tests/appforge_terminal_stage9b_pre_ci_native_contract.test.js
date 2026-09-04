import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const repoRoot = new URL("../../", import.meta.url);
const read = (path) => readFile(new URL(path, repoRoot), "utf8");

const appBuild = () => read("android-app/app/build.gradle.kts");
const rootBuild = () => read("android-app/build.gradle.kts");
const workflow = () => read(".github/workflows/android-debug.yml");
const cmake = () => read("android-app/app/src/main/cpp/CMakeLists.txt");
const nativePty = () => read("android-app/app/src/main/cpp/appforge_pty.c");
const terminalSource = (name) =>
  read(`android-app/app/src/main/java/com/appforge/studio/terminal/${name}`);

test("Stage 9B pins the AGP-compatible NDK and existing CMake version", async () => {
  const [root, app] = await Promise.all([rootBuild(), appBuild()]);

  assert.match(root, /com\.android\.application"\) version "9\.1\.1"/);
  assert.match(app, /compileSdk\s*=\s*37/);
  assert.match(app, /minSdk\s*=\s*26/);
  assert.match(app, /targetSdk\s*=\s*37/);
  assert.match(app, /ndkVersion\s*=\s*"28\.2\.13676358"/);
  assert.match(app, /externalNativeBuild/);
  assert.match(app, /version\s*=\s*"3\.22\.1"/);
});

test("Stage 9B Android Debug CI installs the exact native toolchain before Gradle build", async () => {
  const yml = await workflow();
  const installIndex = yml.indexOf("Install deterministic Android native toolchain");
  const buildIndex = yml.indexOf("Build debug APK");

  assert.ok(installIndex >= 0 && buildIndex > installIndex);
  assert.match(yml, /ndk;28\.2\.13676358/);
  assert.match(yml, /cmake;3\.22\.1/);
  assert.match(yml, /test -d "\$ANDROID_HOME\/ndk\/28\.2\.13676358"/);
  assert.match(yml, /test -d "\$ANDROID_HOME\/cmake\/3\.22\.1"/);
  assert.match(yml, /gradle-version:\s*"9\.3\.1"/);
  assert.match(yml, /:app:testDebugUnitTest/);
  assert.match(yml, /:app:assembleDebug/);
});

test("Stage 9B native PTY remains API-26-compatible C17 with warnings treated as errors", async () => {
  const [app, cmakeText, native] = await Promise.all([
    appBuild(),
    cmake(),
    nativePty()
  ]);

  assert.match(app, /minSdk\s*=\s*26/);
  assert.match(cmakeText, /cmake_minimum_required\(VERSION 3\.22\.1\)/);
  assert.match(cmakeText, /project\(appforge_pty C\)/);
  assert.match(cmakeText, /c_std_17/);
  assert.match(cmakeText, /-Wall/);
  assert.match(cmakeText, /-Wextra/);
  assert.match(cmakeText, /-Werror/);
  assert.match(cmakeText, /-fstack-protector-strong/);
  assert.match(native, /#include <pty\.h>/);
  assert.match(native, /forkpty\s*\(/);
  assert.match(native, /execve\s*\(/);
  assert.doesNotMatch(native, /\bsystem\s*\(/);
  assert.doesNotMatch(native, /\bpopen\s*\(/);
});

test("Stage 9B JNI names and Kotlin bridge load the same appforge_pty library", async () => {
  const [native, bridge, session] = await Promise.all([
    nativePty(),
    terminalSource("AppForgePtyBridge.kt"),
    terminalSource("InteractiveLinuxPtySession.kt")
  ]);

  assert.match(bridge, /System\.loadLibrary\(\s*"appforge_pty"\s*\)/s);
  for (const method of ["nativeSpawn", "nativeResize", "nativeWait", "nativeTerminate"]) {
    assert.match(bridge, new RegExp(`external fun ${method}\\(`));
    assert.match(
      native,
      new RegExp(`Java_com_appforge_studio_terminal_AppForgePtyBridge_${method}\\(`)
    );
  }

  assert.match(session, /ParcelFileDescriptor\s*\.adoptFd/);
  assert.match(session, /AppForgePtyBridge\s*\.waitFor/);
  assert.match(session, /AppForgePtyBridge\s*\.terminate/);
});

test("Stage 9B keeps real Android build verification in CI rather than replacing it with static checks", async () => {
  const yml = await workflow();

  assert.match(yml, /:app:testDebugUnitTest/);
  assert.match(yml, /:app:signingReport/);
  assert.match(yml, /:app:assembleDebug/);
  assert.match(yml, /--stacktrace/);
  assert.doesNotMatch(yml, /continue-on-error:\s*true/);
});
