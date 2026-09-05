import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(
    new URL(`../../${path}`, import.meta.url),
    "utf8"
  );

const terminalSource = (name) =>
  read(
    `android-app/app/src/main/java/com/appforge/studio/terminal/${name}`
  );

const terminalAsset = (name) =>
  read(
    `android-app/app/src/main/assets/terminal/${name}`
  );

test("Stage 10N batches PTY output to avoid paste and Enter redraw storms", async () => {
  const pty =
    await terminalSource(
      "LocalPtyTerminalPanel.kt"
    );

  assert.match(
    pty,
    /outputPublishScope/
  );

  assert.match(
    pty,
    /pendingOutputPublishes/
  );

  assert.match(
    pty,
    /scheduleOutputPublishLocked/
  );

  assert.match(
    pty,
    /OUTPUT_PUBLISH_INTERVAL_MS\s*=\s*32L/
  );

  assert.match(
    pty,
    /CharArray\(8_192\)/
  );

  assert.match(
    pty,
    /current\.buffer\.feed\([\s\S]*?scheduleOutputPublishLocked\(\s*id\s*\)/
  );
});

test("Stage 10N retries a core workstation profile when the complete toolchain fails", async () => {
  const [foundation, manager, coordinator] =
    await Promise.all([
      terminalSource(
        "LinuxRuntimeFoundation.kt"
      ),
      terminalSource(
        "AndroidLinuxRuntimeManager.kt"
      ),
      terminalSource(
        "TerminalDevelopmentEnvironment.kt"
      )
    ]);

  assert.match(
    foundation,
    /standaloneRecoveryIds/
  );

  for (const id of [
    "BASE",
    "PYTHON",
    "NODE",
    "JAVA",
    "C_CPP"
  ]) {
    assert.ok(
      foundation.includes(
        `LinuxToolchainId.${id}`
      ),
      `recovery profile missing ${id}`
    );
  }

  assert.match(
    foundation,
    /standaloneRecoveryCommand/
  );

  assert.match(
    manager,
    /developmentProfileCommand/
  );

  assert.match(
    manager,
    /standaloneRecoveryCommand/
  );

  assert.match(
    manager,
    /appforge-terminal-dev-tools-recovery/
  );

  assert.match(
    manager,
    /appforge-dev-suite-v3/
  );

  assert.match(
    coordinator,
    /toolsAttempted\s*=\s*false/
  );
});

test("Stage 10N provides one-command tool repair and project bootstrap", async () => {
  const [
    bootstrap,
    doctor,
    ready,
    repair,
    project
  ] = await Promise.all([
    terminalSource(
      "TerminalStandaloneDeveloperBootstrap.kt"
    ),
    terminalAsset(
      "appforge-doctor"
    ),
    terminalAsset(
      "appforge-ready"
    ),
    terminalAsset(
      "appforge-repair-tools"
    ),
    terminalAsset(
      "appforge-project"
    )
  ]);

  assert.ok(
    bootstrap.includes(
      '"appforge-repair-tools"'
    )
  );

  assert.ok(
    bootstrap.includes(
      '"appforge-project"'
    )
  );

  assert.match(
    doctor,
    /appforge-repair-tools/
  );

  assert.match(
    doctor,
    /Bağlantılar > GitHub/
  );

  assert.match(
    ready,
    /appforge-project/
  );

  for (const pkg of [
    "npm",
    "default-jdk",
    "nano"
  ]) {
    assert.ok(
      repair.includes(pkg),
      `repair helper missing ${pkg}`
    );
  }

  assert.match(
    project,
    /https:\/\/github\.com\/HACKMASTER-TR\/AppForge-Studio\.git/
  );

  assert.match(
    project,
    /\/workspace\/AppForge-Studio/
  );

  assert.match(
    project,
    /exec \/bin\/bash/
  );
});

test("Stage 10N preserves verified IME, Backspace and productivity keys", async () => {
  const pty =
    await terminalSource(
      "LocalPtyTerminalPanel.kt"
    );

  assert.doesNotMatch(
    pty,
    /\.imePadding\(\)/
  );

  assert.match(
    pty,
    /WindowInsets\.ime/
  );

  assert.match(
    pty,
    /value\s*=\s*imeValue/
  );

  assert.match(
    pty,
    /autoCorrectEnabled\s*=\s*false/
  );

  for (const key of [
    '"CTRL+C"',
    '"CTRL+A"',
    '"CTRL+E"',
    '"CTRL+R"',
    '"CTRL+U"',
    '"CTRL+W"',
    '"⌫"'
  ]) {
    assert.ok(
      pty.includes(key),
      `verified key disappeared: ${key}`
    );
  }
});
