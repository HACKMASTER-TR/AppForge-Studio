import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function read(relative) {
  return fs.readFileSync(new URL(`../../${relative}`, import.meta.url), "utf8");
}

const resumer = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentRemoteBuildResumer.kt"
);
const route = read(
  "android-app/app/src/main/java/com/appforge/studio/UnifiedAgentStudioRoute.kt"
);

test("V12 resume only polls existing build and never creates or cancels one", () => {
  assert.match(resumer, /client\.getBuild\(/);
  assert.doesNotMatch(resumer, /createBuild\(/);
  assert.doesNotMatch(resumer, /cancelBuild\(/);
});

test("V12 route uses persisted buildId for re-attach", () => {
  assert.match(route, /session\.resumableBuildId/);
  assert.match(route, /resumer\.resume\(/);
  assert.match(route, /AppForgeAgentRemoteBuildResumeOutcome\.SUCCESS/);
});

test("V12 resumed build returns to artifact and release review", () => {
  assert.match(route, /AppForgeAgentStudioStep\.RESULT/);
  assert.match(route, /artifactClient\.inspect\(/);
  assert.match(route, /releaseReviewClient\.load\(/);
});
