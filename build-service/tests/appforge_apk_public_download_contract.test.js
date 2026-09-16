import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = async (path) =>
  readFile(
    new URL(
      `../../${path}`,
      import.meta.url
    ),
    "utf8"
  );

test(
  "APK download always publishes to public Downloads",
  async () => {
    const main = await read(
      "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
    );

    assert.match(
      main,
      /val publishedToDownloads\s*=\s*publishApkToDownloads/
    );

    assert.match(
      main,
      /val isOwner\s*=\s*OwnerAccessPolicy[\s\S]{0,180}?isActiveOwner/
    );

    assert.match(
      main,
      /if\s*\(\s*isOwner\s*\)[\s\S]{0,900}?copyArtifactToOwnerVault/
    );

    assert.match(
      main,
      /"OWNER_AND_DOWNLOADS"/
    );

    assert.doesNotMatch(
      main,
      /runCatching\s*\{[\s\S]{0,500}?publishApkToDownloads/
    );
  }
);

test(
  "public APK publication verifies persisted byte size",
  async () => {
    const main = await read(
      "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
    );

    assert.match(
      main,
      /val publishedSize\s*=[\s\S]{0,900}?MediaColumns[\s\S]{0,80}?SIZE/
    );

    assert.match(
      main,
      /publishedSize\s*==\s*sourceFile\.length\(\)/
    );
  }
);

test(
  "Successful APKs reads the same public Downloads folder",
  async () => {
    const folder = await read(
      "android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt"
    );

    assert.match(
      folder,
      /MediaStore\.Downloads\.EXTERNAL_CONTENT_URI/
    );

    assert.match(
      folder,
      /Environment\.DIRECTORY_DOWNLOADS/
    );

    assert.match(
      folder,
      /AppForge Studio/
    );

    assert.ok(folder.includes('endsWith(".apk",true)'));

  }
);
