import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(
    new URL(`../../${path}`, import.meta.url),
    "utf8"
  );

const paths = [
  "android-app/app/src/main/java/com/appforge/studio/terminal/InteractiveLinuxPtySession.kt",
  "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
];

test(
  "PTY waiter only reports process exit while session is still active",
  async () => {
    for (const path of paths) {
      const source = await read(path);

      assert.match(
        source,
        /val shouldNotifyExit\s*=\s*running\.getAndSet\(false\)/
      );

      assert.match(
        source,
        /closeDescriptors\(\)\s*if \(shouldNotifyExit\) \{\s*onExit\(exitCode\)\s*\}/
      );

      assert.doesNotMatch(
        source,
        /running\.set\(false\)\s*closeDescriptors\(\)\s*onExit\(exitCode\)/
      );
    }
  }
);

test(
  "PTY close marks session inactive before terminate and descriptor cleanup",
  async () => {
    for (const path of paths) {
      const source = await read(path);

      assert.match(
        source,
        /if \(running\.getAndSet\(false\)\) \{\s*terminate\(\)\s*\}/
      );

      assert.match(
        source,
        /readerJob\?\.cancel\(\)/
      );

      assert.match(
        source,
        /waiterJob\?\.cancel\(\)/
      );

      assert.match(
        source,
        /closeDescriptors\(\)/
      );
    }
  }
);
