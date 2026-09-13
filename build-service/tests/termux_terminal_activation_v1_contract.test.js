import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "..", "..");

const read = (...parts) =>
  fs.readFile(path.join(repoRoot, ...parts), "utf8");

test("Termux Activation V1 owns viewport without owning AppForge shell", async () => {
  const adapter = await read(
    "android-app",
    "app",
    "src",
    "main",
    "java",
    "com",
    "appforge",
    "studio",
    "terminal",
    "TermuxTerminalCoreAdapter.kt",
  );

  assert.match(adapter, /TermuxTerminalMirrorRegistry/);
  assert.match(adapter, /inputEnabled:\s*Boolean\s*=\s*true/);
  assert.match(adapter, /stty raw -echo/);
  assert.match(adapter, /exec \/system\/bin\/cat/);
  assert.match(adapter, /inputEnabled\s*=\s*false/);
  assert.match(adapter, /TermuxTerminalMirrorHost/);
});

test("Linux PTY publishes redacted output into Termux renderer bridge", async () => {
  const registry = await read(
    "android-app",
    "app",
    "src",
    "main",
    "java",
    "com",
    "appforge",
    "studio",
    "terminal",
    "LinuxPtySessionRegistry.kt",
  );

  assert.match(registry, /val safeChunk\s*=\s*TerminalSecretMasker\.redact/);
  assert.match(registry, /TermuxTerminalMirrorRegistry\s*\.publish/);
});

test("normal terminal viewport uses Termux while Compose renderer remains fallback", async () => {
  const panel = await read(
    "android-app",
    "app",
    "src",
    "main",
    "java",
    "com",
    "appforge",
    "studio",
    "terminal",
    "LocalPtyTerminalPanel.kt",
  );

  assert.match(panel, /val useTermuxViewport\s*=\s*true/);
  assert.match(panel, /userScrollEnabled\s*=\s*!useTermuxViewport/);
  assert.match(panel, /listOf\(-1\)/);
  assert.match(panel, /TermuxTerminalMirrorHost/);
  assert.match(panel, /return@items/);

  const guards =
    panel.match(
      /if\s*\(useTermuxViewport\)\s*\{\s*return@LaunchedEffect\s*\}/g,
    ) ?? [];

  assert.ok(
    guards.length >= 2,
    "Compose auto-scroll must be disabled while Termux owns viewport",
  );
});
