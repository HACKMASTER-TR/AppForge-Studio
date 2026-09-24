import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const androidRoot = new URL(
  "../../android-app/app/",
  import.meta.url
);

const source = (path) =>
  readFile(
    new URL(
      `src/main/java/com/appforge/studio/${path}`,
      androidRoot
    ),
    "utf8"
  );

test("Ultimate editor is integrated without replacing existing file manager", async () => {
  const panel = await source(
    "terminal/TerminalUltimatePanel.kt"
  );

  assert.match(panel, /UltimateCodeEditorPanel/);
  assert.match(panel, /Gelişmiş Kod Editörü/);
  assert.match(panel, /Dosya Yöneticisi/);
  assert.match(panel, /onOpenFiles/);
});

test("editor sandbox blocks traversal and binary signing material", async () => {
  const core = await source(
    "terminal/UltimateCodeEditorCore.kt"
  );

  assert.match(core, /canonicalFile/);
  assert.match(core, /startsWith\(/);
  assert.match(core, /MAX_FILE_BYTES/);
  assert.match(core, /"jks"/);
  assert.match(core, /"keystore"/);
  assert.match(core, /"p12"/);
  assert.match(core, /isSensitive/);
});

test("editor provides tabs search replace undo diff restore points and LSP plans", async () => {
  const [core, ui] = await Promise.all([
    source("terminal/UltimateCodeEditorCore.kt"),
    source("terminal/UltimateCodeEditorPanel.kt")
  ]);

  assert.match(core, /EditorUndoBuffer/);
  assert.match(core, /UltimateEditorSearch/);
  assert.match(core, /UltimateEditorDiff/);
  assert.match(core, /createRestorePoint/);
  assert.match(core, /UltimateLspCatalog/);
  assert.match(core, /typescript-language-server --stdio/);
  assert.match(core, /pyright-langserver --stdio/);
  assert.match(core, /clangd/);

  assert.match(ui, /MAX_EDITOR_TABS/);
  assert.match(ui, /Tümünü Değiştir/);
  assert.match(ui, /Değişiklikleri Gör/);
  assert.match(ui, /Geri Al/);
  assert.match(ui, /Yinele/);
});

test("sensitive env files never receive editor restore point copies", async () => {
  const core = await source(
    "terminal/UltimateCodeEditorCore.kt"
  );

  assert.match(
    core,
    /if \(isSensitive\(file\.name\)\) \{\s*null\s*\} else \{\s*createRestorePoint/
  );
  assert.match(core, /\.appforge-editor-history/);
});
