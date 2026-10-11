import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const main = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  ),
  "utf8"
);

test(
  "Builder step 10 re-reads the persisted active build reference",
  () => {
    assert.match(
      main,
      /BUILDER_ACTIVE_BUILD_REENTRY_GUARD_V25_1/
    );

    assert.match(
      main,
      /remember\(\s*screen,\s*step,\s*buildBusy,\s*buildId,[\s\S]*builderRuntimeProjectKey[\s\S]*activeSingleBuild\(\s*context\s*\)/
    );

    assert.match(
      main,
      /builderActiveBuildReentryGuard/
    );

    assert.match(
      main,
      /builderReentryPendingBuildId/
    );
  }
);

test(
  "Builder re-entry reconnects to the exact persisted Build ID without creating another build",
  () => {
    const start = main.indexOf(
      "BUILDER_ACTIVE_BUILD_REENTRY_V25_1"
    );

    const end = main.indexOf(
      "Build süresi:",
      start
    );

    assert.ok(start >= 0);
    assert.ok(end > start);

    const block =
      main.slice(
        start,
        end
      );

    assert.match(
      block,
      /LaunchedEffect\(\s*screen,\s*step,[\s\S]*builderActiveBuildReference[\s\S]*buildId/
    );

    assert.match(
      block,
      /buildId\s*=\s*reference\.buildId/
    );

    assert.match(
      block,
      /client\.getBuild\(\s*reference\.buildId\s*\)/
    );

    assert.match(
      block,
      /buildRuntime[\s\S]*restoreFromEngine/
    );

    assert.doesNotMatch(
      block,
      /createBuild\(/
    );
  }
);

test(
  "re-entry UI blocks duplicate build before the first real snapshot arrives",
  () => {
    assert.match(
      main,
      /builderReentryUiGuard\s*=\s*builderActiveBuildReentryGuard\s*\|\|/
    );

    assert.match(
      main,
      /builderEffectiveBuildBusy\s*=\s*buildBusy\s*\|\|\s*builderReentryUiGuard/
    );

    assert.match(
      main,
      /step == 10\s*&&\s*builderEffectiveBuildBusy/
    );

    assert.match(
      main,
      /builderEffectiveBuildBusy ->[\s\S]*DERLENİYOR/
    );

    assert.match(
      main,
      /buildBusy\s*\|\|\s*builderReentryUiGuard[\s\S]*Bir derleme zaten devam ediyor/
    );

    assert.match(
      main,
      /buildBusy\s*\|\|\s*builderReentryUiGuard[\s\S]*Derleme devam ederken proje değiştirilemez/
    );
  }
);

test(
  "Builder never advertises Ready zero as proven progress during rebind",
  () => {
    assert.match(
      main,
      /effectiveStatus\s*=\s*if\s*\(\s*reentryPending\s*\)\s*\{\s*"running"/
    );

    assert.match(
      main,
      /reentryPending ->\s*"Aktif derleme geri yükleniyor"/
    );

    assert.match(
      main,
      /if\s*\(\s*reentryPending\s*\)\s*\{\s*stageLabel\s*\}\s*else\s*\{\s*"\$stageLabel • %\$displayProgress"/
    );

    assert.match(
      main,
      /AppForgeBuildProgress\.visible\(\s*effectiveStatus,\s*progress\s*\)/
    );
  }
);

test(
  "terminal snapshot clears persisted tracker before normal re-entry can retrigger",
  () => {
    const start = main.indexOf(
      "BUILDER_ACTIVE_BUILD_REENTRY_V25_1"
    );

    const end = main.indexOf(
      "Build süresi:",
      start
    );

    const block =
      main.slice(
        start,
        end
      );

    const terminal =
      block.indexOf(
        "if (\n                    terminal"
      );

    const clear =
      block.indexOf(
        "BuildProgressService\n                        .clear",
        terminal
      );

    const restore =
      block.indexOf(
        "buildRuntime\n                    .restoreFromEngine",
        clear
      );

    assert.ok(terminal >= 0);
    assert.ok(clear > terminal);
    assert.ok(restore > clear);
  }
);
