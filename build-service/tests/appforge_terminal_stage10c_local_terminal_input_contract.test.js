import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const panel = () =>
  readFile(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalTerminalPanel.kt",
      import.meta.url
    ),
    "utf8"
  );

test("Stage 10C gives the main Terminal tab terminal-native input without the legacy composer", async () => {
  const source = await panel();

  assert.match(source, /BasicTextField\(/);
  assert.match(source, /ImeAction\.Send/);
  assert.match(source, /submitCommand\(\)/);
  assert.match(source, /Terminale dokun ve yaz/);
  assert.match(source, /ExtraKey\("↵"\)/);

  assert.doesNotMatch(source, /OutlinedTextField\(/);
  assert.doesNotMatch(source, /Text\("Komut"\)/);
  assert.doesNotMatch(source, /Text\("Çalıştır"\)/);

  for (const key of [
    'ExtraKey("ESC")',
    'ExtraKey("TAB")',
    'ExtraKey("CTRL+C")',
    'ExtraKey("↑")',
    'ExtraKey("↓")',
    'ExtraKey("pwd")',
    'ExtraKey("ls")',
    'ExtraKey("clear")'
  ]) {
    assert.ok(source.includes(key), `missing local terminal control ${key}`);
  }
});
