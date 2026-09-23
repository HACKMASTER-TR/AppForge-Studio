import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl = new URL(
  "../../scripts/appforge",
  import.meta.url
);

test(
  "autopilot defers Android proot Gradle execution before JVM tools",
  async () => {
    const source = await readFile(sourceUrl, "utf8");

    assert.ok(
      source.includes(
        "def is_android_host_environment():"
      )
    );

    assert.ok(
      source.includes(
        'Path("/system/bin/app_process").exists()'
      )
    );

    assert.ok(
      source.includes(
        'Path("/system/bin/getprop").exists()'
      )
    );

    assert.ok(
      source.includes(
        '"android" in release'
      )
    );

    const gate = source.indexOf(
      "def android_unit_test_gate():"
    );

    const androidGuard = source.indexOf(
      "if is_android_host_environment():",
      gate
    );

    const gradleLookup = source.indexOf(
      'gradle = shutil.which("gradle")',
      gate
    );

    assert.ok(gate >= 0);
    assert.ok(androidGuard > gate);
    assert.ok(gradleLookup > androidGuard);

    assert.ok(
      source.includes(
        "ANDROID UNIT TESTS: "
      )
    );

    assert.ok(
      source.includes(
        "DEFERRED / CI REQUIRED"
      )
    );

    assert.ok(
      source.includes(
        "Android/proot host must not start "
      )
    );

    assert.ok(
      source.includes(
        "the local Gradle/JVM suite."
      )
    );

    assert.ok(
      source.includes(
        "android-debug.yml"
      )
    );

    assert.ok(
      source.includes(
        ":app:testDebugUnitTest"
      )
    );
  }
);

test(
  "Android proot avoids crashing cached diff check path",
  async () => {
    const source = await readFile(sourceUrl, "utf8");

    const helper = source.indexOf(
      "def safe_cached_diff_check():"
    );

    assert.ok(helper >= 0);

    const androidGuard = source.indexOf(
      "if not is_android_host_environment():",
      helper
    );

    assert.ok(androidGuard > helper);

    assert.ok(
      source.includes(
        '"--cached"'
      )
    );

    assert.ok(
      source.includes(
        '"--name-only"'
      )
    );

    assert.ok(
      source.includes(
        "STAGED WHITESPACE CHECK: PASS "
      )
    );

    assert.ok(
      source.includes(
        "(Android/proot safe path)"
      )
    );

    const autoCommit = source.indexOf(
      "=== AUTO COMMIT / PUSH ==="
    );

    assert.ok(autoCommit >= 0);

    const safeStage = source.indexOf(
      "safe_stage()",
      autoCommit
    );

    const safeCachedCheck = source.indexOf(
      "safe_cached_diff_check()",
      safeStage
    );

    assert.ok(safeStage > autoCommit);
    assert.ok(safeCachedCheck > safeStage);
  }
);

test(
  "Android proot safe_stage uses verified git add --all path",
  async () => {
    const source = await readFile(sourceUrl, "utf8");

    const start =
      source.indexOf("def safe_stage():");

    const end =
      source.indexOf("\ndef ", start + 1);

    assert.ok(start >= 0);
    assert.ok(end > start);

    const block =
      source.slice(start, end);

    assert.ok(
      block.includes(
        "if is_android_host_environment():"
      )
    );

    assert.ok(
      block.includes(
        '"--all"'
      )
    );

    assert.ok(
      block.includes(
        "*files"
      )
    );
  }
);
