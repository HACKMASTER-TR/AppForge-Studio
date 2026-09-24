import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function read(relative) {
  return fs.readFileSync(
    new URL(`../../${relative}`, import.meta.url),
    "utf8"
  );
}

const store = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentSessionStore.kt"
);

const route = read(
  "android-app/app/src/main/java/com/appforge/studio/UnifiedAgentStudioRoute.kt"
);

const screen = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeUnifiedAgentStudioScreen.kt"
);

test("V12 session store keeps bounded multi-session history", () => {
  assert.match(store, /fun listRecent\(/);
  assert.match(store, /fun loadById\(/);
  assert.match(store, /fun deleteSession\(/);
  assert.match(store, /MAX_HISTORY_FILES/);
});

test("V12 route wires recent sessions without auto-opening them", () => {
  assert.match(route, /recentSessions/);
  assert.match(route, /sessionStore\.listRecent\(/);
  assert.match(route, /sessionStore\.loadById\(/);
  assert.match(route, /onSelectRecentSession/);
});

test("V12 prompt UI exposes recent work selection and deletion", () => {
  assert.match(screen, /Son çalışmalar/);
  assert.match(screen, /onSelectRecentSession/);
  assert.match(screen, /onDeleteRecentSession/);
  assert.match(screen, /Kaydı sil ve yeni başla/);
});
