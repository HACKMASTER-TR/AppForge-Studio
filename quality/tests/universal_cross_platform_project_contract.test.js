import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../..");
const read = rel => fs.readFileSync(path.join(repo, rel), "utf8");

test("universal project detector wins before nested Android detection", () => {
  const s = read("android-app/app/src/main/java/com/appforge/studio/io/ProjectTechnologyDetector.kt");
  assert.match(s, /appforge\.universal\.json/);
  assert.match(s, /id\s*=\s*\n\s*"appforge-universal"/);
  assert.match(s, /buildEngine\s*=\s*\n\s*"universal-cross-platform"/);
  assert.ok(s.indexOf("appforge.universal.json") < s.indexOf("ProjectSettings/ProjectVersion.txt"));
});

test("universal engine exposes all three outputs", () => {
  const s = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt");
  const start = s.indexOf('"universal-cross-platform"');
  assert.notEqual(start, -1);
  const block = s.slice(start, start + 1600);
  assert.match(block, /DeviceArtifactKind\.APK/);
  assert.match(block, /DeviceArtifactKind\.AAB/);
  assert.match(block, /DeviceArtifactKind\.WINDOWS_EXE/);
  assert.match(block, /DeviceBuildSupport\.READY/);
});

test("universal build keeps native Android and packages static Windows target", () => {
  const s = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt");
  assert.match(s, /private fun buildUniversalProject\(/);
  assert.match(s, /targetRoot\(\s*"android"\s*\)/);
  assert.match(s, /targetRoot\(\s*"windows"\s*\)/);
  assert.match(s, /buildGradleProject\(/);
  assert.match(s, /buildWindowsIfRequested\(/);
  assert.match(s, /val toolchainEngine\s*=/);
  assert.match(s, /install-toolchain\.sh \$\{sh\(toolchainEngine\)\}/);
});

test("universal target paths are traversal guarded", () => {
  const s = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt");
  const start = s.indexOf("private fun buildUniversalProject(");
  const end = s.indexOf("private fun buildStaticWeb(", start);
  const block = s.slice(start, end);
  assert.match(block, /parts\.none/);
  assert.match(block, /it ==\s*\n\s*"\."/);
  assert.match(block, /it ==\s*\n\s*"\.\."/);
  assert.match(block, /canonicalFile/);
});
