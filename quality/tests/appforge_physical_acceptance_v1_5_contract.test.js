import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = path =>
  fs.readFileSync(
    new URL("../../" + path, import.meta.url),
    "utf8"
  );

const main = read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt");
const fast = read("android-app/app/src/main/assets/device-build/FastActivity.java");
const iconProcessor = read("android-app/app/src/main/java/com/appforge/studio/io/AppIconProcessor.kt");
const deviceIcon = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceProjectIcon.kt");
const engine = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt");

test("generated Web wrappers configure system bars after content attachment", () => {
  assert.match(fast, /APPFORGE_ANDROID_WINDOW_LIFECYCLE_V1_5/);
  const a = fast.indexOf("protected void onCreate");
  const b = fast.indexOf("private void requestConfiguredPermissions", a);
  const block = fast.slice(a, b);
  assert.ok(block.indexOf("setContentView(root)") >= 0);
  assert.ok(block.indexOf("configureWindow()") > block.indexOf("setContentView(root)"));
  assert.ok(block.indexOf("applyAppForgeSystemBarInsets()") > block.indexOf("configureWindow()"));
});

test("Keystore backup picker and Builder vault are wired", () => {
  assert.match(main, /KEYSTORE_DEVICE_BACKUP_PICKER_V1_5/);
  assert.match(main, /OpenMultipleDocuments/);
  assert.match(main, /onFindBackups/);
  assert.match(main, /BUILDER_MANAGED_KEYSTORE_V1_5/);
  assert.match(main, /KASADAN SEÇ/);
  assert.match(main, /OLUŞTUR \/ YÖNET/);
  assert.match(main, /Uri\.fromFile/);
  assert.match(engine, /MANAGED_KEYSTORE_BUILD_SOURCE_V1_5/);
});

test("Icon editor and adaptive launcher pipeline are real", () => {
  assert.match(main, /APPFORGE_ICON_EDITOR_V1_5/);
  assert.match(main, /pendingIconZoom/);
  assert.match(main, /"Doldur"/);
  assert.match(main, /"Sığdır"/);
  assert.match(main, /DOSYAYI DEĞİŞTİR/);
  assert.match(iconProcessor, /APPFORGE_ICON_EDITOR_V1_5/);
  assert.match(iconProcessor, /fillCanvas/);
  assert.match(iconProcessor, /zoom\.coerceIn/);
  assert.match(deviceIcon, /APPFORGE_LAUNCHER_FULL_BLEED_V1_5/);
  assert.match(deviceIcon, /mipmap-anydpi-v26/);
  assert.match(deviceIcon, /adaptive-icon/);
});

test("Build result shows app icon and folder action", () => {
  assert.match(main, /BUILD_RESULT_APP_ICON_V1_5/);
  assert.match(main, /BuildArtifactIcon/);
  assert.match(main, /BUILD_RESULT_SHOW_FOLDER_V1_5/);
  assert.match(main, /KLASÖRDE GÖSTER/);
  assert.match(main, /primary:Download\/AppForgeStudio/);
});

test(
  "normal generated Web wrappers restore visible system bars across window lifecycle",
  () => {
    assert.match(
      fast,
      /APPFORGE_SYSTEM_BARS_PHYSICAL_V1_6/
    );

    assert.match(
      fast,
      /WindowInsets\.Type\.statusBars\(\)/
    );

    assert.match(
      fast,
      /WindowInsets\.Type\.navigationBars\(\)/
    );

    assert.match(
      fast,
      /protected void onResume\(\)/
    );

    assert.match(
      fast,
      /public void onWindowFocusChanged/
    );

    assert.match(
      fast,
      /this::applyAppForgeSystemBarMode/
    );

    assert.match(
      fast,
      /clearFlags\([\s\S]*FLAG_FULLSCREEN/
    );
  }
);

test(
  "generated Web wrappers explicitly control system-bar icon contrast",
  () => {
    assert.match(
      fast,
      /setSystemBarsAppearance/
    );

    assert.match(
      fast,
      /APPEARANCE_LIGHT_STATUS_BARS/
    );

    assert.match(
      fast,
      /APPEARANCE_LIGHT_NAVIGATION_BARS/
    );

    assert.match(
      fast,
      /Color\.luminance/
    );
  }
);
