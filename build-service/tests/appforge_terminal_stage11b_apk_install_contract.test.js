import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/WorkspaceFilesPanel.kt",
  import.meta.url
);

const manifestUrl = new URL(
  "../../android-app/app/src/main/AndroidManifest.xml",
  import.meta.url
);

const pathsUrl = new URL(
  "../../android-app/app/src/main/res/xml/apk_file_paths.xml",
  import.meta.url
);

test("Stage 11B opens APK files with Android package installer", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /extension[\s\S]*?equals\([\s\S]*?"apk"[\s\S]*?ignoreCase\s*=\s*true/
  );

  assert.match(
    source,
    /openWorkspaceApkInstaller/
  );

  assert.match(
    source,
    /FileProvider\.getUriForFile/
  );

  assert.match(
    source,
    /Intent\.ACTION_VIEW/
  );

  assert.match(
    source,
    /application\/vnd\.android\.package-archive/
  );

  assert.match(
    source,
    /FLAG_GRANT_READ_URI_PERMISSION/
  );
});

test("Stage 11B stages workspace APK in the FileProvider cache path", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /context\.cacheDir/
  );

  assert.match(
    source,
    /"apk-installer"/
  );

  assert.match(
    source,
    /withContext\(\s*Dispatchers\.IO/
  );

  assert.match(
    source,
    /source\.copyTo/
  );

  assert.match(
    source,
    /destination\.length\(\)\s*==\s*source\.length\(\)/
  );
});

test("Stage 11B handles Android unknown-app permission safely", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /canRequestPackageInstalls/
  );

  assert.match(
    source,
    /ACTION_MANAGE_UNKNOWN_APP_SOURCES/
  );

  assert.match(
    source,
    /package:\$\{context\.packageName\}/
  );
});

test("Stage 11B keeps manifest and FileProvider contract intact", async () => {
  const manifest =
    await readFile(manifestUrl, "utf8");

  const paths =
    await readFile(pathsUrl, "utf8");

  assert.match(
    manifest,
    /android\.permission\.REQUEST_INSTALL_PACKAGES/
  );

  assert.match(
    manifest,
    /androidx\.core\.content\.FileProvider/
  );

  assert.match(
    manifest,
    /\$\{applicationId\}\.fileprovider/
  );

  assert.match(
    paths,
    /<cache-path/
  );

  assert.match(
    paths,
    /path="apk-installer\/"/
  );
});
