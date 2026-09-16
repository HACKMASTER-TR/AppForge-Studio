import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const gateUrl = new URL(
  "../../scripts/appforge-stability-gate",
  import.meta.url
);

test(
  "stability gate uses proot-safe diff validation",
  async () => {
    const source =
      await readFile(
        gateUrl,
        "utf8"
      );

    assert.match(
      source,
      /is_android_proot\(\)/
    );

    assert.match(
      source,
      /safe_worktree_diff_check\(\)/
    );

    assert.match(
      source,
      /git[\s\S]{0,120}diff[\s\S]{0,120}--name-only/
    );

    assert.match(
      source,
      /Android\/proot safe diff check: PASS/
    );
  }
);

test(
  "ordinary Linux still uses native git diff check",
  async () => {
    const source =
      await readFile(
        gateUrl,
        "utf8"
      );

    const start =
      source.indexOf(
        "safe_worktree_diff_check()"
      );

    const end =
      source.indexOf(
        "\n}",
        start
      );

    assert.ok(start >= 0);
    assert.ok(end > start);

    const block =
      source.slice(
        start,
        end
      );

    assert.match(
      block,
      /if ! is_android_proot/
    );

    assert.match(
      block,
      /git diff --check/
    );
  }
);
