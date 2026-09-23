import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const androidRoot = new URL(
  "../../android-app/app/",
  import.meta.url
);

const panel = () =>
  readFile(
    new URL(
      "src/main/java/com/appforge/studio/terminal/LinuxMultiSessionTerminalPanel.kt",
      androidRoot
    ),
    "utf8"
  );

test("Stage 8A clipboard actions are explicit, transient and write paste directly to the PTY", async () => {
  const source = await panel();

  assert.match(source, /LocalClipboard\.current/);
  assert.match(source, /nativeClipboardManager/);
  assert.match(source, /primaryClip/);
  assert.match(source, /ClipData\.newPlainText/);
  assert.match(source, /normalizeTerminalPaste/);
  assert.match(source, /MAX_TERMINAL_PASTE_CHARS\s*=\s*\n\s*65_536/);
  assert.match(source, /LinuxPtySessionRegistry\s*\.write/);

  assert.doesNotMatch(
    source,
    /SharedPreferences|FileOutputStream|openFileOutput|TerminalRestorePointManager/
  );
});

test("Stage 8A fixes empty-buffer Backspace with an in-memory IME sentinel", async () => {
  const source = await panel();

  assert.match(source, /TERMINAL_IME_SENTINEL/);
  assert.match(source, /terminalImeDeltaWithSentinel/);
  assert.match(
    source,
    /previous == TERMINAL_IME_SENTINEL[\s\S]*next\.isEmpty\(\)[\s\S]*return "\\u007f"/
  );
  assert.match(source, /terminalImeShadow/);
});

test("Stage 8A adds transient terminal-output search with previous and next navigation", async () => {
  const source = await panel();

  assert.match(source, /Terminal çıktısında ara/);
  assert.match(source, /terminalSearchLineMatches/);
  assert.match(source, /searchMatchIndex/);
  assert.match(source, /"Önceki"/);
  assert.match(source, /"Sonraki"/);
  assert.match(source, /outputScroll\.scrollTo\(target\)/);
});

test("Stage 8A font zoom updates both rendering and PTY geometry", async () => {
  const source = await panel();

  assert.match(source, /DEFAULT_TERMINAL_FONT_SP/);
  assert.match(source, /MIN_TERMINAL_FONT_SP/);
  assert.match(source, /MAX_TERMINAL_FONT_SP/);
  assert.match(source, /fontSize = terminalFontSize/);
  assert.match(source, /lineHeight = terminalLineHeight/);
  assert.match(source, /charWidthPx/);
  assert.match(source, /LinuxPtySessionRegistry\s*\.resize/);
  assert.match(source, /"A−"/);
  assert.match(source, /"A\+"/);
});

test("Stage 8A provides terminal-native extra keys without restoring the old command field", async () => {
  const source = await panel();

  for (const key of [
    '"Esc"',
    '"⌫"',
    '"↵"',
    '"Ctrl+C"',
    '"Ctrl+L"',
    '"Tab"',
    '"Home"',
    '"End"',
    '"Pg↑"',
    '"Pg↓"'
  ]) {
    assert.ok(source.includes(key), `missing terminal key ${key}`);
  }

  assert.match(source, /"\\u001b\[H"/);
  assert.match(source, /"\\u001b\[F"/);
  assert.match(source, /"\\u001b\[5~"/);
  assert.match(source, /"\\u001b\[6~"/);
  assert.doesNotMatch(source, /Linux komutu \/ giriş/);
  assert.doesNotMatch(source, /Text\("Gönder"\)/);
});
