import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(new URL(`../../${path}`, import.meta.url), "utf8");

test("Stage 10F keeps terminal extra keys above IME and hides text selection handles", async () => {
  const source = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  );

  assert.doesNotMatch(
    source,
    /import androidx\.compose\.foundation\.layout\.imePadding/
  );
  assert.doesNotMatch(source, /\.imePadding\(\)/);

  assert.match(
    source,
    /val imeInsets\s*=\s*WindowInsets\.ime/
  );

  assert.match(
    source,
    /\.offset\s*\{\s*IntOffset\(\s*x\s*=\s*0,\s*y\s*=\s*-imeInsets\.getBottom\(this\)\s*\)\s*\}/
  );
  assert.match(
    source,
    /Row\([\s\S]*?PtyKey\("ESC"/
  );

  assert.match(
    source,
    /LocalTextSelectionColors\s+provides\s+TextSelectionColors\(/
  );
  assert.match(source, /handleColor\s*=\s*Color\.Transparent/);
  assert.match(source, /backgroundColor\s*=\s*Color\.Transparent/);

  assert.match(source, /cursorBrush\s*=\s*SolidColor\(\s*Color\.Transparent\s*\)/);
  assert.match(source, /LocalPtySessionRegistry\s*\.write\(/);

  assert.doesNotMatch(source, /Terminale dokun ve yaz/);
});
