import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test("PTY software keyboard keeps IME composition state for Backspace", async () => {
  const source = await readFile(sourceUrl, "utf8");

  assert.match(
    source,
    /import androidx\.compose\.ui\.text\.input\.TextFieldValue/
  );

  assert.match(
    source,
    /var imeValue[\s\S]*?mutableStateOf\(\s*TextFieldValue\(/
  );

  assert.match(
    source,
    /value\s*=\s*imeValue/
  );

  assert.match(
    source,
    /previous\s*=\s*imeValue\.text/
  );

  assert.match(
    source,
    /next\s*=\s*next\.text/
  );

  assert.match(
    source,
    /imeValue\s*=\s*localPtyImeValue\(\s*next\s*\)/
  );

  assert.match(
    source,
    /autoCorrectEnabled\s*=\s*false/
  );

  assert.doesNotMatch(
    source,
    /var imeShadow\b/
  );
});

test("PTY IME normalization preserves selection and composition when text is unchanged", async () => {
  const source = await readFile(sourceUrl, "utf8");

  assert.match(
    source,
    /private fun localPtyImeValue\(\s*next:\s*TextFieldValue\s*\):\s*TextFieldValue/
  );

  assert.match(
    source,
    /if\s*\(\s*shadow\s*==\s*next\.text\s*\)\s*\{\s*return next\s*\}/
  );

  assert.match(
    source,
    /TextRange\(\s*shadow\.length\s*\)/
  );
});

test("PTY sentinel still maps an empty software-keyboard edit to DEL", async () => {
  const source = await readFile(sourceUrl, "utf8");

  assert.match(
    source,
    /previous\s*==\s*LOCAL_PTY_IME_SENTINEL\s*&&\s*next\.isEmpty\(\)[\s\S]*?return\s+"\\u007f"/
  );
});
