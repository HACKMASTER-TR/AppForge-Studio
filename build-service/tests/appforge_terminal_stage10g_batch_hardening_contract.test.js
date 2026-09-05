import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(new URL(`../../${path}`, import.meta.url), "utf8");

test("Stage 10G hardens local PTY lifecycle, state restore and UX", async () => {
  const pty = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  );
  const ansi = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/AnsiTerminalBuffer.kt"
  );
  const manifest = await read(
    "android-app/app/src/main/AndroidManifest.xml"
  );
  const main = await read(
    "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  );

  assert.match(pty, /runCatching\s*\{\s*InputStreamReader/);
  assert.match(pty, /fun persistNow\(\)/);
  assert.match(pty, /session_descriptors_v2/);
  assert.match(pty, /currentWorkingDirectory\(\)/);
  assert.match(pty, /nextTerminalIndexLocked/);
  assert.match(
    pty,
    /LazyColumn/
  );

  assert.match(
    pty,
    /if\s*\(\s*copyMode\s*\)[\s\S]*?SelectionContainer/
  );
  assert.match(pty, /AnnotatedString/);
  assert.match(pty, /SpanStyle/);
  assert.match(pty, /fontSizeSp/);
  assert.match(pty, /"Tanıla"/);
  assert.doesNotMatch(pty, /active\?\.let \{ state ->\s*Row\(\s*modifier =\s*Modifier\s*\.fillMaxWidth\(\)\s*\.horizontalScroll/);

  assert.match(ansi, /enterAlternateScreen/);
  assert.match(ansi, /leaveAlternateScreen/);
  assert.match(ansi, /fun clear\(\)/);
  assert.match(ansi, /47, 1047, 1049/);

  assert.match(manifest, /android:configChanges="orientation\|screenSize\|screenLayout\|smallestScreenSize\|keyboard\|keyboardHidden\|uiMode"/);
  assert.match(main, /rememberSaveable/);
  assert.match(main, /LocalPtySessionRegistry\.persistNow\(\)/);
});
