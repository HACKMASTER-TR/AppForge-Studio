import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(new URL(`../../${path}`, import.meta.url), "utf8");

test("Stage 10H fixes phone terminal UX without regressing native PTY", async () => {
  const pty = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  );

  assert.match(pty, /outputRevision/);
  assert.match(pty, /LaunchedEffect\(\s*state\.outputRevision\s*\)/);
  assert.doesNotMatch(pty, /LaunchedEffect\(\s*rendered\.length,\s*outputScroll\.maxValue/);
  assert.doesNotMatch(pty, /WindowInsets\.ime/);
  assert.doesNotMatch(pty, /imeBottomPx/);
  assert.doesNotMatch(
    pty,
    /import androidx\.compose\.foundation\.layout\.getBottom/
  );
  assert.match(pty, /RESIZE_DEBOUNCE_MS/);
  assert.match(pty, /TerminalDevelopmentEnvironmentCoordinator/);
  assert.match(pty, /detectTransformGestures/);
  assert.match(pty, /onFontSizeSpChange/);
  assert.match(pty, /SelectionContainer/);
  assert.match(pty, /\.size\(1\.dp\)\s*\.alpha\(0f\)\s*\.focusRequester/);
  assert.match(pty, /selectionEpoch/);
  assert.match(pty, /isHighSurrogate/);
  assert.match(pty, /isLowSurrogate/);
  assert.match(pty, /codePointCount/);
  assert.match(pty, /closeThisSession/);
  assert.match(pty, /contentPadding\s*=\s*PaddingValues/);
  assert.match(pty, /APPFORGE PTY SELF TEST/);
  assert.match(pty, /done\\r"/);
  assert.doesNotMatch(pty, /done\\\\r"/);
});

test("Stage 10I adds one-tap AppForge-owned Linux developer environment", async () => {
  const runtime = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxRuntimeFoundation.kt"
  );
  const panel = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxRuntimePanel.kt"
  );
  const shell = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxShellEngine.kt"
  );
  const pipeline = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/UltimateProjectPipeline.kt"
  );

  for (const pkg of [
    "git", "openssh-client", "bash", "zsh", "python3", "python3-pip",
    "python-is-python3", "nodejs", "npm", "gradle", "build-essential",
    "clang", "cmake", "ninja-build", "adb", "fastboot", "jq", "sqlite3",
    "openssl"
  ]) {
    assert.ok(runtime.includes(`"${pkg}"`), `missing package ${pkg}`);
  }

  assert.match(runtime, /developmentProfileIds/);
  assert.match(runtime, /developmentProfileCommand/);
  assert.match(runtime, /LinuxToolchainId\.ANDROID/);
  assert.match(panel, /AppForge Dev Suite/);
  assert.match(panel, /Rootfs \+ Dev Suite Tek Seferde Kur/);
  assert.match(panel, /LinuxShellEngine/);
  assert.match(panel, /\.developmentProfileCommand\(\)/);
  assert.match(panel, /timeoutMs\s*=\s*1_800_000L/);
  assert.match(shell, /1_000L\.\.1_800_000L/);
  assert.match(
    pipeline,
    /LinuxToolchainId\.ANDROID\s*->\s*setOf\("adb", "fastboot"\)/
  );
});
