import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function read(relative) {
  return fs.readFileSync(
    new URL(`../../${relative}`, import.meta.url),
    "utf8"
  );
}

const mainActivity = read(
  "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
);
const home = read(
  "android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
);
const route = read(
  "android-app/app/src/main/java/com/appforge/studio/UnifiedAgentStudioRoute.kt"
);
const studioScreen = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeUnifiedAgentStudioScreen.kt"
);
const buildRunner = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentBuildServiceStageRunner.kt"
);
const artifactClient = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentArtifactClient.kt"
);
const releaseReview = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentReleaseReview.kt"
);
const androidWorkflow = read(
  ".github/workflows/android-debug.yml"
);

test("V11 active StudioHomeV2 routes to the dedicated Unified Agent screen", () => {
  assert.match(home, /fun StudioHomeV2\(/);
  assert.match(home, /onOpenUnifiedAgent: \(\) -> Unit/);
  assert.match(
    home,
    /UnifiedAgentHomeEntryCard\([\s\S]*onClick = onOpenUnifiedAgent/
  );
  assert.match(
    mainActivity,
    /StudioHomeV2\([\s\S]*onOpenUnifiedAgent = \{[\s\S]*AppScreen\.UNIFIED_AGENT/
  );
  assert.match(mainActivity, /UnifiedAgentStudioRoute\(/);
});

test("V11 route connects local AI blueprint generation and autonomous orchestrator", () => {
  assert.match(route, /generateStructuredJson\(/);
  assert.match(route, /AppForgeAgentBlueprintJson\.parse\(/);
  assert.match(route, /AppForgeAgentStudioOrchestrator\(/);
  assert.match(route, /AppForgeAgentLocalPatchProvider\(/);
});

test("V11 build path uses AppForge Build Service and bounded TEST then BUILD stages", () => {
  assert.match(buildRunner, /AppForgeAgentExecutionStage\.TEST/);
  assert.match(buildRunner, /AppForgeAgentExecutionStage\.BUILD/);
  assert.match(buildRunner, /client\.createBuild\(/);
  assert.match(buildRunner, /client\.getBuild\(/);
  assert.match(buildRunner, /MAX_POLL_ATTEMPTS/);
});

test("V11 result flow exposes artifacts, logs, source export and release review", () => {
  assert.match(artifactClient, /createDownloadTicket\(/);
  assert.match(artifactClient, /client\.getLogs\(/);
  assert.match(artifactClient, /client\.testLab\(/);
  assert.match(studioScreen, /APK indir/);
  assert.match(studioScreen, /AAB indir/);
  assert.match(studioScreen, /Portable EXE indir/);
  assert.match(studioScreen, /Üretilen kaynak ZIP'i dışa aktar/);
  assert.match(studioScreen, /Release Ready Kontrolü/);
});

test("V11 release readiness preserves manual deploy review and blocks high severity findings", () => {
  assert.match(releaseReview, /reviewRequired: Boolean = true/);
  assert.match(releaseReview, /"high"/);
  assert.match(releaseReview, /"critical"/);
  assert.match(studioScreen, /Deploy gate: REVIEW_REQUIRED/);
  assert.doesNotMatch(route, /\bdeploy\s*\(/);
});

test("V11 Android acceptance remains enforced by the existing CI unit-test gate", () => {
  assert.match(androidWorkflow, /:app:testDebugUnitTest/);
});
