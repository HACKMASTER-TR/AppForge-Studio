import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function read(relative) {
  return fs.readFileSync(
    new URL(`../../${relative}`, import.meta.url),
    "utf8"
  );
}

const recovery = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentRecoveryPolicy.kt"
);

const route = read(
  "android-app/app/src/main/java/com/appforge/studio/UnifiedAgentStudioRoute.kt"
);

const screen = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeUnifiedAgentStudioScreen.kt"
);

test("V12 recovery center detects stale build and missing workspace", () => {
  assert.match(recovery, /BUILD_STALE/);
  assert.match(recovery, /WORKSPACE_MISSING/);
  assert.match(recovery, /BUILD_ID_MISSING/);
  assert.match(recovery, /QUARANTINE_PRESENT/);
});

test("V12 recovery center never starts a replacement Cloud Build", () => {
  assert.doesNotMatch(recovery, /createBuild\(/);
  assert.doesNotMatch(recovery, /cancelBuild\(/);
  assert.match(route, /recoveryAssessment/);
  assert.match(route, /AppForgeAgentRecoveryPolicy\.assess/);
});

test("V12 prompt UI surfaces recovery diagnostics before resume", () => {
  assert.match(screen, /Kurtarma Merkezi/);
  assert.match(screen, /recoveryAssessment/);
  assert.match(screen, /safeToOpen/);
  assert.match(screen, /onResumeSession/);
});
