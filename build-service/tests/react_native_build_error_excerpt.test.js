import test from "node:test";
import assert from "node:assert/strict";

import {
  commandFailureExcerpt
} from "../src/reactNativeBuildEngine.js";

test(
  "commandFailureExcerpt leaves short output unchanged",
  () => {
    const text =
      "short failure";

    assert.equal(
      commandFailureExcerpt(
        text,
        200
      ),
      text
    );
  }
);

test(
  "commandFailureExcerpt keeps legacy tail when no root marker exists",
  () => {
    const text =
      "prefix-" +
      "x".repeat(
        400
      ) +
      "-TAIL";

    const excerpt =
      commandFailureExcerpt(
        text,
        120
      );

    assert.equal(
      excerpt,
      text.slice(
        -120
      )
    );
  }
);

test(
  "commandFailureExcerpt preserves Gradle root cause and final stack tail",
  () => {
    const rootCause =
      [
        "FAILURE: Build failed with an exception.",
        "",
        "* What went wrong:",
        "Execution failed for task ':app:compileReleaseKotlin'.",
        "> Unresolved reference: NexBrainRootCause"
      ].join(
        "\n"
      );

    const text =
      "prefix-line\n".repeat(
        120
      ) +
      rootCause +
      "\n" +
      "middle-line\n".repeat(
        120
      ) +
      "org.gradle.internal.tail.Marker\n".repeat(
        80
      );

    const excerpt =
      commandFailureExcerpt(
        text,
        1200
      );

    assert.match(
      excerpt,
      /FAILURE: Build failed with an exception\./
    );

    assert.match(
      excerpt,
      /NexBrainRootCause/
    );

    assert.match(
      excerpt,
      /org\.gradle\.internal\.tail\.Marker/
    );

    assert.ok(
      excerpt.length <=
        1200
    );
  }
);

test(
  "commandFailureExcerpt prioritizes Gradle FAILURE block",
  () => {
    const text =
      "Caused by: harmless setup noise\n" +
      "x".repeat(
        500
      ) +
      "\nFAILURE: Build failed with an exception.\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':app:assembleRelease'.\n" +
      "> ActualRootCause\n" +
      "stack\n".repeat(
        500
      );

    const excerpt =
      commandFailureExcerpt(
        text,
        800
      );

    assert.match(
      excerpt,
      /^FAILURE: Build failed with an exception\./
    );

    assert.match(
      excerpt,
      /ActualRootCause/
    );

    assert.ok(
      excerpt.length <=
        800
    );
  }
);
