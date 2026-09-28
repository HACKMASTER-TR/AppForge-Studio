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

const application = read(
  "android-app/app/src/main/java/com/appforge/studio/AppForgeApplication.kt"
);

const updateGate = read(
  "android-app/app/src/main/java/com/appforge/studio/UpdateGateActivity.kt"
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
    /*
     * V25.1 strengthens the old buildBusy-only visibility rule.
     *
     * BuildStep uses effectiveBuildBusy so a persisted active build
     * remains visible while its first real snapshot is being rebound.
     * The Builder bottom action independently uses
     * builderEffectiveBuildBusy to prevent a duplicate build start.
     */
    assert.match(
      main,
      /buildMatchesCurrentProject\s*=\s*buildProjectKey != null &&[\s\S]*effectiveBuildBusy \|\|/
    );

    assert.match(
      main,
      /builderBuildMatchesCurrentProject\s*=\s*buildProjectKey !=[\s\S]*builderEffectiveBuildBusy \|\|/
    );

    assert.match(
      main,
      /effectiveBuildBusy\s*=\s*buildBusy\s*\|\|\s*reentryPending/
    );

    assert.match(
      main,
      /builderEffectiveBuildBusy\s*=\s*buildBusy\s*\|\|\s*builderReentryUiGuard/
    );
  }
);


