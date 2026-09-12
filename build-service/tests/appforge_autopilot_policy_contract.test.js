import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const policyUrl =
  new URL("../../.appforge/policy.json", import.meta.url);

test("AppForge Autopilot policy keeps hard gates enabled", async () => {
  const policy = JSON.parse(await readFile(policyUrl, "utf8"));

  assert.equal(policy.hard_gate, true);
  assert.equal(policy.require_fail_zero, true);
  assert.equal(policy.auto_merge, true);
  assert.equal(policy.dashboard_refresh_seconds, 30);
  assert.equal(
    policy.admin_email_sha256,
    "1249d3064d7f482d584f75caf93ea01649f13e4a26d183c4729d2fae5d205589"
  );
  assert.ok(policy.forbidden_patterns.length >= 6);
});
