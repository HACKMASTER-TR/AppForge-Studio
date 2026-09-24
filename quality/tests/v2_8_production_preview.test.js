import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const main =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

const backup =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectBackupManager.kt",
    import.meta.url
  );

const draft =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt",
    import.meta.url
  );

test("Live Preview and Production Center screens exist", async () => {
  const text =
    await readFile(
      main,
      "utf8"
    );

  for (const token of [
    "AppScreen.PREVIEW",
    "AppScreen.PRODUCTION",
    "AppPreviewScreen",
    "ProductionCenterScreen",
    "AndroidView",
    "WebView"
  ]) {
    assert.equal(
      text.includes(token),
      true,
      `Missing ${token}`
    );
  }
});

test("preview includes device presets and secure same-host rule", async () => {
  const text =
    await readFile(
      main,
      "utf8"
    );

  assert.equal(
    text.includes(
      '"Telefon"'
    ),
    true
  );

  assert.equal(
    text.includes(
      '"Tablet"'
    ),
    true
  );

  assert.equal(
    text.includes(
      "target.host !="
    ),
    true
  );

  assert.equal(
    text.includes(
      "allowFileAccess"
    ),
    true
  );
});

test("project backup export/import exists and omits secrets", async () => {
  const text =
    await readFile(
      backup,
      "utf8"
    );

  assert.equal(
    text.includes(
      "exportToUri"
    ),
    true
  );

  assert.equal(
    text.includes(
      "importFromUri"
    ),
    true
  );

  assert.equal(
    text.includes(
      "keystore passwords"
    ),
    true
  );

  assert.equal(
    text.includes(
      "250L *"
    ),
    true
  );

  assert.equal(
    text.includes(
      "50L *"
    ),
    true
  );
});

test("auto version code is part of project draft", async () => {
  const text =
    await readFile(
      draft,
      "utf8"
    );

  assert.equal(
    text.includes(
      "autoVersionCode"
    ),
    true
  );
});
