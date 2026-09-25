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

const service = read(
  "android-app/app/src/main/java/com/appforge/studio/BuildProgressService.kt"
);

const main = read(
  "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
);

test(
  "foreground tracker persists active build identity for lifecycle restore",
  () => {
    assert.match(
      service,
      /data class ActiveBuildReference/
    );

    assert.match(
      service,
      /EXTRA_PROJECT_KEY/
    );

    assert.match(
      service,
      /EXTRA_STARTED_AT_MS/
    );

    assert.match(
      service,
      /fun activeSingleBuild\(/
    );

    assert.match(
      service,
      /fun track\([\s\S]*projectKey:\s*String[\s\S]*startedAtMs:\s*Long/
    );
  }
);

test(
  "BuildRuntimeState restores the real DeviceBuildEngine snapshot",
  () => {
    assert.match(
      runtime,
      /fun restoreFromEngine\(/
    );

    assert.match(
      runtime,
      /buildId\.value\s*=\s*snapshot\.buildId/
    );

    assert.match(
      runtime,
      /buildNo\.value\s*=\s*snapshot\.buildNo/
    );

    assert.match(
      runtime,
      /buildBusy\.value\s*=\s*active/
    );

    assert.match(
      runtime,
      /snapshot\.progress/
    );
  }
);

test(
  "Activity recreation reconnects to active engine job and resumes polling",
  () => {
    assert.match(
      main,
      /restoredBuildReference[\s\S]*activeSingleBuild/
    );

    assert.match(
      main,
      /BuildRuntimeState\(\)[\s\S]*restoreFromEngine/
    );

    assert.match(
      main,
      /ACTIVE_DEVICE_BUILD_RESTORE_V1/
    );

    assert.match(
      main,
      /LaunchedEffect\([\s\S]*restoredBuildReference[\s\S]*buildId/
    );

    assert.match(
      main,
      /client\.getBuild\([\s\S]*reference\.buildId/
    );

    assert.match(
      main,
      /var buildBusy by\s*buildRuntime\.buildBusy/
    );

    assert.match(
      main,
      /val buildBusy by\s*runtime\.buildBusy/
    );

    assert.match(
      main,
      /BuildStep\([\s\S]*buildBusy\s*=\s*buildBusy/
    );

    assert.match(
      main,
      /private fun BuildStep\([\s\S]*buildBusy:\s*Boolean/
    );

    assert.doesNotMatch(
      main,
      /var buildBusy by\s*remember\s*\{[\s\S]*mutableStateOf\(false\)/
    );
  }
);

test(
  "restored active build remains the visible Builder job",
  () => {
    const matches =
      main.match(
        /buildProjectKey !=\s*null\s*&&\s*\(\s*buildBusy\s*\|\|/g
      ) || [];

    assert.ok(
      matches.length >= 2
    );
  }
);
