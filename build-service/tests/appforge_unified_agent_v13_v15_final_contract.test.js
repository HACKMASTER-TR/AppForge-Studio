import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function read(relative) {
  return fs.readFileSync(
    new URL(`../../${relative}`, import.meta.url),
    "utf8"
  );
}

const memory = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentProjectMemory.kt"
);
const quality = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentQualityGate.kt"
);
const finalAcceptance = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentFinalAcceptance.kt"
);
const route = read(
  "android-app/app/src/main/java/com/appforge/studio/UnifiedAgentStudioRoute.kt"
);

test("V13 project memory is scoped and integrity checked", () => {
  assert.match(memory, /"unified-agent-workspaces"/);
  assert.match(memory, /"unified-agent-project-memory"/);
  assert.match(memory, /sha256/);
  assert.match(memory, /Files\.isSymbolicLink/);
  assert.match(memory, /canonical\.parentFile == root/);
  assert.doesNotMatch(memory, /filesDir\.deleteRecursively\(\)/);
});

test("V13 provides bounded persistent checkpoints and rollback", () => {
  assert.match(memory, /fun createCheckpoint\(/);
  assert.match(memory, /fun rollbackLatest\(/);
  assert.match(memory, /fun rollbackTo\(/);
  assert.match(memory, /MAX_CHECKPOINTS = 8/);
});

test("V14 quality gate keeps repair bounded and platform-aware", () => {
  assert.match(quality, /fun boundedRepairBudget\(/);
  assert.match(quality, /minOf\(/);
  assert.match(quality, /validationTargets/);
  assert.match(quality, /AppForgeAgentPlatform\.WEB/);
});

test("V15 final acceptance preserves manual review", () => {
  assert.match(finalAcceptance, /manualReviewRequired: Boolean = true/);
  assert.match(finalAcceptance, /readiness\.reviewRequired/);
  assert.doesNotMatch(finalAcceptance, /deploy\(/i);
});

test("route integrates V13 checkpoint, V14 quality and V15 final acceptance", () => {
  assert.match(route, /AppForgeAgentProjectMemoryStore/);
  assert.match(route, /AppForgeAgentQualityGate\.assess/);
  assert.match(route, /projectMemoryStore\.createCheckpoint/);
  assert.match(route, /AppForgeAgentFinalAcceptance\.evaluate/);
});

test("final package does not introduce automatic deploy", () => {
  assert.doesNotMatch(memory, /deploy/i);
  assert.doesNotMatch(quality, /deploy/i);
  assert.doesNotMatch(finalAcceptance, /auto.?deploy/i);
});
