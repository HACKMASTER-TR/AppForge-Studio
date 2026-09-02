import test from "node:test";
import assert from "node:assert/strict";
import {
  readFile
} from "node:fs/promises";

const root =
  new URL(
    "../../",
    import.meta.url
  );

const read =
  path =>
    readFile(
      new URL(
        path,
        root
      ),
      "utf8"
    );

test(
  "server applies AppForge problem envelope to API errors",
  async () => {
    const source =
      await read(
        "build-service/server.js"
      );

    assert.ok(
      source.includes(
        'from "./src/problemExplainer.js"'
      )
    );

    assert.ok(
      source.includes(
        "appForgeProblemEnvelope("
      )
    );

    assert.ok(
      source.includes(
        '"/api"'
      )
    );
  }
);

test(
  "Android build advisor no longer uses generic javac task match",
  async () => {
    const source =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
      );

    assert.ok(
      source.includes(
        "configuring project with invalid directory"
      )
    );

    assert.equal(
      source.includes(
        '                    "javac",'
      ),
      false
    );

    assert.equal(
      source.includes(
        '"compiledebugjavawithjavac"'
      ),
      false
    );

    assert.equal(
      source.includes(
        '"compilereleasejavawithjavac"'
      ),
      false
    );
  }
);
