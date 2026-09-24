import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const model = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ui/BuildArtifactModel.kt",
    import.meta.url
  ),
  "utf8"
);

const screen = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt",
    import.meta.url
  ),
  "utf8"
);

const home = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt",
    import.meta.url
  ),
  "utf8"
);

const main = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  ),
  "utf8"
);

test(
  "artifact model is platform typed",
  () => {
    for (
      const type of [
        "ANDROID_APK",
        "WINDOWS_PORTABLE_EXE",
        "ANDROID_AAB",
        "WEB_ZIP",
        "SOURCE_ZIP",
      ]
    ) {
      assert.match(
        model,
        new RegExp(type)
      );
    }
  }
);

test(
  "available artifacts come from build metadata",
  () => {
    assert.match(
      model,
      /apkUrl/
    );

    assert.match(
      model,
      /aabUrl/
    );

    assert.match(
      model,
      /exeUrl/
    );

    assert.match(
      model,
      /status\.equals\(\s*"success"/
    );
  }
);

test(
  "successful builds uses build history before local download",
  () => {
    assert.match(
      screen,
      /ProjectLibrary\s*\.\s*loadBuilds/
    );

    assert.match(
      screen,
      /availableArtifacts\(\)/
    );

    assert.match(
      screen,
      /Cihaza kaydedilmedi/
    );

    assert.match(
      screen,
      /Başarılı Derlemeler/
    );
  }
);

test(
  "APK and EXE save to AppForgeStudio",
  () => {
    assert.match(
      screen,
      /PUBLIC_FOLDER\s*=\s*\n?\s*"AppForgeStudio"/
    );

    assert.match(
      screen,
      /WINDOWS_PORTABLE_EXE/
    );

    assert.match(
      screen,
      /ANDROID_APK/
    );
  }
);

test(
  "legacy AppForge Studio files remain readable",
  () => {
    assert.match(
      screen,
      /LEGACY_PUBLIC_FOLDER\s*=\s*\n?\s*"AppForge Studio"/
    );
  }
);

test(
  "download still uses build service download tickets",
  () => {
    assert.match(
      screen,
      /createDownloadTicket/
    );

    assert.match(
      screen,
      /ticketKind/
    );

    assert.match(
      screen,
      /https:\/\//
    );
  }
);

test(
  "share trash and APK install remain available",
  () => {
    assert.match(
      screen,
      /Derlemeyi paylaş/
    );

    assert.match(
      screen,
      /IS_TRASHED/
    );

    assert.match(
      screen,
      /onInstall/
    );
  }
);

test(
  "home exposes unified build history without APK-only wording",
  () => {
    assert.match(
      home,
      /buildFolderOpen/
    );

    assert.match(
      home,
      /DownloadedApkFolderScreen/
    );

    assert.match(
      home,
      /Derlemeler/
    );

    assert.doesNotMatch(
      home,
      /Başarılı APK'lar|Yalnız .*\.apk/
    );
  }
);
