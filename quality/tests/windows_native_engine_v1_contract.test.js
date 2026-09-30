import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../..");
const read = relative => fs.readFileSync(path.join(repo, relative), "utf8");

test("Portable EXE remains accepted while Native EXE is a distinct target", () => {
  const cap = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt");
  assert.match(cap, /WINDOWS_EXE\("Windows Portable EXE"\)/);
  assert.match(cap, /WINDOWS_NATIVE_EXE\("Windows Native EXE"\)/);
  assert.match(cap, /"native-exe"[\s\S]*DeviceArtifactKind\.WINDOWS_NATIVE_EXE/);
  assert.match(cap, /engine\s*=\s*\n\s*"windows-native"[\s\S]*DeviceBuildSupport\.EXPERIMENTAL/);
  assert.match(cap, /engine\s*=\s*\n\s*"webview-static"[\s\S]*DeviceArtifactKind\.WINDOWS_EXE[\s\S]*DeviceBuildSupport\.READY/);
});

test("native toolchain is isolated from Android SDK license flow", () => {
  const installer = read("android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh");
  for (const marker of [
    "cmake",
    "ninja",
    "x86_64-w64-mingw32-g++-posix",
    "gcc-mingw-w64-x86-64-posix",
    "g++-mingw-w64-x86-64-posix",
    "APPFORGE_WINDOWS_NATIVE_DPKG_REPAIR=PASS",
    "PE32+ executable"
  ]) {
    assert.ok(installer.includes(marker), `missing native toolchain marker ${marker}`);
  }

  assert.doesNotMatch(
    installer,
    /\n\s+gcc-mingw-w64-x86-64\s+\\/
  );

  assert.doesNotMatch(
    installer,
    /\n\s+g\+\+-mingw-w64-x86-64\s+\\/
  );
  assert.doesNotMatch(installer, /APPFORGE_ANDROID_SDK_LICENSE/);
  assert.doesNotMatch(installer, /sdkmanager/);
});

test("native builder cross-compiles one Windows x64 PE through CMake", () => {
  const builder = read("android-app/app/src/main/assets/device-build/build-windows-native.sh");
  assert.match(builder, /CMAKE_SYSTEM_NAME Windows/);
  assert.match(builder, /CMAKE_SYSTEM_PROCESSOR x86_64/);
  assert.match(builder, /x86_64-w64-mingw32-gcc-posix/);
  assert.match(builder, /x86_64-w64-mingw32-g\+\+-posix/);
  assert.match(builder, /CMAKE_RUNTIME_OUTPUT_DIRECTORY/);
  assert.match(builder, /PE32\+ executable/);
  assert.match(builder, /APPFORGE_WINDOWS_NATIVE_EXE_AMBIGUOUS/);
});

test("DeviceBuildEngine routes native separately and does not require Portable Host", () => {
  const engine = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt");
  assert.match(engine, /effectiveSourceEngine/);
  assert.match(engine, /DeviceArtifactKind\.WINDOWS_NATIVE_EXE/);
  assert.match(engine, /"windows-native" -> buildWindowsNative/);
  assert.match(engine, /install-windows-native-toolchain\.sh/);
  assert.match(engine, /build-windows-native\.sh/);
  assert.match(engine, /verifyWindowsX64Pe/);
  const hostGuard = engine.slice(engine.indexOf("val wantsWindowsExe"), engine.indexOf("state.preflight.add", engine.indexOf("val wantsWindowsExe")));
  assert.match(hostGuard, /WindowsPortableHostStore/);
  assert.doesNotMatch(hostGuard, /wantsWindowsNativeExe[\s\S]*WindowsPortableHostStore/);
});

test("publisher signing is owner-only and fail-closed", () => {
  const policy = read("android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningPolicy.kt");
  const ui = read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt");
  assert.match(policy, /OwnerAccessPolicy[\s\S]*isActiveOwner/);
  assert.match(policy, /OwnerAccessPolicy[\s\S]*requireActiveOwner/);
  assert.match(policy, /getBoolean\([\s\S]*ENABLED,[\s\S]*false/);
  assert.match(policy, /sertifika sağlayıcısı henüz yapılandırılmadı/);
  assert.doesNotMatch(policy, /BEGIN PRIVATE KEY|BEGIN RSA PRIVATE KEY|\.pfx\"|\.p12\"/);
  assert.match(ui, /windowsSigningAdmin/);
  assert.match(ui, /Windows Yayıncı İmzası • Yönetici/);
});

test("offline pack tracks native toolchain without declaring native product READY", () => {
  const manager = read("android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt");
  const screen = read("android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt");
  assert.match(manager, /windowsNativeToolchainReady/);
  assert.match(manager, /windows-native\.ready/);
  assert.match(manager, /install-windows-native-toolchain\.sh/);
  assert.match(screen, /Windows Native EXE Toolchain/);
  assert.match(screen, /EXPERIMENTAL/);
});

test("native source path canonicalizes Android cache alias before workspace mapping", () => {
  const engine = read(
    "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  );

  const builder = read(
    "android-app/app/src/main/assets/device-build/build-windows-native.sh"
  );

  assert.match(
    engine,
    /val canonicalWorkspace\s*=\s*[\s\S]{0,120}workspace[\s\S]{0,120}\.canonicalFile/
  );

  assert.match(
    engine,
    /sourceRoot[\s\S]{0,180}\.relativeTo\(\s*canonicalWorkspace\s*\)/
  );

  assert.match(
    engine,
    /relative\s*==\s*"source"[\s\S]{0,160}relative\.startsWith\([\s\S]{0,80}"source\/"/
  );

  assert.match(
    builder,
    /APPFORGE_WINDOWS_NATIVE_SOURCE_TRAVERSAL/
  );

  assert.match(
    builder,
    /\*"\/\.\.\/"\*/
  );
});
