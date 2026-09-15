import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function read(relative) {
  return fs.readFileSync(new URL(`../../${relative}`, import.meta.url), "utf8");
}

const appforge = read("scripts/appforge");
const policy = JSON.parse(read(".appforge/policy.json"));
const agents = read("AGENTS.md");
const play = read(".github/workflows/android-play-release.yml");
const blockers = JSON.parse(read(".appforge/runtime-blockers.json"));
const blueprintTests = read("android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentBlueprintJsonTest.kt");

test("BUG6 fixture removes platform independent of trimIndent whitespace", () => {
  const matches = blueprintTests.match(/Regex\([\s\S]{0,140}\(\?m\)\^\\s\*"platform":\\s\*"ANDROID"/g) || [];
  assert.equal(matches.length, 2);
});

test("full autopilot is fail-stop and auto-merges only after PR CI success", () => {
  assert.match(appforge, /def autopilot\(\):/);
  assert.match(appforge, /APPFORGE FULL AUTOPILOT \/ FAIL-STOP/);
  assert.match(appforge, /ci_watch\(\)[\s\S]{0,300}PR REQUIRED CI: SUCCESS[\s\S]{0,400}merge_pr\(num\)/);
  assert.match(appforge, /APPFORGE FULL AUTOPILOT COMPLETE/);
});

test("Termux reports Android unit execution as deferred", () => {
  assert.match(appforge, /ANDROID UNIT TESTS: DEFERRED \/ CI REQUIRED/);
  assert.match(appforge, /:app:testDebugUnitTest/);
});

test("BUG7 runtime blocker is a hard shipping boundary", () => {
  assert.match(appforge, /require_no_runtime_blockers\(\)/);
  assert.equal(blockers.active[0].id, "BUG-7");
  assert.equal(blockers.active[0].status, "DEVICE_LOG_PENDING");
  assert.equal(blockers.active[0].shipping_blocker, true);
});

test("policy and AGENTS define one-shot delivery", () => {
  assert.equal(policy.delivery_mode, "FULL_AUTOPILOT_FAIL_STOP");
  for (const key of ["auto_commit", "auto_push", "auto_pr", "auto_merge", "auto_deploy", "auto_release", "auto_publish", "stop_on_first_failure"]) {
    assert.equal(policy[key], true, `${key} must be true`);
  }
  assert.match(agents, /FULL AUTOPILOT \/ FAIL-STOP/);
});

test("Play workflow performs a real Play Console upload", () => {
  assert.match(play, /APPFORGE_PLAY_SERVICE_ACCOUNT_JSON/);
  assert.match(play, /r0adkll\/upload-google-play@v1/);
  assert.match(play, /packageName:\s*com\.appforge\.studio/);
  assert.match(play, /APPFORGE_PLAY_TRACK/);
});
