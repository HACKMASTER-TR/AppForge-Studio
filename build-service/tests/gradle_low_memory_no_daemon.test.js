import test from "node:test";
import assert from "node:assert/strict";
import {
  gradleArguments,
  gradlePerformanceProfile
} from "../src/gradlePerformance.js";

test(
  "low-memory Gradle runs without persistent or single-use daemon override",
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
      args.includes("--no-daemon"),
      true
    );

    assert.equal(
      args.includes("--daemon"),
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

    assert.equal(
      args.includes("--build-cache"),
      true
    );

    assert.equal(
      args.includes("--configuration-cache"),
      true
    );
  }
);

test(
  "balanced profile keeps reusable daemon and daemon JVM override",
  () => {
    const profile =
      gradlePerformanceProfile(
        "balanced"
      );

    const args =
      gradleArguments(
        ["assembleRelease"],
        profile
      );

    assert.equal(
      args.includes("--daemon"),
      true
    );

    assert.equal(
      args.includes("--no-daemon"),
      false
    );

    assert.equal(
      args.some(
        arg =>
          arg.startsWith(
            "-Dorg.gradle.jvmargs="
          )
      ),
      true
    );
  }
);
