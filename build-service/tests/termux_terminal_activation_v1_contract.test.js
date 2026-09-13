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

  /*
   * Termux must own the viewport directly.
   * Nesting TerminalView inside LazyColumn caused foreground/resume
   * repositioning and could leave the prompt below the visible area.
   */
  assert.match(
    panel,
    /}\s*else if\s*\(useTermuxViewport\)\s*\{[\s\S]{0,1600}?TermuxTerminalMirrorHost/,
  );

  /*
   * V1.3 keeps TerminalView measured independently from IME.
   * The stable viewport has fixed padding; IME occlusion is handled
   * only by placement offset, not by changing terminal rows.
   */
  const v13DirectHostStart =
    panel.indexOf(
      "TermuxTerminalMirrorHost(",
    );

  const v13LegacyRendererStart =
    panel.indexOf(
      "LazyColumn(",
      v13DirectHostStart,
    );

  assert.ok(
    v13DirectHostStart >= 0,
    "direct Termux host must exist",
  );

  assert.ok(
    v13LegacyRendererStart > v13DirectHostStart,
    "legacy Compose renderer must remain after Termux host",
  );

  const v13DirectHostBlock =
    panel.slice(
      v13DirectHostStart,
      v13LegacyRendererStart,
    );

  assert.match(
    v13DirectHostBlock,
    /\.fillMaxSize\(\)/,
  );

  assert.match(
    v13DirectHostBlock,
    /bottom\s*=\s*12\.dp/,
  );

  assert.match(
    v13DirectHostBlock,
    /-bottomContentPaddingPx/,
  );

  assert.doesNotMatch(
    v13DirectHostBlock,
    /bottom\s*=\s*bottomContentPadding\b/,
  );

  assert.doesNotMatch(
    panel,
    /listOf\(-1\)/,
  );

  assert.doesNotMatch(
    panel,
    /TermuxTerminalMirrorHost[\s\S]{0,900}\.fillParentMaxHeight\(\)/,
  );

  assert.doesNotMatch(
    panel,
    /userScrollEnabled\s*=\s*!useTermuxViewport/,
  );

  assert.match(
    panel,
    /Legacy Compose terminal renderer/,
  );

  const guards =
    panel.match(
      /if\s*\(useTermuxViewport\)\s*\{\s*return@LaunchedEffect\s*\}/g,
    ) ?? [];

  assert.ok(
    guards.length >= 2,
    "Compose auto-scroll must be disabled while Termux owns viewport",
  );
});


test("Termux V1.3 geometry follows the real TerminalView", async () => {
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

  /*
   * Termux is the geometry authority in Termux viewport mode.
   * The old Compose font-size approximation must not resize the real PTY.
   */
  const resizeEffectAnchor =
    "LaunchedEffect(\n" +
    "        surfaceSize,\n" +
    "        fontSizeSp,\n" +
    "        state.id\n" +
    "    ) {";

  const resizeEffectIndex =
    panel.indexOf(resizeEffectAnchor);

  assert.notEqual(
    resizeEffectIndex,
    -1,
    "PTY resize LaunchedEffect must exist",
  );

  const termuxGuardIndex =
    panel.indexOf(
      "if (useTermuxViewport)",
      resizeEffectIndex,
    );

  const legacySizeGuardIndex =
    panel.indexOf(
      "surfaceSize.width <= 0",
      resizeEffectIndex,
    );

  assert.ok(
    termuxGuardIndex > resizeEffectIndex,
    "Termux geometry guard must exist inside PTY resize effect",
  );

  assert.ok(
    legacySizeGuardIndex > termuxGuardIndex,
    "Termux guard must run before legacy Compose size calculation",
  );

  const guardBlock =
    panel.slice(
      termuxGuardIndex,
      legacySizeGuardIndex,
    );

  assert.match(
    guardBlock,
    /return@LaunchedEffect/,
  );

  const geometryCallbackStart =
    panel.indexOf(
      "onGeometryChanged = {",
    );

  assert.notEqual(
    geometryCallbackStart,
    -1,
    "Termux geometry callback must exist",
  );

  const geometryCallbackEnd =
    panel.indexOf(
      "modifier =",
      geometryCallbackStart,
    );

  assert.ok(
    geometryCallbackEnd > geometryCallbackStart,
    "geometry callback must end before modifier",
  );

  const geometryCallbackBlock =
    panel.slice(
      geometryCallbackStart,
      geometryCallbackEnd,
    );

  assert.match(
    geometryCallbackBlock,
    /\brows\b/,
  );

  assert.match(
    geometryCallbackBlock,
    /\bcolumns\b/,
  );

  assert.match(
    geometryCallbackBlock,
    /LocalPtySessionRegistry/,
  );

  assert.match(
    geometryCallbackBlock,
    /\.resize\(/,
  );

  assert.ok(
    geometryCallbackBlock.indexOf("rows") <
      geometryCallbackBlock.indexOf(".resize("),
    "rows must feed PTY resize",
  );

  assert.ok(
    geometryCallbackBlock.indexOf("columns") <
      geometryCallbackBlock.indexOf(".resize("),
    "columns must feed PTY resize",
  );

  /*
   * IME may translate the viewport but must not change its measured size.
   */
  assert.match(
    panel,
    /TermuxTerminalMirrorHost[\s\S]{0,1400}?bottom\s*=\s*12\.dp[\s\S]{0,900}?\.offset\s*\{[\s\S]{0,400}?-bottomContentPaddingPx/,
  );

  assert.doesNotMatch(
    panel,
    /TermuxTerminalMirrorHost[\s\S]{0,1400}?bottom\s*=\s*bottomContentPadding/,
  );

  /*
   * TerminalView's real emulator rows/columns are reported upstream.
   */
  assert.match(
    adapter,
    /onGeometryChanged:\s*\(\s*rows:\s*Int,\s*columns:\s*Int,\s*\)\s*->\s*Unit/,
  );

  assert.match(
    adapter,
    /mEmulator[\s\S]{0,500}?mRows[\s\S]{0,300}?mColumns/,
  );

  assert.match(
    adapter,
    /addOnLayoutChangeListener/,
  );
});
