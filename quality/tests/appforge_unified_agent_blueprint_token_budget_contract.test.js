import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function read(relative) {
  return fs.readFileSync(
    new URL(`../../${relative}`, import.meta.url),
    "utf8"
  );
}

const prompt = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentBlueprintPrompt.kt"
);
const assistant = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeLocalAssistant.kt"
);
const route = read(
  "android-app/app/src/main/java/com/appforge/studio/UnifiedAgentStudioRoute.kt"
);

test("blueprint prompt stays compact and preserves the schema contract", () => {
  assert.ok(prompt.length < 4_000, `prompt source unexpectedly large: ${prompt.length}`);
  assert.match(prompt, /APPFORGE BLUEPRINT JSON V2/);
  assert.match(prompt, /schemaVersion/);
  assert.match(prompt, /maxRepairAttempts/);
  assert.match(prompt, /<user_request>/);
  assert.doesNotMatch(prompt, /APPFORGE STRUCTURED BLUEPRINT GENERATOR V2/);
});

test("local AI context budget is raised above the previous 768 token ceiling", () => {
  assert.match(assistant, /maxNumTokens\s*=\s*2_048/);
  assert.doesNotMatch(assistant, /maxNumTokens\s*=\s*768/);
});

test("token overflow gets a Turkish user-facing fallback", () => {
  assert.match(route, /Input token ids are too long/);
  assert.match(route, /yerel AI bağlam sınırını aştı/);
});
