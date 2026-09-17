import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const source = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/OwnerFilesPanel.kt",
    import.meta.url
  ),
  "utf8"
);

test("AppForge latest APK replaces the previous self-update APK", () => {
  assert.match(source, /isAppForgeLatestApk/);
  assert.match(source, /\.AppForgeStudio-latest\.apk\.new/);
  assert.match(source, /staged\.length\(\) != source\.length\(\)/);
  assert.match(source, /source\.delete\(\)/);
});

test("project artifacts retain collision-safe naming", () => {
  assert.match(
    source,
    /\$\{stem\}_\$index\$ext/
  );
});
