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

test("Ultimate Linux pins vendor rootfs sources and never trusts mutable downloads", async () => {
  const foundation = await source("terminal/LinuxRuntimeFoundation.kt");

  assert.match(foundation, /https:\/\/cdimage\.ubuntu\.com\/ubuntu-base\/releases\/24\.04\/release/);
  assert.match(foundation, /04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2/);
  assert.match(foundation, /c1e67ef7b17a6300e136118bd1dc04725009cb376c1aad10abcf8cd453628d58/);
  assert.match(foundation, /991520b47f6586f38a78505cf016e300b6191bb8ff86a0723481ec23a37ab7f4/);
  assert.match(foundation, /TRUSTED_ROOTFS_HOSTS/);
  assert.match(foundation, /sourceUri\.scheme\.equals\([\s\S]*"https"/);
});

test("verified rootfs installer enforces HTTPS size digest and archive traversal protections", async () => {
  const installer = await source("terminal/VerifiedLinuxRootfsInstaller.kt");

  assert.match(installer, /HttpsURLConnection/);
  assert.match(installer, /instanceFollowRedirects = false/);
  assert.match(installer, /manifest\.maxArchiveBytes/);
  assert.match(installer, /LinuxRuntimeIntegrity\.matches/);
  assert.match(installer, /GzipCompressorInputStream/);
  assert.match(installer, /TarArchiveInputStream/);
  assert.match(installer, /targetPath\.startsWith/);
  assert.match(installer, /Files\.isSymbolicLink/);
  assert.match(installer, /MAX_TOTAL_EXTRACTED_BYTES/);
  assert.doesNotMatch(installer, /ProcessBuilder/);
  assert.doesNotMatch(installer, /Runtime\.getRuntime\(\)\.exec/);
  assert.doesNotMatch(installer, /curl[\s\S]*\|[\s\S]*(?:sh|bash)/i);
  assert.doesNotMatch(installer, /wget[\s\S]*\|[\s\S]*(?:sh|bash)/i);
});

test("Android Linux manager exposes verified installation without marking missing native engine ready", async () => {
  const [manager, packagedEngine, panel] = await Promise.all([
    source("terminal/AndroidLinuxRuntimeManager.kt"),
    source("terminal/PackagedLinuxEngine.kt"),
    source("terminal/LinuxRuntimePanel.kt")
  ]);

  assert.match(manager, /suspend fun installVerifiedRootfs/);
  assert.match(manager, /VerifiedLinuxRootfsInstaller/);
  assert.match(manager, /PackagedLinuxEngineStatus\.READY/);
  assert.match(packagedEngine, /nativeLibraryDir/);
  assert.match(packagedEngine, /sha256\(safeFile\)/);
  assert.match(panel, /Doğrulanmış Rootfs Kur/);
  assert.match(panel, /Gerçek PTY terminali/);
});
