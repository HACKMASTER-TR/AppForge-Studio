import test from "node:test";
import assert from "node:assert/strict";
import {
  gradleArguments,
  gradleInvocationPlan,
  gradleJvmOptions,
  gradlePerformanceProfile
} from "../src/gradlePerformance.js";

test("throughput Gradle profile keeps one warm daemon and batches APK and AAB", () => {
  const profile = gradlePerformanceProfile("throughput");
  assert.deepEqual(
    gradleInvocationPlan(["assembleRelease", "bundleRelease"], profile.name),
    [["assembleRelease", "bundleRelease"]]
  );
  assert.equal(profile.maxWorkers, 4);
  assert.equal(profile.incremental, true);
  assert.ok(gradleArguments(["bundleRelease"], profile).includes("--max-workers=4"));
  assert.ok(gradleArguments(["bundleRelease"], profile).includes("--daemon"));
  assert.ok(gradleArguments(["bundleRelease"], profile).includes("--configuration-cache"));
  assert.match(gradleJvmOptions(profile), /-Xmx1024m/);
});

test("low-memory Gradle fallback isolates output tasks", () => {
  const profile = gradlePerformanceProfile("low-memory");
  assert.deepEqual(
    gradleInvocationPlan(["assembleRelease", "bundleRelease"], profile.name),
    [["assembleRelease"], ["bundleRelease"]]
  );
  assert.equal(profile.parallel, false);
  assert.match(gradleJvmOptions(profile), /UseSerialGC/);
});
