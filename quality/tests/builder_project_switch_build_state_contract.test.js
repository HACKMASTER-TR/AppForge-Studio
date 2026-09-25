import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = path =>
  fs.readFileSync(
    new URL("../../" + path, import.meta.url),
    "utf8"
  );

const runtime = read(
  "android-app/app/src/main/java/com/appforge/studio/BuildRuntimeState.kt"
);

const main = read(
  "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
);

test(
  "project switch clears transient build runtime",
  () => {
    assert.match(
      runtime,
      /fun resetForProjectChange\(\)/
    );

    assert.match(
      runtime,
      /buildProjectKey\.value\s*=\s*null/
    );

    assert.match(
      runtime,
      /buildId\.value\s*=\s*null/
    );

    assert.match(
      runtime,
      /apkUrl\.value\s*=\s*null/
    );

    assert.match(
      runtime,
      /aabUrl\.value\s*=\s*null/
    );

    assert.match(
      runtime,
      /exeUrl\.value\s*=\s*null/
    );

    assert.match(
      runtime,
      /progress\.intValue\s*=\s*0/
    );

    assert.match(
      runtime,
      /logs\.value\s*=\s*emptyList\(\)/
    );

    assert.match(
      runtime,
      /preflight\.value\s*=\s*emptyList\(\)/
    );
  }
);

test(
  "builder detects stale project build state",
  () => {
    assert.match(
      main,
      /PROJECT_SWITCH_BUILD_STATE_RESET_V1/
    );

    assert.match(
      main,
      /previousBuildKey !=\s*currentProjectKey/
    );

    assert.match(
      main,
      /resetForProjectChange\(\)/
    );

    assert.match(
      main,
      /if \(\s*buildBusy\s*\)/
    );
  }
);

test(
  "stale outputs cannot hide new project build action",
  () => {
    assert.match(
      main,
      /builderBuildMatchesCurrentProject/
    );

    assert.match(
      main,
      /builderBuildOutputReady\s*=\s*builderBuildMatchesCurrentProject\s*&&/
    );
  }
);
