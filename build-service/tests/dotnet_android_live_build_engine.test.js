import test from "node:test";
import assert from "node:assert/strict";

import {
  dotnetAndroidOutputFormats
} from "../src/dotnetAndroidBuildEngine.js";

test(
  "generic .NET Android output routing supports APK AAB BOTH",
  () => {
    assert.deepEqual(
      dotnetAndroidOutputFormats(
        "apk"
      ),
      [
        "apk"
      ]
    );

    assert.deepEqual(
      dotnetAndroidOutputFormats(
        "aab"
      ),
      [
        "aab"
      ]
    );

    assert.deepEqual(
      dotnetAndroidOutputFormats(
        "both"
      ),
      [
        "apk",
        "aab"
      ]
    );
  }
);
