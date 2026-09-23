import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test(
  "Studio progress uses device build state; only success reaches 100",
  async () => {
    const source = await readFile(
      new URL(
        "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
        import.meta.url
      ),
      "utf8"
    );

    assert.match(
      source,
      /val backendProgress\s*=\s*progress\.coerceIn\(\s*0,\s*100\s*\)/
    );

    assert.match(
      source,
      /val safeProgress\s*=\s*if\s*\(\s*normalizedStatus\s*==\s*"success"\s*\)\s*\{\s*100\s*\}\s*else\s*\{\s*backendProgress\.coerceAtMost\(99\)\s*\}/
    );

    // The retired timer-based progress must not return.
    assert.doesNotMatch(source, /var flowingProgress by/);
    assert.doesNotMatch(source, /flowingProgress\s*\+=\s*1/);
  }
);
