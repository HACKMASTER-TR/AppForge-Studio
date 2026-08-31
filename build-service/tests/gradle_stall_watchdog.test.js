import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";

test(
  "Gradle worker uses low-memory default and stall watchdog",
  async () => {
    const configSource =
      await fs.readFile(
        new URL("../src/config.js", import.meta.url),
        "utf8"
      );

    const buildSource =
      await fs.readFile(
        new URL("../src/buildEngine.js", import.meta.url),
        "utf8"
      );

    assert.match(
      configSource,
      /GRADLE_PERFORMANCE_PROFILE[\s\S]*?"low-memory"/
    );

    assert.match(
      configSource,
      /GRADLE_STALL_TIMEOUT_MS[\s\S]*?180_000/
    );

    assert.match(
      buildSource,
      /stallWatchdog/
    );

    assert.match(
      buildSource,
      /GradleStallTimeout/
    );

    assert.match(
      buildSource,
      /config\.gradleStallTimeoutMs/
    );
  }
);
