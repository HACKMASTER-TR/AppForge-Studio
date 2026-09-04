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

test("pipeline performs health, toolchain, install, test, build and deploy gate in order", async () => {
  const core = await source(
    "terminal/UltimateProjectPipeline.kt"
  );

  for (const phase of [
    "HEALTH",
    "TOOLCHAINS",
    "INSTALL",
    "TEST",
    "BUILD",
    "DEPLOY_GATE"
  ]) {
    assert.ok(
      core.includes(phase),
      `missing pipeline phase ${phase}`
    );
  }

  assert.match(
    core,
    /sortedBy[\s\S]*ProjectAutomationStepKind\.INSTALL[\s\S]*ProjectAutomationStepKind\.TEST[\s\S]*ProjectAutomationStepKind\.BUILD/
  );
  assert.match(
    core,
    /if \([\s\S]*result\.status ==[\s\S]*ProjectPipelineStatus\.FAILED[\s\S]*return failure/
  );
});

test("pipeline has one explicit start confirmation and deploy remains behind the existing 5B gate", async () => {
  const panel = await source(
    "terminal/UltimateProjectPipelinePanel.kt"
  );

  assert.match(
    panel,
    /confirmPipeline/
  );
  assert.match(
    panel,
    /Pipeline'ı Başlat/
  );
  assert.match(
    panel,
    /Gerçek deploy bu zincirde sessizce başlamaz/
  );
  assert.match(
    panel,
    /onOpenDeployment\(\)/
  );
  assert.doesNotMatch(
    panel,
    /DeploymentExecutionService/
  );
});

test("missing runtime tools are diagnosed before package installation", async () => {
  const core = await source(
    "terminal/UltimateProjectPipeline.kt"
  );

  assert.match(
    core,
    /parseRuntimeProbe/
  );
  assert.match(
    core,
    /missingCommands/
  );
  assert.match(
    core,
    /packageInstallCommand/
  );
  assert.match(
    core,
    /Kurulum sonrasında eksik kalan araçlar/
  );
});

test("failed pipeline output is sanitized and handed to Ultimate AI without persisting secrets", async () => {
  const [core, ultimate] = await Promise.all([
    source(
      "terminal/UltimateProjectPipeline.kt"
    ),
    source(
      "terminal/TerminalUltimatePanel.kt"
    )
  ]);

  assert.match(
    core,
    /UltimatePipelineAiContextBuilder/
  );
  assert.match(
    core,
    /TerminalSecretMasker\.redact/
  );
  assert.match(
    core,
    /UltimateAiHandoffStore/
  );
  assert.doesNotMatch(
    core,
    /SharedPreferences|FileOutputStream|writeText|appendText/
  );
  assert.match(
    ultimate,
    /UltimateAiHandoffStore[\s\S]*\.peek\(\)/
  );
});

test("project pipeline never creates its own host process, curl-pipe installer or silent deploy client", async () => {
  const files = await Promise.all([
    source(
      "terminal/UltimateProjectPipeline.kt"
    ),
    source(
      "terminal/UltimateProjectPipelinePanel.kt"
    )
  ]);

  const combined = files.join("\n");

  assert.doesNotMatch(
    combined,
    /ProcessBuilder|Runtime\.getRuntime\(\)\.exec/
  );
  assert.doesNotMatch(
    combined,
    /curl.*\|.*(?:sh|bash)|wget.*\|.*(?:sh|bash)/
  );
  assert.doesNotMatch(
    combined,
    /DeploymentExecutionService/
  );
});
