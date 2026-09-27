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


test("foreground return removes notification tracking without cancelling the active engine job", () => {
  const start = service.indexOf(
    "ACTIVE_BUILD_FOREGROUND_NOTIFICATION_HANDOFF_V21_3"
  );
  assert.ok(start >= 0);

  const end = service.indexOf(
    "fun stop(",
    start
  );
  assert.ok(end > start);

  const block = service.slice(start, end);

  assert.match(block, /fun onHostResumed\(/);
  assert.match(block, /stop\(\s*context\s*\)/);
  assert.doesNotMatch(block, /DeviceBuildEngine\.cancel/);
  assert.doesNotMatch(block, /DeviceBuildEngine\.snapshot/);
  assert.doesNotMatch(block, /clear\(context\)/);

  assert.match(service, /Intent\.FLAG_ACTIVITY_REORDER_TO_FRONT/);
  assert.match(service, /Intent\.FLAG_ACTIVITY_SINGLE_TOP/);
  assert.doesNotMatch(service, /Intent\.FLAG_ACTIVITY_CLEAR_TOP/);
  assert.match(main, /BuildProgressService\.onHostResumed\(this\)/);
});

test("notification tap hydrates snapshot before consuming navigation and hides the tracker", () => {
  const start = main.indexOf("ACTIVE_BUILD_NOTIFICATION_REBIND_V2");
  assert.ok(start >= 0);

  const block = main.slice(start, start + 4300);
  const restoreIndex = block.indexOf("restoreFromEngine");
  const stopIndex = block.indexOf("BuildProgressService.stop(context)");
  const consumeIndex = block.indexOf("consumeBuildNotificationNavigation");

  assert.ok(restoreIndex >= 0);
  assert.ok(stopIndex > restoreIndex);
  assert.ok(consumeIndex > restoreIndex);
  assert.doesNotMatch(block, /while\s*\(\s*true\s*\)/);
  assert.doesNotMatch(block, /cancelBuild/);
});


test("notification Build ID stays stable until the snapshot rebind completes", () => {
  const seedStart = main.indexOf(
    "NOTIFICATION_BUILD_ID_STABLE_RESTORE_V21_2"
  );
  assert.ok(seedStart >= 0);

  const seedEnd = main.indexOf(
    "val notificationBuildRestoreServerUrl",
    seedStart
  );
  assert.ok(seedEnd > seedStart);

  const seedBlock = main.slice(seedStart, seedEnd);
  assert.match(seedBlock, /buildIdFromNotification/);
  assert.doesNotMatch(seedBlock, /openBuildFromNotification/);

  assert.match(
    main,
    /val initialBuildRestoreId =[\s\S]{0,300}restoredBuildReference[\s\S]{0,300}notificationBuildRestoreId/
  );
  assert.match(
    main,
    /remember\(\s*initialBuildRestoreId\s*\)[\s\S]{0,2500}getBuild\(\s*restoreId\s*\)[\s\S]{0,1200}restoreFromEngine/
  );

  const navStart = main.indexOf(
    "NOTIFICATION_NAVIGATION_DEFERRED_CONSUME_V21_2"
  );
  const navEnd = main.indexOf(
    "hostActivity?.accountActionSequence",
    navStart
  );
  assert.ok(navStart >= 0 && navEnd > navStart);
  assert.doesNotMatch(
    main.slice(navStart, navEnd),
    /consumeBuildNotificationNavigation/
  );

  const rebindStart = main.indexOf(
    "NOTIFICATION_RETURN_ATOMIC_HANDOFF_V21_2"
  );
  assert.ok(rebindStart >= 0);
  assert.match(
    main.slice(rebindStart, rebindStart + 1200),
    /consumeBuildNotificationNavigation/
  );
});


test("notification background handoff starts at pause and closes the late-track race", () => {
  assert.match(
    main,
    /ACTIVE_BUILD_NOTIFICATION_IMMEDIATE_BACKGROUND_V21_3[\s\S]*override fun onPause\(\)[\s\S]*BuildProgressService\.startPending\(this\)/
  );

  assert.match(
    main,
    /override fun onStop\(\)[\s\S]*BuildProgressService\.startPending\(this\)/
  );

  assert.match(
    service,
    /ACTIVE_BUILD_LATE_TRACK_BACKGROUND_START_V21_3[\s\S]*!AppVisibility\.isForeground[\s\S]*startPending/
  );
});

test("notification tap uses an explicit foreground-service handoff and cannot resurrect", () => {
  assert.match(service, /ACTION_HANDOFF_TO_FOREGROUND/);
  assert.match(
    service,
    /ACTIVE_BUILD_NOTIFICATION_SERVICE_HANDOFF_V21_3[\s\S]*foregroundSuppressed[\s\S]*stopForeground\([\s\S]*Service\.STOP_FOREGROUND_REMOVE/
  );
  assert.match(
    service,
    /private fun showNotification\([\s\S]*foregroundSuppressed[\s\S]*return/
  );
  assert.match(
    service,
    /override fun onDestroy\(\)[\s\S]*stopForeground\([\s\S]*Service\.STOP_FOREGROUND_REMOVE/
  );
  assert.match(
    main,
    /NOTIFICATION_TAP_EAGER_DISMISS_V21_3[\s\S]*BuildProgressService\.stop\(this\)/
  );
});

test("foreground resume never clears active build identity on a transient snapshot miss", () => {
  const start = service.indexOf(
    "ACTIVE_BUILD_FOREGROUND_NOTIFICATION_HANDOFF_V21_3"
  );
  assert.ok(start >= 0);

  const end = service.indexOf(
    "fun stop(",
    start
  );
  assert.ok(end > start);

  const block = service.slice(start, end);

  assert.match(block, /stop\(\s*context\s*\)/);
  assert.doesNotMatch(block, /clear\(context\)/);
  assert.doesNotMatch(block, /DeviceBuildEngine\.snapshot/);
});

test("compact build notification exposes numeric progress without expansion", () => {
  assert.match(
    service,
    /\$appName • %\$\{progress\.coerceIn\(0, 100\)\}/
  );
  assert.match(service, /\.setProgress\(\s*100,/);
});
