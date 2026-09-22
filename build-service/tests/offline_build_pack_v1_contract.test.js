import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const repo =
  path.resolve(
    here,
    "../.."
  );

const read = relative =>
  fs.readFileSync(
    path.join(
      repo,
      relative
    ),
    "utf8"
  );

test(
  "offline pack remains isolated from Terminal Linux",
  () => {
    const manager =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
      );

    assert.match(
      manager,
      /DeviceBuildRuntimeV3/
    );

    assert.match(
      manager,
      /runtimeBaseDirectory/
    );

    assert.doesNotMatch(
      manager,
      /AndroidLinuxRuntimeManager/
    );
  }
);

test(
  "one-click pack prepares current Android engines",
  () => {
    const manager =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
      );

    assert.match(
      manager,
      /webview-static/
    );

    assert.match(
      manager,
      /node-web/
    );

    assert.match(
      manager,
      /python-android/
    );

    assert.match(
      manager,
      /prepare-offline-pack\.sh/
    );
  }
);

test(
  "portable EXE cannot be falsely marked ready",
  () => {
    const manager =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
      );

    const capabilities =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
      );

    assert.match(
      manager,
      /windowsExeReady\s*=\s*false/
    );

    assert.match(
      capabilities,
      /engine\s*=\s*"windows-web"[\s\S]*DeviceBuildSupport\.PLANNED/
    );
  }
);

test(
  "offline pack prewarms Android and Python Gradle caches",
  () => {
    const script =
      read(
        "android-app/app/src/main/assets/device-build/prepare-offline-pack.sh"
      );

    assert.match(
      script,
      /GRADLE_USER_HOME/
    );

    assert.match(
      script,
      /android-37\.0/
    );

    assert.match(
      script,
      /com\.android\.application/
    );

    assert.match(
      script,
      /python-template/
    );

    assert.match(
      script,
      /:app:assembleDebug/
    );
  }
);

test(
  "settings exposes the offline pack screen",
  () => {
    const settings =
      read(
        "android-app/app/src/main/java/com/appforge/studio/AppForgeSettingsScreens.kt"
      );

    const main =
      read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    const screen =
      read(
        "android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt"
      );

    assert.match(
      settings,
      /Tam Çevrimdışı Derleme Paketi/
    );

    assert.match(
      main,
      /AppScreen\.OFFLINE_PACK/
    );

    assert.match(
      screen,
      /HAZIR BİLEŞENLERİ İNDİR VE KUR/
    );
  }
);
