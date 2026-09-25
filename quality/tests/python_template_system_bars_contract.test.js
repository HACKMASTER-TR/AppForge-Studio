import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const source =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/assets/device-build/python-template/app/src/main/java/com/appforge/pythonruntime/MainActivity.kt",
      import.meta.url
    ),
    "utf8"
  );

test(
  "Python template applies status and navigation bar insets",
  () => {
    assert.match(
      source,
      /setOnApplyWindowInsetsListener/
    );

    assert.match(
      source,
      /WindowInsets\.Type\.systemBars\(\)/
    );

    assert.match(
      source,
      /systemWindowInsetTop/
    );

    assert.match(
      source,
      /systemWindowInsetBottom/
    );

    assert.match(
      source,
      /view\.setPadding\(\s*left,\s*top,\s*right,\s*bottom\s*\)/
    );

    assert.match(
      source,
      /requestApplyInsets\(\)/
    );
  }
);

test(
  "Python safe-area fix stays platform-only",
  () => {
    assert.doesNotMatch(
      source,
      /androidx\.core/
    );

    assert.match(
      source,
      /setContentView\(\s*root\s*\)/
    );
  }
);
