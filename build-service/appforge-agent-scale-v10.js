import crypto from "node:crypto";

export const DEFAULT_SCALE_POLICY_V10 = Object.freeze({
  maxGlobalQueueDepth: 2000,
  maxTenantQueueDepth: 64,
  maxConcurrentGlobal: 128,
  maxConcurrentPerTenant: 4,
  maxDailyBuildsPerTenant: 250,
  maxDailyAiRequestsPerTenant: 2000,
  maxGeneratedBytesPerJob: 64 * 1024 * 1024,
});

const SCOPE_ID = /^[A-Za-z0-9][A-Za-z0-9_.-]{0,95}$/;
const SECRET_PATTERNS = [
  /ghp_[A-Za-z0-9]{20,}/g,
  /github_pat_[A-Za-z0-9_]{20,}/g,
  /\bsk-[A-Za-z0-9_-]{20,}\b/g,
  /\bAKIA[0-9A-Z]{16}\b/g,
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/g,
];

function sha256(value) {
  return crypto.createHash("sha256").update(String(value)).digest("hex");
}

export function assertScopeId(value, label = "scope") {
  const normalized = String(value ?? "").trim();
  if (!SCOPE_ID.test(normalized)) {
    throw new Error(`${label} is invalid`);
  }
  return normalized;
}

export function scopeHashV10(value) {
  return sha256(assertScopeId(value)).slice(0, 20);
}

export function scopedCacheKeyV10({ tenantId, projectDigest, stage, inputFingerprint }) {
  const tenantScope = scopeHashV10(tenantId);
  if (!/^[a-fA-F0-9]{32,128}$/.test(String(projectDigest ?? ""))) {
    throw new Error("projectDigest must be hex");
  }
  if (!/^[A-Z_]{2,32}$/.test(String(stage ?? ""))) {
    throw new Error("stage is invalid");
  }
  const fingerprint = String(inputFingerprint ?? "");
  if (fingerprint.length < 1 || fingerprint.length > 512) {
    throw new Error("inputFingerprint size is invalid");
  }
  return `v10:${sha256(["v10", tenantScope, projectDigest.toLowerCase(), stage, fingerprint].join("|"))}`;
}

export function sanitizeTelemetryV10(attributes = {}) {
  const output = {};
  for (const [key, rawValue] of Object.entries(attributes).slice(0, 32)) {
    if (!/^[A-Za-z][A-Za-z0-9_.-]{0,63}$/.test(key)) continue;
    let value = String(rawValue ?? "").slice(0, 1024);
    for (const pattern of SECRET_PATTERNS) value = value.replace(pattern, "[REDACTED]");
    output[key] = value;
  }
  return output;
}

export class ScaleAdmissionControllerV10 {
  constructor(policy = {}) {
    this.policy = { ...DEFAULT_SCALE_POLICY_V10, ...policy };
    if (this.policy.maxConcurrentPerTenant > this.policy.maxConcurrentGlobal) {
      throw new Error("tenant concurrency cannot exceed global concurrency");
    }
  }

  evaluate({ tenantId, jobId, estimatedGeneratedBytes = 0, load = {}, usage = {}, manualReviewRequired = false }) {
    const tenantScope = scopeHashV10(tenantId);
    const jobScope = scopeHashV10(jobId);
    const p = this.policy;
    const q = {
      globalQueueDepth: 0,
      tenantQueueDepth: 0,
      runningGlobal: 0,
      runningTenant: 0,
      ...load,
    };
    const u = { dailyBuilds: 0, dailyAiRequests: 0, ...usage };

    const blocked = (reason) => ({
      disposition: "BLOCKED",
      reason,
      tenantScope,
      jobScope,
      manualReviewRequired: Boolean(manualReviewRequired),
    });

    if (estimatedGeneratedBytes > p.maxGeneratedBytesPerJob) return blocked("JOB_OUTPUT_QUOTA");
    if (u.dailyBuilds >= p.maxDailyBuildsPerTenant) return blocked("DAILY_BUILD_QUOTA");
    if (u.dailyAiRequests >= p.maxDailyAiRequestsPerTenant) return blocked("DAILY_AI_QUOTA");
    if (q.globalQueueDepth >= p.maxGlobalQueueDepth) return blocked("GLOBAL_QUEUE_FULL");
    if (q.tenantQueueDepth >= p.maxTenantQueueDepth) return blocked("TENANT_QUEUE_FULL");

    const queueNeeded = q.runningGlobal >= p.maxConcurrentGlobal || q.runningTenant >= p.maxConcurrentPerTenant;
    return {
      disposition: queueNeeded ? "QUEUED" : "RUN_NOW",
      reason: queueNeeded ? "CAPACITY_QUEUE" : "CAPACITY_AVAILABLE",
      tenantScope,
      jobScope,
      manualReviewRequired: Boolean(manualReviewRequired),
    };
  }
}

export class TenantFairQueueV10 {
  constructor(policy = {}) {
    this.policy = { ...DEFAULT_SCALE_POLICY_V10, ...policy };
    this.queues = new Map();
    this.rotation = [];
    this.totalDepth = 0;
  }

  offer(job) {
    const tenantId = assertScopeId(job?.tenantId, "tenantId");
    assertScopeId(job?.jobId, "jobId");
    if (this.totalDepth >= this.policy.maxGlobalQueueDepth) return false;
    let queue = this.queues.get(tenantId);
    if (!queue) {
      queue = [];
      this.queues.set(tenantId, queue);
    }
    if (queue.length >= this.policy.maxTenantQueueDepth) return false;
    const wasEmpty = queue.length === 0;
    queue.push({ ...job, tenantId });
    this.totalDepth += 1;
    if (wasEmpty) this.rotation.push(tenantId);
    return true;
  }

  poll() {
    while (this.rotation.length) {
      const tenantId = this.rotation.shift();
      const queue = this.queues.get(tenantId);
      if (!queue?.length) {
        this.queues.delete(tenantId);
        continue;
      }
      const job = queue.shift();
      this.totalDepth -= 1;
      if (queue.length) this.rotation.push(tenantId);
      else this.queues.delete(tenantId);
      return job;
    }
    return null;
  }

  depth() {
    return this.totalDepth;
  }

  tenantDepth(tenantId) {
    return this.queues.get(assertScopeId(tenantId, "tenantId"))?.length ?? 0;
  }
}
