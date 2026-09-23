import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = async (path) =>
  readFile(new URL(`../../${path}`, import.meta.url), "utf8");

const mainPath = "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt";
const storePath = "android-app/app/src/main/java/com/appforge/studio/terminal/OwnerArtifactReferenceStore.kt";
const ownerPath = "android-app/app/src/main/java/com/appforge/studio/terminal/OwnerFilesPanel.kt";
const servicePath = "android-app/app/src/main/java/com/appforge/studio/terminal/WorkspaceFileService.kt";
const panelPath = "android-app/app/src/main/java/com/appforge/studio/terminal/WorkspaceFilesPanel.kt";

test("device-local owner EXE uses a reference before legacy copy fallback", async () => {
  const main = await read(mainPath);
  const ownerDownload = main.indexOf("private fun downloadArtifactToOwnerVault");
  assert.notEqual(ownerDownload, -1);
  const localStart = main.indexOf(
    'if (url.startsWith("file://", ignoreCase = true))',
    ownerDownload
  );
  const httpsStart = main.indexOf(
    "require(\n        url.startsWith(",
    localStart
  );
  assert.ok(localStart > ownerDownload);
  assert.ok(httpsStart > localStart);
  const localBranch = main.slice(localStart, httpsStart);
  assert.match(localBranch, /fileName\.endsWith\([\s\S]*?"\.exe"/);
  assert.match(localBranch, /OwnerArtifactReferenceStore[\s\S]*?publishLocalExeReference/);
  assert.match(localBranch, /return copyArtifactToOwnerVault/);
  assert.ok(
    localBranch.indexOf("publishLocalExeReference") <
      localBranch.indexOf("copyArtifactToOwnerVault")
  );
});

test("owner EXE references are owner-gated and constrained to local artifact root", async () => {
  const store = await read(storePath);
  assert.match(store, /OwnerAccessPolicy\.requireActiveOwner/);
  assert.match(store, /context\.filesDir,\s*"device-build\/artifacts"/);
  assert.match(store, /buildDirectory\.parentFile == root/);
  assert.match(store, /buildDirectory\.name\.startsWith\("local-"\)/);
  assert.match(store, /appforge-owner-artifact-refs-v1/);
  assert.match(store, /artifactRelativePath/);
  assert.doesNotMatch(store, /createSymbolicLink|createLink|Os\.link|symlink\s*\(/);
});

test("legacy owner EXE is removed only after exact hash match and reference write", async () => {
  const store = await read(storePath);
  const adopt = store.slice(
    store.indexOf("fun adoptDuplicateExeCopies"),
    store.indexOf("fun list(context", store.indexOf("fun adoptDuplicateExeCopies"))
  );
  assert.match(adopt, /ownerExe\.length\(\)/);
  assert.match(adopt, /sha256\(ownerExe\)/);
  assert.match(adopt, /sha256\(it\) == ownerDigest/);
  assert.match(adopt, /writeReference\(context, source, safeName, ownerDigest\)/);
  assert.match(adopt, /if \(ownerExe\.delete\(\)\)/);
  assert.ok(adopt.indexOf("writeReference") < adopt.indexOf("ownerExe.delete"));
});

test("AppForge Files overlays owner EXE references without escaping workspace semantics", async () => {
  const [owner, service, panel] = await Promise.all([
    read(ownerPath),
    read(servicePath),
    read(panelPath),
  ]);
  assert.match(owner, /OwnerArtifactReferenceStore[\s\S]*?\.list/);
  assert.match(owner, /directory\.canonicalFile ==[\s\S]*?apkRoot\.canonicalFile/);
  assert.match(owner, /referenceId =\s*reference\.id/);
  assert.match(service, /val referenceId: String\? = null/);
  assert.match(service, /additionalEntries: List<WorkspaceEntry>/);
  assert.match(service, /safeFile\.parentFile ==\s*safeDirectory/);
  assert.match(panel, /onDeleteReference: \(WorkspaceEntry\) -> Boolean/);
  assert.match(panel, /asıl build çıktısı korundu/);
});
