import test from "node:test";
import assert from "node:assert/strict";

import {
  assertSourceBuildIsolation,
  isUntrustedSourceEngine,
  sourceBuildIsolationStatus
} from "../src/sourceBuildIsolation.js";

test(
  "source execution engines are untrusted",
  () => {
    for (
      const engine of [
        "node-web",
        "python-android",
        "android-gradle",
        "flutter",
        "react-native-android",
        "expo-android",
        "android-ndk",
        "dotnet-maui-android",
        "dotnet-android",
        "unity-android"
      ]
    ) {
      assert.equal(
        isUntrustedSourceEngine(engine),
        true,
        engine
      );
    }

    assert.equal(
      isUntrustedSourceEngine("remote-backend"),
      false
    );
  }
);

test(
  "shared mode stays compatible when isolation is optional",
  () => {
    const status =
      assertSourceBuildIsolation({
        engine: "node-web",
        mode: "shared",
        requireIsolation: false,
        workerCapabilities: []
      });

    assert.equal(status.untrusted, true);
    assert.equal(status.attestedIsolation, false);
    assert.equal(status.blocked, false);
  }
);

test(
  "shared mode fails closed when isolation is required",
  () => {
    assert.throws(
      () =>
        assertSourceBuildIsolation({
          engine: "android-gradle",
          mode: "shared",
          requireIsolation: true,
          workerCapabilities: []
        }),
      /izole Worker zorunlu/
    );
  }
);

test(
  "isolated mode needs explicit Worker capability",
  () => {
    const status =
      sourceBuildIsolationStatus({
        engine: "python-android",
        mode: "dedicated",
        requireIsolation: true,
        workerCapabilities: ["android-api-37"]
      });

    assert.equal(status.isolatedMode, true);
    assert.equal(status.capabilityPresent, false);
    assert.equal(status.blocked, true);
  }
);

test(
  "dedicated Worker with capability passes attestation",
  () => {
    const status =
      assertSourceBuildIsolation({
        engine: "dotnet-android",
        mode: "dedicated",
        requireIsolation: true,
        workerCapabilities: [
          "android-api-37",
          "source-isolation-dedicated"
        ]
      });

    assert.equal(status.attestedIsolation, true);
    assert.equal(status.blocked, false);
  }
);
