import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(
    new URL(`../../${path}`, import.meta.url),
    "utf8"
  );

const sources = [
  "android-app/app/src/main/java/com/appforge/studio/terminal/InteractiveLinuxPtySession.kt",
  "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
];

function readerSection(source) {
  const start = source.indexOf("readerJob =");
  const end = source.indexOf("waiterJob =", start);

  assert.notEqual(start, -1, "readerJob bulunamadı");
  assert.notEqual(end, -1, "waiterJob bulunamadı");

  return source.slice(start, end);
}

test(
  "PTY reader failures terminate an active process without stealing waiter cleanup",
  async () => {
    for (const path of sources) {
      const source = await read(path);
      const reader = readerSection(source);

      assert.match(
        reader,
        /runCatching\s*\{/
      );

      assert.match(
        reader,
        /InputStreamReader\s*\(/
      );

      assert.match(
        reader,
        /\.onFailure\s*\{[\s\S]*?if\s*\(\s*running\.get\(\)\s*\)\s*\{\s*terminate\(\)\s*\}/
      );

      assert.doesNotMatch(
        reader,
        /running\.set\(false\)/
      );
    }
  }
);

test(
  "PTY waiter remains responsible for final exit notification",
  async () => {
    for (const path of sources) {
      const source = await read(path);

      assert.match(
        source,
        /val shouldNotifyExit\s*=\s*running\.getAndSet\(false\)/
      );

      assert.match(
        source,
        /if \(shouldNotifyExit\) \{\s*onExit\(exitCode\)\s*\}/
      );
    }
  }
);
