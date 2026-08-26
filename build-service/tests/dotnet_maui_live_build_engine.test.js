import test from "node:test";
import assert from "node:assert/strict";

import {
  dotnetMauiOutputFormats
} from "../src/dotnetMauiBuildEngine.js";

test(
  ".NET MAUI output routing maps APK, AAB and BOTH",
  () => {
    assert.deepEqual(
      dotnetMauiOutputFormats(
        "apk"
      ),
      [
        "apk"
      ]
    );

    assert.deepEqual(
      dotnetMauiOutputFormats(
        "aab"
      ),
      [
        "aab"
      ]
    );

    assert.deepEqual(
      dotnetMauiOutputFormats(
        "both"
      ),
      [
        "apk",
        "aab"
      ]
    );
  }
);
