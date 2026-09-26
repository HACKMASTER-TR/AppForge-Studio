import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "..", "..");

const mainPath = path.join(
  repoRoot,
  "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
);
const libraryPath = path.join(
  repoRoot,
  "android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt"
);

test("Studio ready themes stay visible on horizontally scrollable phones", async () => {
  const main = await fs.readFile(mainPath, "utf8");

  for (const label of ["Koyu", "Açık", "OLED"]) {
    assert.ok(main.includes(`Text("${label}")`), label);
  }

  assert.ok(
    main.includes("Modifier.widthIn(") &&
      main.includes("if (formCompact) 92.dp else 108.dp"),
    "theme chips need finite minimum width"
  );
});

test("project save and restore preserves non-secret builder selections", async () => {
  const library = await fs.readFile(libraryPath, "utf8");

  for (const marker of [
    'put("sourceTechnology", d.sourceTechnology)',
    'put("sourceBuildEngine", d.sourceBuildEngine)',
    'put("iconUri", d.iconUri)',
    'put("iconName", d.iconName)',
    'put("appCategory", d.appCategory)',
    'put("signingMode", d.signingMode.name)',
    'put("keystoreUri", d.keystoreUri)',
    'put("keystoreName", d.keystoreName)',
    'put("keyAlias", d.keyAlias)',
    'put("firebaseConfigUri", d.firebaseConfigUri)',
    'put("firebaseConfigName", d.firebaseConfigName)',
    'sourceTechnology =',
    'sourceBuildEngine =',
    'iconUri =',
    'appCategory =',
    'signingMode =',
    'keystoreUri =',
    'firebaseConfigUri ='
  ]) {
    assert.ok(library.includes(marker), marker);
  }

  for (const forbidden of [
    'put("storePassword"',
    'put("keyPassword"',
    'put("buildApiKey"'
  ]) {
    assert.equal(
      library.includes(forbidden),
      false,
      `${forbidden} must remain out of plaintext project JSON`
    );
  }
});

test(
  "OpenDocument selections keep persisted read access",
  async () => {
    const main =
      await fs.readFile(
        mainPath,
        "utf8"
      );

    assert.ok(
      main.includes(
        "takePersistableUriPermission"
      ),
      "OpenDocument selections must keep persisted read access"
    );

    assert.ok(
      main.includes(
        "FLAG_GRANT_READ_URI_PERMISSION"
      ),
      "persisted document access must remain read-only"
    );
  }
);

test(
  "saved native or Expo source remains usable after reopening the project",
  async () => {
    const main =
      await fs.readFile(
        mainPath,
        "utf8"
      );

    const library =
      await fs.readFile(
        libraryPath,
        "utf8"
      );

    assert.ok(
      library.includes(
        'put("sourceUri", d.sourceUri)'
      )
    );

    assert.match(
      library,
      /sourceUri\s*=\s*[\s\S]{0,180}?obj\.optString/
    );

    assert.match(
      library,
      /LOCAL_SOURCE_DIRECTORY_RESTORE_V1/
    );

    assert.match(
      library,
      /storedStartPage[\s\S]{0,340}?it\.exists\(\)/
    );

    assert.match(
      library,
      /restoredStartPage[\s\S]{0,220}?\?: restoredFolder/
    );

    assert.match(
      main,
      /LOCAL_SOURCE_VALIDATION_V2/
    );

    assert.match(
      main,
      /importedSourceValid[\s\S]{0,220}?isDirectory/
    );

    assert.match(
      main,
      /SOURCE_DOCUMENT_PERMISSION_V1/
    );

    assert.match(
      main,
      /SOURCE_PROJECT_STORAGE_ISOLATION_V1/
    );
  }
);

test(
  "advanced app name updates an automatic package suffix",
  async () => {
    const main =
      await fs.readFile(
        mainPath,
        "utf8"
      );

    assert.match(
      main,
      /return "com\.appforgestudio\.\$segment"/
    );

    assert.match(
      main,
      /AUTO_PACKAGE_FROM_APP_NAME_V1/
    );

    assert.match(
      main,
      /autoPackageName\(\s*appName\s*\)/
    );

    assert.match(
      main,
      /oldLegacyAutoPackage/
    );

    assert.doesNotMatch(
      main,
      /if\s*\(\s*autoMode\s*\)\s*\{\s*"com\.appforgestudio\.myapp"/
    );
  }
);