test("foreground return removes notification tracking without cancelling the active engine job", () => {
  const start = service.indexOf(
    "ACTIVE_BUILD_FOREGROUND_NOTIFICATION_HANDOFF_V21_4"
  );
  assert.ok(start >= 0);

  const end = service.indexOf(
    "fun onHostPaused(",
    start
  );
  assert.ok(end > start);

  const block = service.slice(start, end);

  assert.match(block, /fun onHostResumed\(/);
  assert.match(block, /hostForeground\s*=\s*true/);
  assert.match(block, /stop\(\s*context\s*\)/);
  assert.doesNotMatch(block, /DeviceBuildEngine\.cancel/);
  assert.doesNotMatch(block, /DeviceBuildEngine\.snapshot/);
  assert.doesNotMatch(block, /clear\(context\)/);

  assert.match(service, /Intent\.FLAG_ACTIVITY_REORDER_TO_FRONT/);
  assert.match(service, /Intent\.FLAG_ACTIVITY_SINGLE_TOP/);
  assert.doesNotMatch(service, /Intent\.FLAG_ACTIVITY_CLEAR_TOP/);
  assert.match(main, /BuildProgressService\.onHostResumed\(this\)/);
});

test("notification tap hydrates snapshot before navigation and hides the tracker", () => {
  const effectStart = main.indexOf(
    "ACTIVE_BUILD_NOTIFICATION_REBIND_V2"
  );
  assert.ok(effectStart >= 0);

  const effectEnd = main.indexOf(
    "var conversionApkUri",
    effectStart
  );
  assert.ok(effectEnd > effectStart);

  const restoreIndex = main.indexOf(
    "buildRuntime.restoreFromEngine(",
    effectStart
  );
  const handoffMarkerIndex = main.indexOf(
    "NOTIFICATION_RETURN_ATOMIC_HANDOFF_V21_4",
    restoreIndex
  );
  const screenIndex = main.indexOf(
    "screen = AppScreen.BUILDER",
    handoffMarkerIndex
  );
  const stopIndex = main.indexOf(
    "BuildProgressService.stop(context)",
    handoffMarkerIndex
  );
  const consumeIndex = main.indexOf(
    "activity.consumeBuildNotificationNavigation()",
    handoffMarkerIndex
  );

  assert.ok(restoreIndex > effectStart && restoreIndex < effectEnd);
  assert.ok(handoffMarkerIndex > restoreIndex && handoffMarkerIndex < effectEnd);
  assert.ok(screenIndex > handoffMarkerIndex && screenIndex < effectEnd);
  assert.ok(stopIndex > screenIndex && stopIndex < effectEnd);
  assert.ok(consumeIndex > stopIndex && consumeIndex < effectEnd);

  const effectBlock = main.slice(effectStart, effectEnd);
  assert.doesNotMatch(effectBlock, /while\s*\(\s*true\s*\)/);
  assert.doesNotMatch(effectBlock, /cancelBuild/);
});

test("BuildRuntimeState is a single owner and notification ID only hydrates it", () => {
  assert.match(main, /BUILD_RUNTIME_SINGLE_OWNER_V21_4/);
  assert.match(main, /val buildRuntime =\s*remember\s*\{\s*BuildRuntimeState\(\)/);
  assert.doesNotMatch(main, /remember\(\s*initialBuildRestoreId\s*\)\s*\{\s*BuildRuntimeState\(\)/);
  assert.match(main, /NOTIFICATION_NAVIGATION_AFTER_HYDRATE_V21_4/);
  const rebind = main.indexOf("NOTIFICATION_RETURN_ATOMIC_HANDOFF_V21_4");
  assert.ok(rebind >= 0);
  assert.match(main.slice(rebind, rebind + 1000), /screen\s*=\s*AppScreen\.BUILDER[\s\S]*BuildProgressService\.stop\(context\)[\s\S]*consumeBuildNotificationNavigation/);
});

test("notification background handoff starts at pause and closes the late-track race", () => {
  assert.match(main, /BUILD_NOTIFICATION_HOST_OWNERSHIP_V21_4[\s\S]*override fun onPause\(\)[\s\S]*BuildProgressService\.onHostPaused\(this\)/);
  assert.match(service, /ACTIVE_BUILD_LATE_TRACK_BACKGROUND_START_V21_4[\s\S]*!hostForeground[\s\S]*startPending/);
  assert.match(service, /BUILD_NOTIFICATION_START_GUARD_V21_4[\s\S]*hostForeground[\s\S]*return/);
});

test("notification tap directly stops foreground tracking and cannot resurrect", () => {
  assert.match(service, /ACTIVE_BUILD_FOREGROUND_NOTIFICATION_HANDOFF_V21_4[\s\S]*hostForeground\s*=\s*true[\s\S]*stop\(context\)/);
  assert.match(service, /fun stop\([\s\S]*hostForeground\s*=\s*true[\s\S]*context\.stopService/);
  assert.match(service, /private fun showNotification\([\s\S]*foregroundSuppressed\s*\|\|[\s\S]*hostForeground/);
  assert.doesNotMatch(service, /ACTION_HANDOFF_TO_FOREGROUND/);
});

test("foreground resume preserves active build identity and suppresses notification publishing", () => {
  const start = service.indexOf("ACTIVE_BUILD_FOREGROUND_NOTIFICATION_HANDOFF_V21_4");
  assert.ok(start >= 0);
  const end = service.indexOf("fun clear(", start);
  assert.ok(end > start);
  const block = service.slice(start, end);
  assert.match(block, /hostForeground\s*=\s*true/);
  assert.match(block, /context\.stopService/);
  assert.doesNotMatch(block, /clear\(context\)/);
  assert.doesNotMatch(block, /DeviceBuildEngine\.snapshot/);
});

test("compact build notification exposes numeric progress without expansion", () => {
  assert.match(service, /\$appName • %\$\{progress\.coerceIn\(0, 100\)\}/);
  assert.match(service, /\.setProgress\(\s*100,/);
  assert.match(service, /BUILD_NOTIFICATION_IMMEDIATE_DISPLAY_V21_4[\s\S]*FOREGROUND_SERVICE_IMMEDIATE/);
});


test("approved notification return bypasses the update gate without finishing MainActivity", () => {
  const marker = application.indexOf(
    "BUILD_NOTIFICATION_GATE_SESSION_BYPASS_V21_5"
  );
  assert.ok(marker >= 0);

  const block = application.slice(
    marker,
    marker + 2200
  );

  const approvedIndex = block.indexOf(
    "UpdateGateSession.isApproved()"
  );
  const returnIndex = block.indexOf("return", approvedIndex);
  const redirectIndex = block.indexOf(
    "activity.startActivity(gate)"
  );
  const finishIndex = block.indexOf(
    "activity.finish()"
  );

  assert.ok(approvedIndex >= 0);
  assert.ok(returnIndex > approvedIndex);
  assert.ok(redirectIndex > returnIndex);
  assert.ok(finishIndex > redirectIndex);

  assert.doesNotMatch(
    block,
    /notificationEntry/
  );
});

test("cold notification entry still passes through the update gate and preserves extras", () => {
  assert.match(
    application,
    /val gate = Intent\([\s\S]*UpdateGateActivity::class\.java[\s\S]*original\?\.extras\?\.let\(::putExtras\)/
  );

  assert.match(
    updateGate,
    /UpdateGateSession\.approve\(\)[\s\S]*putExtra\("appforge_gate_checked", true\)/
  );

  assert.match(
    updateGate,
    /original\?\.extras\?\.let\(::putExtras\)/
  );
});
