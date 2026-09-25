import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const main =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
      import.meta.url
    ),
    "utf8"
  );

test(
  "local build refreshes technology from real source tree",
  () => {
    assert.match(
      main,
      /BUILD_SOURCE_ENGINE_REFRESH_V1/
    );

    assert.match(
      main,
      /refreshLocalSourceBuildMetadata/
    );

    assert.match(
      main,
      /SourceCapabilityAnalyzer[\s\S]*\.analyze/
    );

    assert.match(
      main,
      /sourceBuildEngine\s*=\s*detected\.buildEngine/
    );

    assert.match(
      main,
      /sourceTechnology\s*=\s*detected\.technologyId/
    );
  }
);

test(
  "build uses refreshed draft instead of stale saved engine",
  () => {
    assert.match(
      main,
      /val verifiedBuildDraft\s*=\s*refreshLocalSourceBuildMetadata/
    );

    assert.match(
      main,
      /verifiedBuildDraft\.autoVersionCode/
    );

    assert.match(
      main,
      /verifiedBuildDraft\.copy\(/
    );

    assert.match(
      main,
      /effectiveBuildDraft\.sourceBuildEngine/
    );
  }
);

test(
  "engine correction is visible in local build logs",
  () => {
    assert.match(
      main,
      /sourceEngineCorrected/
    );

    assert.match(
      main,
      /Proje türü kaynak klasörden yeniden doğrulandı/
    );
  }
);
