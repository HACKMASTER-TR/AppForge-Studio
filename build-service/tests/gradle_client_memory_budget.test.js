import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";

import {
  gradleClientJvmOptions,
  gradleJvmOptions,
  gradlePerformanceProfile
} from "../src/gradlePerformance.js";

test(
  "low-memory Gradle client uses a smaller JVM budget than build daemon",
  () => {
    const profile =
      gradlePerformanceProfile(
        "low-memory"
      );

    const client =
      gradleClientJvmOptions(
        profile
      );

    const daemon =
      gradleJvmOptions(
        profile
      );

    assert.match(
      client,
      /-Xmx96m/
    );

    assert.match(
      client,
      /MaxMetaspaceSize=128m/
    );

    assert.match(
      daemon,
      /-Xmx1536m/
    );

    assert.match(
      daemon,
      /MaxMetaspaceSize=512m/
    );

    assert.notEqual(
      client,
      daemon
    );
  }
);

test(
  "build engine applies the small client JVM budget through GRADLE_OPTS",
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
      /GRADLE_OPTS:\s*gradleClientJvmOptions\(\s*profile\s*\)/
    );
  }
);
