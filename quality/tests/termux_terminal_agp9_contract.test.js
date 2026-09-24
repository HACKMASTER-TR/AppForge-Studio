import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "..", "..");

const read = (...parts) =>
  fs.readFile(path.join(repoRoot, ...parts), "utf8");

test(
  "Termux Android library modules do not declare targetSdk under AGP 9",
  async () => {
    const files = [
      [
        "android-app",
        "termux-terminal-emulator",
        "build.gradle.kts",
      ],
      [
        "android-app",
        "termux-terminal-view",
        "build.gradle.kts",
      ],
    ];

    for (const parts of files) {
      const source = await read(...parts);

      assert.equal(
        /^\s*targetSdk\s*=/m.test(source),
        false,
        `${parts.join("/")} must not declare targetSdk in LibraryDefaultConfig`,
      );
    }
  },
);

test(
  "AppForge application still owns targetSdk 37",
  async () => {
    const source = await read(
      "android-app",
      "app",
      "build.gradle.kts",
    );

    assert.match(
      source,
      /^\s*targetSdk\s*=\s*37\s*$/m,
    );
  },
);
