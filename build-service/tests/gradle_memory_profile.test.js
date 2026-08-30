import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";
import {
  gradleArguments,
  gradleJvmOptions,
  gradlePerformanceProfile
} from "../src/gradlePerformance.js";

test(
  "low-memory Gradle uses launcher JVM limits without daemon JVM override",
  async () => {
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
      args.some(
        arg =>
          arg.startsWith(
            "-Dorg.gradle.jvmargs="
          )
      ),
      false
    );

    assert.match(
      gradleJvmOptions(profile),
      /-Xmx320m/
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

    const source =
      await fs.readFile(
        new URL(
          "../src/buildEngine.js",
          import.meta.url
        ),
        "utf8"
      );

    assert.match(
      source,
      /GRADLE_OPTS:\s*gradleJvmOptions\(\s*profile\s*\)/
    );
  }
);

test(
  "throughput memory failure falls back without repeated throughput attempts",
  async () => {
    const source =
      await fs.readFile(
        new URL(
          "../src/buildEngine.js",
          import.meta.url
        ),
        "utf8"
      );

    assert.match(
      source,
      /maxAttempts\s*=\s*profile\.name === "low-memory"\s*\?\s*3\s*:\s*1/
    );
  }
);
