import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function read(relative) {
  return fs.readFileSync(
    new URL(`../../${relative}`, import.meta.url),
    "utf8"
  );
}

const model = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentPersistentSession.kt"
);
const codec = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentSessionCodec.kt"
);
const store = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentSessionStore.kt"
);
const route = read(
  "android-app/app/src/main/java/com/appforge/studio/UnifiedAgentStudioRoute.kt"
);
const screen = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeUnifiedAgentStudioScreen.kt"
);
const resumer = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentRemoteBuildResumer.kt"
);

test("V12 final session metadata is additive and persisted", () => {
  assert.match(model, /customName/);
  assert.match(model, /pinned/);
  assert.match(model, /archived/);
  assert.match(codec, /"customName"/);
  assert.match(codec, /"pinned"/);
  assert.match(codec, /"archived"/);
});

test("V12 session manager supports rename pin archive restore", () => {
  assert.match(store, /fun renameSession\(/);
  assert.match(store, /fun setPinned\(/);
  assert.match(store, /fun setArchived\(/);
  assert.match(store, /fun listArchived\(/);
  assert.match(screen, /Son çalışmalar/);
  assert.match(screen, /Arşiv/);
  assert.match(screen, /Yeniden adlandır/);
});

test("V12 storage cleanup stays scoped to Unified Agent workspace root", () => {
  assert.match(
    store,
    /val workspaceRoot\s*=\s*File\(\s*filesDir,\s*"unified-agent-workspaces"\s*\)\s*\.canonicalFile/s
  );

  assert.match(
    store,
    /val insideRoot\s*=\s*canonical\.parentFile\s*==\s*workspaceRoot/s
  );

  assert.match(
    store,
    /insideRoot\s*&&\s*canonical\.path\s*!in\s*referenced\s*&&\s*oldEnough\s*&&\s*canonical\.deleteRecursively\(\)/s
  );

  assert.doesNotMatch(
    store,
    /filesDir\.deleteRecursively\(\)/
  );

  assert.doesNotMatch(
    store,
    /workspaceRoot\.deleteRecursively\(\)/
  );
});

test("V12 resume still never creates or cancels replacement Cloud Builds", () => {
  assert.match(resumer, /client\.getBuild\(/);
  assert.doesNotMatch(resumer, /createBuild\(/);
  assert.doesNotMatch(resumer, /cancelBuild\(/);
});

test("V12 final product flow keeps manual deploy out of persistence and recovery", () => {
  assert.doesNotMatch(store, /deploy/i);
  assert.doesNotMatch(codec, /deploy/i);
  assert.match(route, /AppForgeAgentRecoveryPolicy\.assess/);
  assert.match(route, /sessionStore\.cleanupStorage/);
});

test("V12 route exposes all session-management callbacks", () => {
  assert.match(route, /onRenameSession/);
  assert.match(route, /onTogglePinned/);
  assert.match(route, /onArchiveSession/);
  assert.match(route, /onRestoreArchivedSession/);
});
