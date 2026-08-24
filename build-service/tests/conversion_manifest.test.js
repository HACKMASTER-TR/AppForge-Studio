import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const fullEngine =
  new URL(
    "../src/buildEngine.js",
    import.meta.url
  );

const fastEngine =
  new URL(
    "../src/fastBuild.js",
    import.meta.url
  );

test(
  "Android FULL build embeds AppForge conversion manifest",
  async () => {
    const text =
      await readFile(
        fullEngine,
        "utf8"
      );

    assert.equal(
      text.includes(
        "appforge-project.json"
      ),
      true
    );

    assert.equal(
      text.includes(
        '"appforge-project"'
      ),
      true
    );

    assert.equal(
      text.includes(
        '"assets/site"'
      ),
      true
    );
  }
);

test(
  "Android FAST APK embeds AppForge conversion manifest",
  async () => {
    const text =
      await readFile(
        fastEngine,
        "utf8"
      );

    assert.equal(
      text.includes(
        '"assets/appforge-project.json"'
      ),
      true
    );

    assert.equal(
      text.includes(
        "apkToExe:"
      ),
      true
    );

    assert.equal(
      text.includes(
        "exeToApk:"
      ),
      true
    );
  }
);
