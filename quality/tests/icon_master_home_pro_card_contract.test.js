import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
const read = relative => fs.readFileSync(new URL("../../" + relative, import.meta.url), "utf8");

const prepared = read("android-app/app/src/main/java/com/appforge/studio/io/AppIconProcessor.kt");
const home = read("android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt");
const android = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceProjectIcon.kt");
const windows = read("android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableExePackager.kt");

test("same full-width prepared master feeds APK and EXE outputs", () => {
  assert.match(prepared, /SAFE_CONTENT_SIZE = OUTPUT_SIZE/);
  assert.match(prepared, /masterBackgroundColor\(decoded, backgroundColor\)/);
  assert.match(prepared, /corners\.map\(channel\)\.sorted\(\)/);
  assert.match(prepared, /Color\.alpha\(it\) >= 240/);
  assert.match(prepared, /Uri\.fromFile\(/);
  assert.match(android, /draft\.iconUri/);
  assert.match(windows, /WindowsPeIconPatcher\.install\(appContext, draft\.iconUri!!, part\)/);
});

test("bottom Home Pro card is absent while plan chip and existing Pro navigation remain", () => {
  assert.doesNotMatch(home, /ModernProCard\s*\(/);
  assert.match(home, /ModernHomeHero\(/);
  assert.match(home, /proUnlocked = proUnlocked/);
  assert.match(home, /onOpenPro: \(\) -> Unit/);
  assert.match(home, /Text\("Ayarlar"\)/);
});
