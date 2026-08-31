import test from "node:test";
import assert from "node:assert/strict";
import {
  gradleArguments,
  gradlePerformanceProfile,
  sourceGradleProfileName
} from "../src/gradlePerformance.js";

test(
  "low-memory Gradle uses ephemeral daemon",
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
  "balanced profile keeps reusable daemon",
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
  }
);

test(
  "Python Android Gradle disables configuration cache",
  () => {
    assert.equal(
      sourceGradleProfileName(
        "python-android",
        "throughput"
      ),
      "python-android"
    );

    const profile =
      gradlePerformanceProfile(
        "python-android"
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
      args.includes("--no-configuration-cache"),
      true
    );

    assert.equal(
      args.includes("--configuration-cache"),
      false
    );

    assert.equal(
      args.includes("--max-workers=1"),
      true
    );

    assert.equal(
      profile.metaspaceMb,
      320
    );

    assert.equal(
      args.includes("--no-watch-fs"),
      true
    );

    for (
      const task of [
        "lintVitalAnalyzeRelease",
        "lintVitalReportRelease",
        "lintVitalRelease"
      ]
    ) {
      const index =
        args.indexOf(task);

      assert.equal(
        index > 0,
        true
      );

      assert.equal(
        args[index - 1],
        "-x"
      );
    }
  }
);
