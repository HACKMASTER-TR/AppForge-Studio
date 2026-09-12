import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const gateUrl =
  new URL(
    "../../scripts/appforge-stability-gate",
    import.meta.url
  );

test(
  "stability gate uses portable AppForge runtime temp directory",
  async () => {
    const source =
      await readFile(
        gateUrl,
        "utf8"
      );

    assert.doesNotMatch(
      source,
      /\/tmp\/appforge-/
    );

    assert.match(
      source,
      /APPFORGE_TMPDIR/
    );

    assert.match(
      source,
      /\.appforge\/tmp/
    );

    assert.match(
      source,
      /DIFF_LOG=/
    );

    assert.match(
      source,
      /BRAIN_LOG=/
    );

    assert.match(
      source,
      /trap cleanup_tmp EXIT HUP INT TERM/
    );
  }
);
