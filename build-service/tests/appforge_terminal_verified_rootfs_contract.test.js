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

  assert.match(foundation, /https:\/\/cdimage\.ubuntu\.com\/ubuntu-base\/releases\/26\.04\/release/);
  assert.match(foundation, /b2b46a37324ea1954e93f293fe6d7c2241daf2fc298c4022e6e4caceeed74cab/);
  assert.match(foundation, /046fcabb7f16f45a80ae11824664f2a07e01386c6fb1ed9dc1e225a66a6553a2/);
  assert.match(foundation, /414e9d5685ff8a6f4497149544e5aa76129f51aa2b97ccd94d845a9803725b46/);
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
