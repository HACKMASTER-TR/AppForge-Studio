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

test("Stage 10D makes the complete main terminal surface the keyboard input target", async () => {
  const source = await panel();

  const outputFunction = source.indexOf("private fun TerminalOutput(");
  const nativeInput = source.indexOf("BasicTextField(");

  assert.ok(outputFunction >= 0, "TerminalOutput is missing");
  assert.ok(
    nativeInput > outputFunction,
    "BasicTextField must live inside TerminalOutput, not in a separate composer"
  );

  assert.match(source, /TerminalOutput\([\s\S]*command = command[\s\S]*onCommandChange/);
  assert.match(source, /decorationBox = \{ innerTextField ->[\s\S]*innerTextField\(\)/);
  assert.match(source, /Terminale dokun ve yaz…/);
  assert.match(source, /ImeAction\.Send/);

  assert.doesNotMatch(source, /Terminal satırına dokun/);
  assert.doesNotMatch(source, /OutlinedTextField\(/);
  assert.doesNotMatch(source, /Text\("Komut"\)/);
  assert.doesNotMatch(source, /Text\("Çalıştır"\)/);

  for (const key of [
    'ExtraKey("ESC")',
    'ExtraKey("TAB")',
    'ExtraKey("CTRL+C")',
    'ExtraKey("↵")',
    'ExtraKey("↑")',
    'ExtraKey("↓")',
    'ExtraKey("pwd")',
    'ExtraKey("ls")',
    'ExtraKey("clear")'
  ]) {
    assert.ok(source.includes(key), `missing terminal control ${key}`);
  }
});
