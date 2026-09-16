import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl = new URL(
  "../../scripts/appforge",
  import.meta.url
);

test(
  "local gate routes diff check through proot-safe helper",
  async () => {
    const source =
      await readFile(
        sourceUrl,
        "utf8"
      );

    const start =
      source.indexOf(
        "def local_gate():"
      );

    const end =
      source.indexOf(
        "\ndef ",
        start + 1
      );

    assert.ok(start >= 0);
    assert.ok(end > start);

    const block =
      source.slice(
        start,
        end
      );

    assert.ok(
      block.includes(
        "safe_worktree_diff_check()"
      )
    );

    assert.doesNotMatch(
      block,
      /run\(\["git",\s*"diff",\s*"--check"\]\)/
    );
  }
);

test(
  "worktree diff helper preserves native Git check off Android",
  async () => {
    const source =
      await readFile(
        sourceUrl,
        "utf8"
      );

    const start =
      source.indexOf(
        "def safe_worktree_diff_check():"
      );

    const end =
      source.indexOf(
        "\ndef ",
        start + 1
      );

    assert.ok(start >= 0);
    assert.ok(end > start);

    const block =
      source.slice(
        start,
        end
      );

    assert.ok(
      block.includes(
        "if not is_android_host_environment():"
      )
    );

    assert.ok(
      block.includes(
        '"diff",'
      )
    );

    assert.ok(
      block.includes(
        '"--check",'
      )
    );

    assert.ok(
      block.includes(
        "WORKTREE WHITESPACE CHECK: PASS"
      )
    );
  }
);

test(
  "cached diff helper does not recursively call itself on normal hosts",
  async () => {
    const source =
      await readFile(
        sourceUrl,
        "utf8"
      );

    const start =
      source.indexOf(
        "def safe_cached_diff_check():"
      );

    const end =
      source.indexOf(
        "\ndef ",
        start + 1
      );

    assert.ok(start >= 0);
    assert.ok(end > start);

    const block =
      source.slice(
        start,
        end
      );

    assert.ok(
      block.includes(
        '"--cached",'
      )
    );

    assert.ok(
      block.includes(
        '"--check",'
      )
    );

    const recursive =
      block.indexOf(
        "safe_cached_diff_check()",
        block.indexOf(
          "if not is_android_host_environment():"
        )
      );

    assert.equal(
      recursive,
      -1
    );
  }
);
