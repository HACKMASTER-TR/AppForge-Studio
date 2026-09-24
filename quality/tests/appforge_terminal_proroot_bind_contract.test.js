import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(
    new URL(`../../${path}`, import.meta.url),
    "utf8"
  );

test(
  "AppForge Proroot binds always use explicit host:guest format",
  async () => {
    const runtime = await read(
      "android-app/app/src/main/java/com/appforge/studio/terminal/ProrootRuntimeContract.kt"
    );

    assert.equal(
      (runtime.match(/"\/dev:\/dev"/g) || []).length,
      2
    );

    assert.equal(
      (runtime.match(/"\/proc:\/proc"/g) || []).length,
      2
    );

    assert.doesNotMatch(
      runtime,
      /"-b",\s*"\/dev",/
    );

    assert.doesNotMatch(
      runtime,
      /"-b",\s*"\/proc",/
    );

    assert.equal(
      (
        runtime.match(
          /"\$\{safeWorkspace\.absolutePath\}:\/workspace"/g
        ) || []
      ).length,
      2
    );
  }
);

test(
  "all Proroot execution paths use the central runtime contract",
  async () => {
    const interactive = await read(
      "android-app/app/src/main/java/com/appforge/studio/terminal/InteractiveLinuxPtySession.kt"
    );

    const panel = await read(
      "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
    );

    const shell = await read(
      "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxShellEngine.kt"
    );

    const lsp = await read(
      "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxLspSession.kt"
    );

    assert.match(
      interactive,
      /buildInteractiveShellArguments/
    );

    assert.match(
      panel,
      /buildInteractiveShellArguments/
    );

    assert.match(
      shell,
      /buildShellArguments/
    );

    assert.match(
      lsp,
      /buildShellArguments/
    );

    for (const source of [
      interactive,
      panel,
      shell,
      lsp
    ]) {
      assert.doesNotMatch(
        source,
        /"-b"\s*,\s*"\/dev"/
      );

      assert.doesNotMatch(
        source,
        /"-b"\s*,\s*"\/proc"/
      );
    }
  }
);
