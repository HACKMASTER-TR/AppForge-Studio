import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const androidRoot = new URL(
  "../../android-app/app/",
  import.meta.url
);

const source = (path) =>
  readFile(
    new URL(`src/main/java/com/appforge/studio/${path}`, androidRoot),
    "utf8"
  );

test("Ultimate Linux requires APK-bundled engine and pinned rootfs integrity", async () => {
  const [foundation, manager, packagedEngine, panel] = await Promise.all([
    source("terminal/LinuxRuntimeFoundation.kt"),
    source("terminal/AndroidLinuxRuntimeManager.kt"),
    source("terminal/PackagedLinuxEngine.kt"),
    source("terminal/LinuxRuntimePanel.kt")
  ]);

  assert.match(foundation, /LinuxDistribution/);
  assert.match(foundation, /DEBIAN\("debian", "Debian"\)/);
  assert.match(foundation, /UBUNTU\("ubuntu", "Ubuntu"\)/);
  assert.match(foundation, /LinuxRuntimeIntegrity/);
  assert.match(foundation, /MessageDigest\.getInstance\("SHA-256"\)/);
  assert.match(foundation, /LinuxRuntimeManifestRegistry/);
  assert.match(foundation, /apt-get update/);
  assert.match(foundation, /python3/);
  assert.match(foundation, /nodejs/);
  assert.match(foundation, /default-jdk/);
  assert.match(foundation, /php-cli/);
  assert.match(foundation, /golang-go/);
  assert.match(foundation, /rustc/);
  assert.match(foundation, /build-essential/);

  assert.match(manager, /Build\.SUPPORTED_ABIS/);
  assert.match(manager, /PackagedLinuxEngine/);
  assert.match(manager, /PackagedLinuxEngineStatus\.READY/);
  assert.match(packagedEngine, /nativeLibraryDir/);
  assert.match(packagedEngine, /libproroot|ProrootPinnedRuntime/);
  assert.match(manager, /LinuxRuntimeManifestRegistry\.find/);
  assert.match(manager, /ROOTFS_UNTRUSTED/);
  assert.doesNotMatch(manager, /ProcessBuilder\s*\(/);
  assert.doesNotMatch(manager, /Runtime\.getRuntime\(\)\.exec/);

  assert.match(panel, /Rootless Linux Runtime/);
  assert.match(panel, /APT araç zincirleri/);
  assert.match(panel, /Kurucu hiçbir executable indirmez veya çalıştırmaz/);

  const combined = `${foundation}\n${manager}\n${packagedEngine}\n${panel}`;
  assert.doesNotMatch(combined, /curl\s+[^\n]*\|\s*(?:sh|bash)/i);
  assert.doesNotMatch(combined, /wget\s+[^\n]*\|\s*(?:sh|bash)/i);
});
