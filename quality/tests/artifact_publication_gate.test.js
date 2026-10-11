import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const repo =
  path.resolve(
    here,
    "../.."
  );

const main =
  fs.readFileSync(
    path.join(
      repo,
      "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
    ),
    "utf8"
  );

test("artifact publication waits for final build success and proven 100 percent", () => {
  assert.match(
    main,
    /ARTIFACT_PUBLICATION_GATE_V1/
  );

  assert.match(
    main,
    /val artifactPublicationReady\s*=\s*buildSucceeded\s*&&\s*safeProgress\s*==\s*100\s*&&\s*!effectiveBuildBusy/
  );
});

test("APK download action is hidden before artifact publication readiness", () => {
  assert.match(
    main,
    /if\s*\(\s*artifactPublicationReady\s*&&\s*apkUrl\s*!=\s*null\s*\)/
  );

  assert.doesNotMatch(
    main,
    /if\s*\(\s*apkUrl\s*!=\s*null\s*\)\s*\{\s*item\s*\{\s*Button/
  );
});

test("AAB download action is hidden before artifact publication readiness", () => {
  assert.match(
    main,
    /if\s*\(\s*artifactPublicationReady\s*&&\s*aabUrl\s*!=\s*null\s*\)/
  );

  assert.doesNotMatch(
    main,
    /if\s*\(\s*aabUrl\s*!=\s*null\s*\)\s*\{\s*item\s*\{\s*Button/
  );
});

test("Windows EXE download action is hidden before final signing and verification success", () => {
  assert.match(
    main,
    /if\s*\(\s*artifactPublicationReady\s*&&\s*exeUrl\s*!=\s*null\s*\)/
  );

  assert.doesNotMatch(
    main,
    /if\s*\(\s*exeUrl\s*!=\s*null\s*\)\s*\{\s*item\s*\{\s*Button/
  );
});
