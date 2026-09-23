import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = async (path) =>
  readFile(
    new URL(`../../${path}`, import.meta.url),
    "utf8"
  );

test(
  "build screen shares downloaded APK through secure content URI",
  async () => {
    const main = await read(
      "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
    );

    assert.match(main, /private fun shareCachedApk/);
    assert.match(main, /FileProvider\.getUriForFile/);
    assert.match(main, /Intent\.ACTION_SEND/);
    assert.match(main, /Intent\.EXTRA_STREAM/);
    assert.match(
      main,
      /application\/vnd\.android\.package-archive/
    );
    assert.match(main, /APK'YI PAYLAŞ/);
  }
);

test(
  "Successful Builds preserves APK install and supports artifact sharing",
  async () => {
    const folder = await read(
      "android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt"
    );

    assert.match(folder, /shareArtifact/);
    assert.match(folder, /Intent\.ACTION_SEND/);
    assert.match(folder, /Intent\.EXTRA_STREAM/);
    assert.match(folder, /artifact\.type\s*\.\s*mimeType/);
    assert.match(folder, /BuildArtifactType\.ANDROID_APK/);
    assert.match(folder, /onInstall/);
    assert.match(folder, /Text\(\s*"Kur"\s*\)/);
    assert.match(folder, /Text\(\s*"Paylaş"\s*\)/);
  }
);
