import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test(
  "PTY keeps TextFieldValue IME composition architecture",
  async () => {
    const source =
      await readFile(sourceUrl, "utf8");

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
      /autoCorrectEnabled\s*=\s*false/
    );

    assert.doesNotMatch(
      source,
      /var imeShadow\b/
    );
  }
);

test(
  "normal typing and Backspace still use localPtyImeValue",
  async () => {
    const source =
      await readFile(sourceUrl, "utf8");

    /*
     * Stage 11O:
     *
     * protected bulk paste -> reset sentinel
     * normal edit/backspace -> localPtyImeValue(next)
     */
    assert.match(
      source,
      /imeValue\s*=\s*if\s*\(\s*dispatch\.resetIme\s*\)[\s\S]*?else\s*\{[\s\S]*?localPtyImeValue\(\s*next\s*\)/
    );

    assert.match(
      source,
      /private fun localPtyImeValue\(\s*next:\s*TextFieldValue\s*\):\s*TextFieldValue/
    );

    assert.match(
      source,
      /shadow\s*==\s*next\.text[\s\S]*?return next/
    );
  }
);

test(
  "PTY sentinel still converts empty IME edit to DEL",
  async () => {
    const source =
      await readFile(sourceUrl, "utf8");

    assert.match(
      source,
      /previous\s*==\s*LOCAL_PTY_IME_SENTINEL\s*&&\s*next\.isEmpty\(\)[\s\S]*?return\s+"\\u007f"/
    );
  }
);

test(
  "Stage 11O resets IME only through protected paste dispatch",
  async () => {
    const source =
      await readFile(sourceUrl, "utf8");

    assert.match(
      source,
      /dispatch\.resetIme/
    );

    assert.match(
      source,
      /text\s*=\s*LOCAL_PTY_IME_SENTINEL/
    );

    assert.match(
      source,
      /resetIme\s*=\s*true/
    );

    assert.match(
      source,
      /resetIme\s*=\s*false/
    );
  }
);
