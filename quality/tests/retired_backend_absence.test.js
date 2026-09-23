import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const get = p => fs.readFileSync(path.join(root,p), "utf8");
test("remote Build Service and worker tree are retired", () => {
  assert.equal(fs.existsSync(path.join(root,"build-service/server.js")),false);
  assert.equal(fs.existsSync(path.join(root,"build-service/src/jobQueue.js")),false);
  assert.equal(fs.existsSync(path.join(root,"build-service/src/monthlyBuildQuota.js")),false);
  assert.equal(fs.existsSync(path.join(root,"build-service/src/proEntitlements.js")),false);
  assert.equal(fs.existsSync(path.join(root,"build-service/source-worker.js")),false);
  assert.equal(fs.existsSync(path.join(root,"build-service/worker.js")),false);
});
test("active Android app stays device-only and portable outputs remain guarded", () => {
  const client=get("android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt");
  const engine=get("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt");
  assert.match(client,/DeviceBuildEngine\.start/);
  assert.match(engine,/DeviceBuildRuntimeV3/);
  assert.match(engine,/WindowsPortable/);
});
test("test runner and CI no longer depend on retired backend", () => {
  const ci=get(".github/workflows/appforge-stability-gate.yml");
  const terminal=get("android-app/app/src/main/assets/terminal/appforge-test");
  const cli=get("scripts/appforge");
  assert.match(ci,/npm --prefix quality test/);
  assert.match(terminal,/quality\/package\.json/);
  assert.match(cli,/"--prefix", "quality", "test"/);
  assert.doesNotMatch(ci,/build-service\/package-lock\.json/);
});
