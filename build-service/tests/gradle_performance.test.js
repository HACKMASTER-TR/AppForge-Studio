import test from "node:test";
import assert from "node:assert/strict";
import {
  gradleArguments,
  gradleInvocationPlan,
  gradleJvmOptions,
  gradlePerformanceProfile
} from "../src/gradlePerformance.js";

test("balanced Gradle profile batches APK and AAB in one warm invocation", () => {
  const profile = gradlePerformanceProfile("balanced");
  assert.deepEqual(
    gradleInvocationPlan(["assembleRelease", "bundleRelease"], profile.name),
    [["assembleRelease", "bundleRelease"]]
  );
  assert.equal(profile.maxWorkers, 2);
  assert.equal(profile.incremental, true);
  assert.ok(gradleArguments(["bundleRelease"], profile).includes("--max-workers=2"));
  assert.match(gradleJvmOptions(profile), /-Xmx640m/);
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
