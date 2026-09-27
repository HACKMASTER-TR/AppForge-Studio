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

test("failure and cancel retain last real stage; only success writes 100", () => {
  assert.doesNotMatch(engine, /state\.status = "cancelled"\s*\n\s*state\.progress = 0/);
  assert.match(engine, /state\.status = "success"\s*\n\s*state\.progress = 100/);
  assert.match(main, /if \(buildId == null\) \{\s*progress = 0/);
});
