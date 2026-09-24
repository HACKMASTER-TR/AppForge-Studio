import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const here =
  path.dirname(
    fileURLToPath(import.meta.url),
  );

const repoRoot =
  path.resolve(
    here,
    "..",
    "..",
  );

test(
  "AppForge Termux terminal adapter owns no Compose scroll viewport",
  async () => {
    const adapter =
      await fs.readFile(
        path.join(
          repoRoot,
          "android-app",
          "app",
          "src",
          "main",
          "java",
          "com",
          "appforge",
          "studio",
          "terminal",
          "TermuxTerminalCoreAdapter.kt",
        ),
        "utf8",
      );

    const gradle =
      await fs.readFile(
        path.join(
          repoRoot,
          "android-app",
          "app",
          "build.gradle.kts",
        ),
        "utf8",
      );

    for (
      const marker of [
        "TerminalSession(",
        "TerminalView(",
        "attachSession(",
        "onScreenUpdated()",
        "TermuxTerminalCoreHost",
        "TermuxTerminalLaunchSpec",
      ]
    ) {
      assert.ok(
        adapter.includes(marker),
        `Missing Termux adapter marker: ${marker}`,
      );
    }

    for (
      const forbidden of [
        "LazyColumn(",
        "rememberLazyListState(",
        "rememberScrollState(",
        "scrollToItem(",
        "animateScrollToItem(",
      ]
    ) {
      assert.equal(
        adapter.includes(forbidden),
        false,
        `Termux core adapter must not own Compose viewport: ${forbidden}`,
      );
    }

    assert.ok(
      gradle.includes(
        'implementation(project(":termux-terminal-view"))',
      ),
      "App module must depend on vendored Termux terminal view",
    );
  },
);
