import test from "node:test";
import assert from "node:assert/strict";
import {
  ScaleAdmissionControllerV10,
  TenantFairQueueV10,
  sanitizeTelemetryV10,
  scopedCacheKeyV10,
  scopeHashV10,
} from "../appforge-agent-scale-v10.js";

const base = () => ({
  tenantId: "tenant-a",
  jobId: "job-1",
  estimatedGeneratedBytes: 1024,
});

test("V10 admission runs immediately under capacity", () => {
  const result = new ScaleAdmissionControllerV10().evaluate(base());
  assert.equal(result.disposition, "RUN_NOW");
});

test("V10 queues when per-tenant concurrency is saturated", () => {
  const result = new ScaleAdmissionControllerV10().evaluate({
    ...base(), load: { runningTenant: 4 },
  });
  assert.equal(result.disposition, "QUEUED");
});

test("V10 blocks when daily build quota is exhausted", () => {
  const result = new ScaleAdmissionControllerV10().evaluate({
    ...base(), usage: { dailyBuilds: 250 },
  });
  assert.equal(result.disposition, "BLOCKED");
  assert.equal(result.reason, "DAILY_BUILD_QUOTA");
});

test("V10 blocks oversized generated output", () => {
  const result = new ScaleAdmissionControllerV10({ maxGeneratedBytesPerJob: 1024 }).evaluate({
    ...base(), estimatedGeneratedBytes: 1025,
  });
  assert.equal(result.reason, "JOB_OUTPUT_QUOTA");
});

test("V10 cache keys are tenant isolated", () => {
  const common = { projectDigest: "a".repeat(64), stage: "BUILD", inputFingerprint: "same" };
  assert.notEqual(
    scopedCacheKeyV10({ tenantId: "tenant-a", ...common }),
    scopedCacheKeyV10({ tenantId: "tenant-b", ...common }),
  );
});

test("V10 public scopes do not expose raw tenant IDs", () => {
  const raw = "customer-private-name";
  const hashed = scopeHashV10(raw);
  assert.notEqual(hashed, raw);
  assert.equal(hashed.length, 20);
});

test("V10 telemetry redacts secret-like values", () => {
  const safe = sanitizeTelemetryV10({ msg: "ghp_123456789012345678901234567890" });
  assert.equal(safe.msg.includes("ghp_"), false);
  assert.match(safe.msg, /\[REDACTED\]/);
});

test("V10 fair queue rotates across tenants", () => {
  const queue = new TenantFairQueueV10();
  queue.offer({ tenantId: "tenant-a", jobId: "a1" });
  queue.offer({ tenantId: "tenant-a", jobId: "a2" });
  queue.offer({ tenantId: "tenant-b", jobId: "b1" });
  assert.equal(queue.poll().jobId, "a1");
  assert.equal(queue.poll().jobId, "b1");
  assert.equal(queue.poll().jobId, "a2");
});

test("V10 fair queue enforces tenant depth", () => {
  const queue = new TenantFairQueueV10({ maxTenantQueueDepth: 1 });
  assert.equal(queue.offer({ tenantId: "tenant-a", jobId: "a1" }), true);
  assert.equal(queue.offer({ tenantId: "tenant-a", jobId: "a2" }), false);
});

test("V10 keeps manual review signal in admission metadata", () => {
  const result = new ScaleAdmissionControllerV10().evaluate({
    ...base(), manualReviewRequired: true,
  });
  assert.equal(result.disposition, "RUN_NOW");
  assert.equal(result.manualReviewRequired, true);
});
