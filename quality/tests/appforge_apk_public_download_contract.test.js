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
  "Successful Builds reads AppForgeStudio with legacy compatibility",
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
      /PUBLIC_FOLDER\s*=\s*\n?\s*"AppForgeStudio"/
    );

    assert.match(
      folder,
      /LEGACY_PUBLIC_FOLDER\s*=\s*\n?\s*"AppForge Studio"/
    );

    assert.match(folder, /ANDROID_APK/);
    assert.match(folder, /ANDROID_AAB/);
    assert.match(folder, /WINDOWS_PORTABLE_EXE/);
    assert.doesNotMatch(
      folder,
      /endsWith\("\.apk",true\)/
    );
  }
);


test(
  "AAB and EXE owner downloads also publish to public Downloads",
  async () => {
    const main = await read(
      "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
    );

    const aabButton =
      main.indexOf(
        "\"AAB'Yİ İNDİR\""
      );

    const exeButton =
      main.indexOf(
        "\"WINDOWS EXE'Yİ İNDİR\""
      );

    assert.notEqual(aabButton, -1);
    assert.notEqual(exeButton, -1);

    const aabStart =
      main.lastIndexOf(
        "if (\n            aabUrl != null",
        aabButton
      );

    const exeStart =
      main.lastIndexOf(
        "if (\n            exeUrl != null",
        exeButton
      );

    const messageStart =
      main.indexOf(
        "if (\n            downloadMessage.isNotBlank()",
        exeButton
      );

    assert.notEqual(aabStart, -1);
    assert.notEqual(exeStart, -1);
    assert.notEqual(messageStart, -1);

    const aabBlock =
      main.slice(
        aabStart,
        exeStart
      );

    const exeBlock =
      main.slice(
        exeStart,
        messageStart
      );

    for (
      const [name, block] of [
        ["AAB", aabBlock],
        ["EXE", exeBlock]
      ]
    ) {
      assert.match(
        block,
        /OwnerAccessPolicy/
      );

      assert.match(
        block,
        /downloadArtifactToDownloads/
      );

      assert.match(
        block,
        /downloadArtifactToOwnerVault/
      );

      assert.match(
        block,
        /Downloads\/AppForgeStudio ve/
      );

      assert.ok(
        block.indexOf(
          "downloadArtifactToDownloads"
        ) <
          block.indexOf(
            "downloadArtifactToOwnerVault"
          ),
        `${name} must publish public copy before optional owner copy`
      );

      if (
        name === "AAB"
      ) {
        assert.doesNotMatch(
          block,
          /DownloadManager\.Request/
        );

        assert.match(
          block,
          /aabSaveLauncher\.launch/
        );
      }
    }
  }
);
