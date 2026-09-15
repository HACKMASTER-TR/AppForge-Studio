import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

const adapterUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/TermuxTerminalCoreAdapter.kt",
  import.meta.url
);

test("real PTY output is redacted then published into the active Termux mirror", async () => {
  const source = await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /val safeChunk\s*=\s*TerminalSecretMasker\.redact\(\s*chunk\s*\)/
  );
  assert.match(source, /current\.buffer\.feed\(\s*safeChunk\s*\)/);
  assert.match(
    source,
    /TermuxTerminalMirrorRegistry\s*\.publish\(\s*sessionId\s*=\s*id,\s*text\s*=\s*safeChunk\s*\)/
  );
  assert.doesNotMatch(
    source,
    /TermuxTerminalMirrorRegistry\s*\.publish\([\s\S]{0,180}text\s*=\s*chunk\b/
  );
});

test("hidden Android IME input still writes to the real AppForge PTY", async () => {
  const source = await readFile(panelUrl, "utf8");

  assert.match(source, /BasicTextField\(/);
  assert.match(
    source,
    /LocalPtySessionRegistry\.write\(\s*state\.id,\s*dispatch\.ptyText\s*\)/
  );
});

test("keyboard no longer double-translates terminal controls by the full IME height", async () => {
  const source = await readFile(panelUrl, "utf8");

  assert.doesNotMatch(source, /val imeInsets\s*=\s*WindowInsets\.ime/);
  assert.doesNotMatch(source, /y\s*=\s*-imeInsets\.getBottom\(this\)/);
  assert.match(source, /val accessoryReservePx\s*=\s*0/);
});

test("Termux viewport remains output-only; hidden IME owns real PTY input", async () => {
  const source = await readFile(adapterUrl, "utf8");
  const mirrorStart = source.indexOf("internal fun TermuxTerminalMirrorHost(");
  assert.ok(mirrorStart >= 0, "TermuxTerminalMirrorHost missing");
  const mirror = source.slice(mirrorStart);
  assert.match(mirror, /inputEnabled\s*=\s*false/);
});
