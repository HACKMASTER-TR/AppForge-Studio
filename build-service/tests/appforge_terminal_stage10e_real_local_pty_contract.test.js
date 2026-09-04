import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(new URL(`../../${path}`, import.meta.url), "utf8");

test("Stage 10E routes the main Terminal tab through a persistent native PTY", async () => {
  const workspace = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalWorkspaceScreen.kt"
  );
  const pty = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  );

  assert.match(
    workspace,
    /TerminalWorkspaceTab\.TERMINAL\s*->\s*LocalPtyTerminalPanel\(/
  );
  assert.doesNotMatch(
    workspace,
    /TerminalWorkspaceTab\.TERMINAL\s*->\s*LocalTerminalPanel\(/
  );

  assert.match(pty, /AppForgePtyBridge\.spawn\(/);
  assert.match(pty, /executable\s*=\s*"\/system\/bin\/sh"/);
  assert.match(pty, /arguments\s*=\s*listOf\("-i"\)/);
  assert.match(pty, /LocalPtySessionRegistry\s*\.write\(/);
  assert.match(pty, /BasicTextField\(/);
  assert.match(pty, /Color\.Transparent/);
  assert.match(pty, /innerTextField\(\)/);
  assert.match(pty, /localPtyImeDeltaWithSentinel/);
  assert.match(pty, /sendControlC/);
  assert.match(pty, /"\\u001b\[A"/);
  assert.match(pty, /"\\u001b\[B"/);

  assert.doesNotMatch(pty, /OutlinedTextField\(/);
  assert.doesNotMatch(pty, /Terminale dokun ve yaz/);
  assert.doesNotMatch(pty, /Text\("Komut"\)/);
  assert.doesNotMatch(pty, /Text\("Çalıştır"\)/);
});
