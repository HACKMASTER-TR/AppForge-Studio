import test from "node:test";
import assert from "node:assert/strict";

import {
  reactNativeBuildTargets,
  selectReactNativeGradle
} from "../src/reactNativeBuildEngine.js";

test(
  "React Native output routing maps APK, AAB and BOTH",
  () => {
    assert.deepEqual(
      reactNativeBuildTargets(
        "apk"
      ),
      [
        ":app:assembleRelease"
      ]
    );

    assert.deepEqual(
      reactNativeBuildTargets(
        "aab"
      ),
      [
        ":app:bundleRelease"
      ]
    );

    assert.deepEqual(
      reactNativeBuildTargets(
        "both"
      ),
      [
        ":app:assembleRelease",
        ":app:bundleRelease"
      ]
    );
  }
);

test(
  "React Native Gradle selector avoids uploaded wrapper",
  () => {
    const previousRn =
      process.env.REACT_NATIVE_GRADLE_HOME;

    const previousGradle =
      process.env.GRADLE_HOME;

    try {
      process.env.REACT_NATIVE_GRADLE_HOME =
        "/opt/gradle/gradle-8.14.3";

      process.env.GRADLE_HOME =
        "/opt/gradle/gradle-9.3.1";

      assert.equal(
        selectReactNativeGradle(
          8
        ),
        "/opt/gradle/gradle-8.14.3/bin/gradle"
      );

      assert.equal(
        selectReactNativeGradle(
          9
        ),
        "/opt/gradle/gradle-9.3.1/bin/gradle"
      );
    } finally {
      if (
        previousRn ===
          undefined
      ) {
        delete process.env.REACT_NATIVE_GRADLE_HOME;
      } else {
        process.env.REACT_NATIVE_GRADLE_HOME =
          previousRn;
      }

      if (
        previousGradle ===
          undefined
      ) {
        delete process.env.GRADLE_HOME;
      } else {
        process.env.GRADLE_HOME =
          previousGradle;
      }
    }
  }
);
