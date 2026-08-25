import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import path from "path";

const root =
  path.resolve(
    "python-android-template"
  );

async function read(
  relative
) {
  return fs.readFile(
    path.join(
      root,
      relative
    ),
    "utf8"
  );
}

test(
  "Python Android template has Chaquopy runtime contract",
  async () => {
    const top =
      await read(
        "build.gradle.kts"
      );

    const app =
      await read(
        "app/build.gradle.kts"
      );

    const activity =
      await read(
        "app/src/main/java/com/appforge/pythonruntime/MainActivity.kt"
      );

    const entry =
      await read(
        "app/src/main/python/appforge_entry.py"
      );

    assert.match(
      top,
      /com\.chaquo\.python.*17\.0\.0/
    );

    assert.match(
      app,
      /version = "3\.11"/
    );

    assert.match(
      app,
      /buildPython/
    );

    assert.match(
      app,
      /requirements\.txt/
    );

    assert.match(
      activity,
      /Python\.start/
    );

    assert.match(
      activity,
      /getModule\(\s*"appforge_entry"/
    );

    assert.match(
      entry,
      /import_module\("main"\)/
    );

    assert.match(
      entry,
      /def run\(\):/
    );
  }
);
