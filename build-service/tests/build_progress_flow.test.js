import test from "node:test";
import assert from "node:assert/strict";
import {
  readFile
} from "node:fs/promises";

test(
  "Studio build progress flows from 1 to 99 and reaches 100 only on success",
  async () => {
    const source =
      await readFile(
        new URL(
          "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
          import.meta.url
        ),
        "utf8"
      );

    assert.match(
      source,
      /var flowingProgress by/
    );

    assert.match(
      source,
      /flowingProgress\s*<\s*99/
    );

    assert.match(
      source,
      /normalizedStatus\s*==\s*"success"/
    );

    assert.match(
      source,
      /flowingProgress\s*<\s*100/
    );

    assert.match(
      source,
      /flowingProgress \+=\s*1/
    );

    assert.match(
      source,
      /backendProgress/
    );
  }
);
