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
  "offline prewarm reports the real Gradle cause instead of stacktrace noise",
  () => {
    const script =
      read(
        "android-app/app/src/main/assets/device-build/prepare-offline-pack.sh"
      );

    assert.doesNotMatch(
      script,
      /--stacktrace/
    );

    assert.match(
      script,
      /--console=plain/
    );

    assert.match(
      script,
      /APPFORGE_OFFLINE_PREWARM_FAILED:ANDROID/
    );

    assert.match(
      script,
      /APPFORGE_OFFLINE_PREWARM_FAILED:PYTHON/
    );

    assert.match(
      script,
      /sed -n '\/FAILURE:\/,\$p'/
    );
  }
);


test(
  "Android SDK license requires explicit app consent",
  () => {
    const installer =
      read(
        "android-app/app/src/main/assets/device-build/install-toolchain.sh"
      );

    const manager =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
      );

    const screen =
      read(
        "android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt"
      );

    assert.match(
      manager,
      /androidSdkLicenseAccepted/
    );

    assert.match(
      manager,
      /APPFORGE_ANDROID_SDK_LICENSE_ACCEPTED=1/
    );

    assert.match(
      screen,
      /Android SDK Lisansı/
    );

    assert.match(
      screen,
      /KABUL ET VE KUR/
    );

    assert.match(
      screen,
      /android_sdk_license_2026_04_28/
    );

    assert.match(
      screen,
      /developer\.android\.com/
    );

    assert.match(
      installer,
      /APPFORGE_ANDROID_SDK_LICENSE_REQUIRED/
    );

    assert.match(
      installer,
      /commandlinetools-linux-\$\{CMDLINE_TOOLS_VERSION\}_latest\.zip/
    );

    assert.match(
      installer,
      /4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583/
    );

    assert.match(
      installer,
      /platforms;android-37\.0/
    );

    assert.match(
      installer,
      /SDK_LICENSE_MARKER/
    );

    assert.doesNotMatch(
      installer,
      /echo\s+[0-9a-f]{40}\s*>\s*["']?\$SDK\/licenses/
    );
  }
);


test(
  "device Python and Chaquopy use the same Python 3.12 runtime",
  () => {
    const template =
      read(
        "android-app/app/src/main/assets/device-build/python-template/app/build.gradle.kts"
      );

    const engine =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
      );

    const installer =
      read(
        "android-app/app/src/main/assets/device-build/install-toolchain.sh"
      );

    assert.match(
      template,
      /version = "3\.12"/
    );

    assert.match(
      template,
      /buildPython\(\s*"\/usr\/bin\/python3\.12"\s*\)/
    );

    assert.doesNotMatch(
      template,
      /version = "3\.11"/
    );

    assert.match(
      engine,
      /version = "3\.12"/
    );

    assert.match(
      engine,
      /buildPython\("\/usr\/bin\/python3\.12"\)/
    );

    assert.match(
      installer,
      /APPFORGE_PYTHON_BUILD_RUNTIME=3\.12/
    );

    assert.match(
      installer,
      /sys\.version_info\[:2\] == \(3, 12\)/
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
