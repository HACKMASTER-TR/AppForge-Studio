import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const androidRoot = new URL(
  "../../android-app/app/",
  import.meta.url
);

const readAndroid = (path) =>
  readFile(new URL(path, androidRoot), "utf8");

test("AppForge packages a pinned arm64 rootless Linux engine with SHA-256 verification", async () => {
  const [buildFile, manifest, contract] = await Promise.all([
    readAndroid("build.gradle.kts"),
    readAndroid("src/main/AndroidManifest.xml"),
    readAndroid(
      "src/main/java/com/appforge/studio/terminal/ProrootRuntimeContract.kt"
    )
  ]);

  assert.match(buildFile, /appForgeProrootVersion\s*=\s*[\s\S]*"v1\.2\.8"/);
  assert.match(buildFile, /prepareAppForgeProrootRuntime/);
  assert.match(buildFile, /github\.com\/coderredlab\/proroot\/releases\/download/);
  assert.doesNotMatch(buildFile, /\/releases\/latest/);
  assert.match(buildFile, /appForgeSha256/);
  assert.match(buildFile, /expectedSha256/);
  assert.match(buildFile, /expectedSize/);
  assert.match(buildFile, /arm64-v8a/);
  assert.match(buildFile, /jniLibs\.srcDir/);
  assert.match(buildFile, /dependsOn\(\s*prepareAppForgeProrootRuntime/);
  assert.match(manifest, /android:extractNativeLibs="true"/);

  for (const digest of [
    "a4e74d75b66cdc02b080adfe863dbf9951c3b30610d77beddc95488d5fe5de01",
    "8c47a0a7db32d84c179ebb5bf3640f655a3181860ece5886ae44d92858730c34",
    "1c5bc9537a270e8bf8b1c70222813f57b60b828bfb5503ddf8fe37685092de2f",
    "51a0ec5bfed00e572a0de09e22d9057e2befc386b78e426613d3e0ab03f4ecee",
    "06c6624db3bdc45b9ced151cd781df439a37b47731d244b93e9d6a58cd48cde0"
  ]) {
    assert.match(buildFile, new RegExp(digest));
    assert.match(contract, new RegExp(digest));
  }
});

test("packaged Linux launcher accepts only verified nativeLibraryDir assets", async () => {
  const engine = await readAndroid(
    "src/main/java/com/appforge/studio/terminal/PackagedLinuxEngine.kt"
  );

  assert.match(engine, /applicationInfo[\s\S]*nativeLibraryDir/);
  assert.match(engine, /Build\.SUPPORTED_ABIS/);
  assert.match(engine, /ProrootPinnedRuntime\.assets/);
  assert.match(engine, /sha256\(safeFile\)/);
  assert.match(engine, /safeFile\.canExecute\(\)/);
  assert.match(engine, /safeFile\.parentFile\s*!=\s*nativeDirectory/);
  assert.match(engine, /PackagedLinuxEngineStatus\.READY/);
});

test("Linux shell executes only the packaged launcher and clears host linker injection", async () => {
  const shell = await readAndroid(
    "src/main/java/com/appforge/studio/terminal/LinuxShellEngine.kt"
  );

  assert.match(shell, /packagedEngine[\s\S]*requireLauncher/);
  assert.match(shell, /ProcessBuilder\([\s\S]*launcher\.absolutePath/);
  assert.match(shell, /ProrootPinnedRuntime[\s\S]*buildShellArguments/);
  assert.match(shell, /TerminalCommandPolicy\.review/);
  assert.match(shell, /environment\(\)[\s\S]*remove\([\s\S]*"LD_PRELOAD"/);
  assert.match(shell, /environment\(\)[\s\S]*remove\([\s\S]*"LD_LIBRARY_PATH"/);
  assert.doesNotMatch(shell, /https?:\/\//);
});

test("proroot attribution is packaged with the Android app", async () => {
  const notice = await readAndroid(
    "src/main/assets/third_party/proroot-LICENSE.txt"
  );

  assert.match(notice, /Copyright \(c\) 2026 coderred/);
  assert.match(notice, /attribution to "proroot"/);
  assert.match(notice, /complete application package/);
});
