import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const apkCommand = await readFile(
  new URL(
    "../../android-app/app/src/main/assets/terminal/appforge-apk",
    import.meta.url
  ),
  "utf8"
);

const bootstrap = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/TerminalStandaloneDeveloperBootstrap.kt",
    import.meta.url
  ),
  "utf8"
);

test("AppForge APK shortcut resolves the successful artifact for exact HEAD", () => {
  assert.match(
    apkCommand,
    /actions\/workflows\/android-debug\.yml\/runs\?branch=\$branch&status=success/
  );

  assert.match(
    apkCommand,
    /select\(\.head_sha == \$head\)/
  );

  assert.match(
    apkCommand,
    /AppForgeStudio-debug-/
  );

  assert.match(
    apkCommand,
    /actions\/artifacts\/\$artifact_id\/zip/
  );

  assert.match(
    apkCommand,
    /AppForgeStudio-latest\.apk/
  );
});

test("AppForge Studio never silently falls back to a stale Release APK", () => {
  assert.match(
    apkCommand,
    /Eski Release APK'sı indirilmeyecek/
  );
});

test("standalone bootstrap packages appforge-apk", () => {
  assert.match(
    bootstrap,
    /"appforge-apk"/
  );
});
