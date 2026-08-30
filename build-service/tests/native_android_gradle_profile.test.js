import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";

import {
  gradleArguments,
  gradleClientJvmOptions,
  gradleInvocationPlan,
  gradleJvmOptions,
  gradlePerformanceProfile
} from "../src/gradlePerformance.js";

test(
  "native Android uses isolated memory-constrained Gradle profile",
  () => {
    const profile =
      gradlePerformanceProfile(
        "native-android"
      );

    assert.equal(
      profile.name,
      "native-android"
    );

    assert.equal(
      profile.maxWorkers,
      1
    );

    assert.equal(
      profile.parallel,
      false
    );

    assert.equal(
      profile.heapMb,
      320
    );

    assert.equal(
      profile.metaspaceMb,
      224
    );

    assert.deepEqual(
      gradleInvocationPlan(
        [
          "assembleRelease",
          "bundleRelease"
        ],
        profile.name
      ),
      [
        ["assembleRelease"],
        ["bundleRelease"]
      ]
    );

    const args =
      gradleArguments(
        ["assembleRelease"],
        profile
      );

    assert.ok(
      args.includes(
        "--no-daemon"
      )
    );

    assert.ok(
      args.includes(
        "--no-watch-fs"
      )
    );

    assert.ok(
      args.includes(
        "--max-workers=1"
      )
    );

    assert.match(
      gradleJvmOptions(
        profile
      ),
      /ReservedCodeCacheSize=96m/
    );

    const client =
      gradleClientJvmOptions(
        profile
      );

    assert.match(
      client,
      /-Xmx64m/
    );

    assert.match(
      client,
      /MaxMetaspaceSize=96m/
    );

    assert.match(
      client,
      /ReservedCodeCacheSize=64m/
    );
  }
);

test(
  "build engine routes android-gradle projects to native profile",
  async () => {
    const source =
      await fs.readFile(
        new URL(
          "../src/buildEngine.js",
          import.meta.url
        ),
        "utf8"
      );

    const start =
      source.indexOf(
        "const preferredGradleProfile"
      );

    assert.ok(
      start >= 0
    );

    const section =
      source.slice(
        start,
        start + 600
      );

    assert.match(
      section,
      /source\.engine\s*===\s*"android-gradle"/
    );

    assert.match(
      section,
      /"native-android"/
    );
  }
);
