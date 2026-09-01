import test from "node:test";
import assert from "node:assert/strict";

import {
  classifyBuildError
} from "../src/buildErrorClassifier.js";

test(
  "network errors are retryable",
  () => {
    const result =
      classifyBuildError(
        new Error("ECONNRESET socket hang up")
      );

    assert.equal(
      result.category,
      "network"
    );

    assert.equal(
      result.retryable,
      true
    );
  }
);

test(
  "signing errors are not retryable",
  () => {
    const result =
      classifyBuildError(
        new Error(
          "ENOENT /tmp/appforge-signing/debug.keystore"
        )
      );

    assert.equal(
      result.category,
      "signing"
    );

    assert.equal(
      result.retryable,
      false
    );
  }
);

test(
  "compile errors are not retryable",
  () => {
    const result =
      classifyBuildError(
        new Error(
          "Unresolved reference in compileReleaseKotlin"
        )
      );

    assert.equal(
      result.category,
      "user-code"
    );

    assert.equal(
      result.retryable,
      false
    );
  }
);
