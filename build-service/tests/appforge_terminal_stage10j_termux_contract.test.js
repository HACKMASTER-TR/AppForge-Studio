import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(new URL(`../../${path}`, import.meta.url), "utf8");

test("Stage 10J makes main Terminal AppForge-owned and Termux-like", async () => {
  const pty = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  );
  const env = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalDevelopmentEnvironment.kt"
  );
  const manager = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/AndroidLinuxRuntimeManager.kt"
  );
  const proroot = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/ProrootRuntimeContract.kt"
  );

  assert.match(pty, /TerminalDevelopmentEnvironmentCoordinator/);
  assert.match(pty, /TermuxEnvironmentGate/);
  assert.match(pty, /LinearProgressIndicator/);
  assert.match(env, /Geliştirme ortamı hazırlanıyor/);
  assert.match(pty, /buildInteractiveShellArguments/);
  assert.match(pty, /PackagedLinuxEngine/);
  assert.match(pty, /RESIZE_DEBOUNCE_MS/);
  assert.match(pty, /availableWidthPx/);
  assert.match(pty, /FOCUS_RESTORE_GUARD_MS/);
  assert.match(pty, /isLowSurrogate/);
  assert.match(pty, /horizontalScroll/);
  assert.doesNotMatch(
    pty,
    /\.sortedByDescending\s*\{\s*it\.lastActivatedAt\s*\}/
  );

  for (const key of ["ESC", "TAB", "CTRL+C", "CTRL+L", "A−", "A+"]) {
    assert.ok(pty.includes(`"${key}"`), `missing compact key ${key}`);
  }

  assert.match(env, /SupervisorJob/);
  assert.match(env, /TerminalEnvironmentPhase\.PREPARING/);
  assert.match(env, /TerminalEnvironmentPhase\.READY/);
  assert.match(env, /TerminalEnvironmentPhase\.ERROR/);
  assert.match(env, /friendlyError/);

  assert.match(manager, /ensureDevelopmentEnvironment/);
  assert.match(manager, /developmentEnvironmentMutex\.withLock/);
  assert.match(manager, /developmentProfileReady/);
  assert.match(manager, /DEV_SUITE_MARKER/);
  assert.match(manager, /ConnectivityManager/);
  assert.match(proroot, /"-b",\s*"\/dev:\/dev"/);
  assert.match(proroot, /"-b",\s*"\/proc:\/proc"/);
});

test("Stage 10J rootfs extraction survives Android hard-link limitations safely", async () => {
  const installer = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/VerifiedLinuxRootfsInstaller.kt"
  );

  assert.match(installer, /HardLinkRequest/);
  assert.match(installer, /Files\.createLink/);
  assert.match(installer, /runCatching/);
  assert.match(installer, /Files\.copy/);
  assert.match(installer, /StandardCopyOption\.REPLACE_EXISTING/);
  assert.match(installer, /MAX_TOTAL_EXTRACTED_BYTES/);
  assert.match(installer, /Files\.isSymbolicLink/);
});
