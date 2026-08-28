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

test("saved document URIs and source analysis survive reopening projects", async () => {
  const main = await fs.readFile(mainPath, "utf8");

  assert.ok(
    main.includes("takePersistableUriPermission"),
    "OpenDocument selections must keep persisted read access"
  );
  assert.ok(
    main.includes("SourceCapabilityAnalyzer") &&
      main.includes("File(folderPath)"),
    "restored source folder must be re-analysed"
  );
  assert.ok(
    main.includes("statusMessage = status") &&
      main.includes("NoteCard(\n                    statusMessage"),
    "Step 9 must show save/update feedback"
  );
});
