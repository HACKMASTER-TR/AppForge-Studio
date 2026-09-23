import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const runtimePath =
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/ProrootRuntimeContract.kt";

const readRuntime = () =>
  readFile(
    new URL(runtimePath, import.meta.url),
    "utf8"
  );

test(
  "PRoot creates the /workspace mount point before launching",
  async () => {
    const source = await readRuntime();

    assert.match(
      source,
      /private fun ensureWorkspaceMountPoint\s*\(/
    );

    assert.match(
      source,
      /File\s*\(\s*rootfs,\s*"workspace"\s*\)/
    );

    assert.match(
      source,
      /mountPoint\.mkdirs\(\)/
    );

    assert.match(
      source,
      /mountPoint\.isDirectory/
    );

    assert.equal(
      (
        source.match(
          /ensureWorkspaceMountPoint\s*\(\s*safeRootfs\s*\)/g
        ) || []
      ).length,
      2
    );
  }
);

test(
  "both PRoot launch modes bind and enter /workspace",
  async () => {
    const source = await readRuntime();

    assert.equal(
      (
        source.match(
          /\$\{safeWorkspace\.absolutePath\}:\/workspace/g
        ) || []
      ).length,
      2
    );

    assert.equal(
      (
        source.match(
          /"-w",\s*"\/workspace"/g
        ) || []
      ).length,
      2
    );
  }
);
