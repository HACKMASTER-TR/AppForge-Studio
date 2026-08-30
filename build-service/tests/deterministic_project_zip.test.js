import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";

test(
  "Android project ZIP uses content fingerprint and deterministic entries",
  async () => {
    const source =
      await fs.readFile(
        new URL(
          "../../android-app/app/src/main/java/com/appforge/studio/io/ZipUtils.kt",
          import.meta.url
        ),
        "utf8"
      );

    assert.match(
      source,
      /projectFiles/
    );

    assert.match(
      source,
      /\.sortedBy/
    );

    assert.match(
      source,
      /digest\.update\(\s*buffer,\s*0,\s*count\s*\)/
    );

    assert.doesNotMatch(
      source,
      /lastModified\(\)/
    );

    assert.match(
      source,
      /ZipEntry\(\s*rel\s*\)\.apply\s*\{\s*time\s*=\s*0L/
    );
  }
);
