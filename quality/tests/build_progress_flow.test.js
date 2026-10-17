import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const main = await readFile(new URL("../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt", import.meta.url), "utf8");
const service = await readFile(new URL("../../android-app/app/src/main/java/com/appforge/studio/BuildProgressService.kt", import.meta.url), "utf8");
const engine = await readFile(new URL("../../android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt", import.meta.url), "utf8");

test("Studio and notification share exact engine progress", () => {
  assert.match(main, /AppForgeBuildProgress\.visible/);
  assert.match(service, /AppForgeBuildProgress\.visible/);
  assert.match(service, /lastVisibleProgress/);
  assert.doesNotMatch(main, /\bbackendProgress\b/);
  assert.match(
    main,
    /val stageLabel =[\s\S]{0,1800}safeProgress >= 90[\s\S]{0,500}safeProgress > 0/
  );
});

test("progress is milestone driven, never timer interpolated", () => {
  assert.match(engine, /APPFORGE_EVENT_DRIVEN_BUILD_PROGRESS_V1/);
  for (const m of [8,10,12,15,25,32,52,56,60,65,90,93,96]) {
    assert.equal(engine.includes(`advanceProgress(state, ${m},`), true, `Missing milestone ${m}`);
  }
  assert.doesNotMatch(main, /var flowingProgress by/);
  assert.doesNotMatch(main, /flowingProgress\s*\+=\s*1/);
});

const terminal = await readFile(new URL("../../android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildTerminalState.kt", import.meta.url), "utf8");

test("failure and cancel retain last real stage; only success writes 100", () => {
  assert.doesNotMatch(engine, /state\.status = "cancelled"\s*\n\s*state\.progress = 0/);
  assert.doesNotMatch(engine, /state\.status\s*=(?!=)|state\.progress\s*=\s*100\b/);
  assert.equal((engine.match(/state\.complete\(/g) || []).length, 2);
  assert.match(engine, /state\.advance\(value, status\)/);
  assert.match(terminal, /value\.coerceIn\(0, 99\)/);
  assert.match(terminal, /fun complete\(message: String\): Boolean = synchronized\(this\) \{\s+if \(terminal\(\) \|\| cancelled\.get\(\)\) return false\s+progress = 100\s+status = "success"/);
  assert.equal((terminal.match(/progress = 100/g) || []).length, 1);
  assert.doesNotMatch(terminal.slice(terminal.indexOf('fun cancelBuild()')), /progress\s*=/);
  assert.match(main, /if \(buildId == null\) \{\s*progress = 0/);
});

test("visual progress eases only toward a proven engine milestone", () => {
  assert.match(
    main,
    /BUILD_PROGRESS_SOFT_VISUAL_V21_6/
  );

  assert.match(
    main,
    /Animatable\(\s*safeProgress\.toFloat\(\)\s*\)/
  );

  assert.match(
    main,
    /targetProgress <=\s*visualProgress\.value[\s\S]{0,500}visualProgress\.snapTo\(\s*targetProgress\s*\)/
  );

  assert.match(
    main,
    /visualProgress\.animateTo\([\s\S]{0,700}targetValue =\s*targetProgress[\s\S]{0,700}FastOutSlowInEasing/
  );

  assert.match(
    main,
    /val displayProgress =[\s\S]{0,300}visualProgress[\s\S]{0,300}roundToInt/
  );

  assert.match(
    main,
    /\$stageLabel • %\$displayProgress/
  );

  assert.doesNotMatch(
    main,
    /displayProgress\s*\+=/
  );

  assert.doesNotMatch(
    main,
    /flowingProgress/
  );
});
