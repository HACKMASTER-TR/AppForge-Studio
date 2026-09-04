import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const androidRoot = new URL(
  "../../android-app/app/",
  import.meta.url
);

const source = (path) =>
  readFile(
    new URL(
      `src/main/java/com/appforge/studio/${path}`,
      androidRoot
    ),
    "utf8"
  );

test("Ultimate security center is integrated and Android declares biometric permission", async () => {
  const [panel, manifest] = await Promise.all([
    source("terminal/TerminalUltimatePanel.kt"),
    readFile(
      new URL(
        "src/main/AndroidManifest.xml",
        androidRoot
      ),
      "utf8"
    )
  ]);

  assert.match(
    panel,
    /TerminalSecurityCenterPanel\(/
  );
  assert.match(
    panel,
    /Güvenlik \+ Geri Yükleme/
  );
  assert.match(
    manifest,
    /android\.permission\.USE_BIOMETRIC/
  );
});

test("restore points exclude secrets and cap archive extraction", async () => {
  const manager = await source(
    "terminal/TerminalRestorePointManager.kt"
  );

  assert.match(manager, /\.env/);
  assert.match(manager, /id_rsa/);
  assert.match(manager, /jks/);
  assert.match(manager, /service-account\.json/);
  assert.match(manager, /MAX_ARCHIVE_BYTES/);
  assert.match(manager, /MAX_SINGLE_FILE_BYTES/);
  assert.match(manager, /MAX_FILE_COUNT/);
  assert.match(manager, /MAX_RESTORE_POINTS/);
});

test("restore point extraction rejects traversal and restores as non-destructive overlay", async () => {
  const manager = await source(
    "terminal/TerminalRestorePointManager.kt"
  );

  assert.match(
    manager,
    /!clean\.startsWith\("\.\.\/"\)/
  );
  assert.match(
    manager,
    /!clean\.contains\("\/.\.\/"\)/
  );
  assert.match(
    manager,
    /requireInside\(/
  );
  assert.match(
    manager,
    /copyTo\(\s*target,\s*overwrite\s*=\s*true/s
  );
  assert.doesNotMatch(
    manager,
    /workspace\.deleteRecursively|root\.deleteRecursively/
  );
});

test("sensitive restore requires biometric prompt when available", async () => {
  const [guard, panel] = await Promise.all([
    source("terminal/TerminalBiometricGuard.kt"),
    source("terminal/TerminalSecurityCenterPanel.kt")
  ]);

  assert.match(
    guard,
    /BiometricPrompt/
  );
  assert.match(
    guard,
    /onAuthenticationSucceeded/
  );
  assert.match(
    panel,
    /TerminalBiometricGuard\s*\.authenticate/
  );
  assert.match(
    panel,
    /pendingRestore/
  );
});

test("security layer neither starts processes nor makes network calls", async () => {
  const all = (
    await Promise.all([
      source("terminal/TerminalBiometricGuard.kt"),
      source("terminal/TerminalRestorePointManager.kt"),
      source("terminal/TerminalSecurityCenterPanel.kt")
    ])
  ).join("\n");

  assert.doesNotMatch(
    all,
    /ProcessBuilder|Runtime\.getRuntime\(\)\.exec/
  );
  assert.doesNotMatch(
    all,
    /HttpURLConnection|https?:\/\//
  );
  assert.doesNotMatch(
    all,
    /SharedPreferences|getSharedPreferences/
  );
});
