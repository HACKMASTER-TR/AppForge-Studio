import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";
import {
  gradleArguments,
  gradleJvmOptions,
  gradlePerformanceProfile
} from "../src/gradlePerformance.js";

test(
  "low-memory Gradle profile overrides daemon JVM args",
  () => {
    const profile =
      gradlePerformanceProfile(
        "low-memory"
      );

    const args =
      gradleArguments(
        ["assembleRelease"],
        profile
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
      1536
    );

    assert.equal(
      profile.metaspaceMb,
      512
    );

    assert.equal(
      profile.codeCacheMb,
      128
    );

    assert.ok(
      args.includes(
        `-Dorg.gradle.jvmargs=${gradleJvmOptions(profile)}`
      )
    );

    assert.ok(
      args.includes(
        "--max-workers=1"
      )
    );

    assert.ok(
      args.includes(
        "-Dorg.gradle.parallel=false"
      )
    );
  }
);

test(
  "memory constrained profiles retry without repeated throughput attempts",
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
        "const runGradleTaskWithRetry"
      );

    const end =
      source.indexOf(
        "const preferredGradleProfile"
      );

    assert.ok(
      start >= 0 &&
      end > start
    );

    const section =
      source.slice(
        start,
        end
      );

    assert.match(
      section,
      /"low-memory"/
    );

    assert.match(
      section,
      /"native-android"/
    );

    assert.match(
      section,
      /\?\s*3\s*:\s*1/
    );
  }
);
