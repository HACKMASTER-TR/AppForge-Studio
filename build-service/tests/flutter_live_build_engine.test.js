import test from "node:test";
import assert from "node:assert/strict";

import {
  flutterBuildTargets
} from "../src/flutterBuildEngine.js";

test(
  "Flutter output routing maps APK, AAB and BOTH",
  () => {
    assert.deepEqual(
      flutterBuildTargets(
        "apk"
      ),
      [
        "apk"
      ]
    );

    assert.deepEqual(
      flutterBuildTargets(
        "aab"
      ),
      [
        "appbundle"
      ]
    );

    assert.deepEqual(
      flutterBuildTargets(
        "both"
      ),
      [
        "apk",
        "appbundle"
      ]
    );
  }
);
