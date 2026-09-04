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

test("Ultimate exposes project automation and the visual package store", async () => {
  const [ultimate, panel] = await Promise.all([
    source("terminal/TerminalUltimatePanel.kt"),
    source("terminal/UltimateProjectAutomationPanel.kt")
  ]);

  assert.match(
    ultimate,
    /UltimateProjectAutomationPanel\(/
  );
  assert.match(
    ultimate,
    /Proje Otomasyonu \+ Paket Mağazası/
  );
  assert.match(
    panel,
    /LinuxToolchainCatalog\.specs/
  );
  assert.match(
    panel,
    /Seçilenleri Kur/
  );
});

test("automation planner covers install test build and deployment hints", async () => {
  const core = await source(
    "terminal/UltimateProjectAutomation.kt"
  );

  assert.match(core, /ProjectAutomationStepKind\.INSTALL/);
  assert.match(core, /ProjectAutomationStepKind\.TEST/);
  assert.match(core, /ProjectAutomationStepKind\.BUILD/);
  assert.match(core, /package-lock\.json/);
  assert.match(core, /npm ci/);
  assert.match(core, /python3 -m pip install -r requirements\.txt/);
  assert.match(core, /\.\/gradlew assembleDebug/);
  assert.match(core, /cargo build/);
  assert.match(core, /go build \.\/\.\.\./);
  assert.match(core, /DeploymentProvider\.VERCEL/);
  assert.match(core, /DeploymentProvider\.CLOUDFLARE/);
  assert.match(core, /DeploymentProvider\.FIREBASE/);
  assert.match(core, /DeploymentProvider\.SUPABASE/);
  assert.match(core, /DeploymentProvider\.RENDER/);
  assert.match(core, /DeploymentProvider\.RAILWAY/);
});

test("automation commands execute only inside verified rootless Linux", async () => {
  const core = await source(
    "terminal/UltimateProjectAutomation.kt"
  );

  assert.match(core, /AndroidLinuxRuntimeManager/);
  assert.match(core, /requireReadyRootfs/);
  assert.match(core, /LinuxShellEngine/);
  assert.match(core, /confirmed\s*=\s*confirmed/);
  assert.doesNotMatch(core, /ProcessBuilder/);
  assert.doesNotMatch(core, /Runtime\.getRuntime\(\)\.exec/);
  assert.doesNotMatch(core, /curl.*\|.*(?:sh|bash)/s);
  assert.doesNotMatch(core, /wget.*\|.*(?:sh|bash)/s);
});

test("package installation cannot accept arbitrary package names from the UI", async () => {
  const [core, panel] = await Promise.all([
    source("terminal/UltimateProjectAutomation.kt"),
    source("terminal/UltimateProjectAutomationPanel.kt")
  ]);

  assert.match(core, /toolchainCommand/);
  assert.match(panel, /selectedToolchains/);
  assert.match(panel, /LinuxToolchainCatalog\.specs/);
  assert.doesNotMatch(panel, /OutlinedTextField/);
});

test("every lifecycle mutation requires a visible confirmation dialog", async () => {
  const panel = await source(
    "terminal/UltimateProjectAutomationPanel.kt"
  );

  assert.match(panel, /PendingAutomationCommand/);
  assert.match(panel, /AlertDialog/);
  assert.match(panel, /Onayla ve Çalıştır/);
  assert.match(panel, /confirmed\s*=\s*true/);
});
