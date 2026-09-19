import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../..");

const read = relative =>
  fs.readFileSync(path.join(repo, relative), "utf8");

test("Android device build does not require full terminal development profile", () => {
  const source = read(
    "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  );

  assert.match(source, /ensureBaseEnvironment/);
  assert.doesNotMatch(source, /ensureDevelopmentEnvironment/);
});

test("device build passes source engine to local toolchain installer", () => {
  const source = read(
    "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  );

  assert.match(
    source,
    /install-toolchain\.sh.*sourceBuildEngine/s
  );
});

test("Android toolchain no longer installs npm unconditionally", () => {
  const source = read(
    "android-app/app/src/main/assets/device-build/install-toolchain.sh"
  );

  assert.match(source, /ENGINE="\$\{1:-webview-static\}"/);

  assert.match(
    source,
    /node-web\)[\s\S]*apt-get install[\s\S]*nodejs npm/
  );

  assert.match(
    source,
    /python-android\)[\s\S]*python3 python3-pip python3-venv/
  );

  const unconditional = source
    .split('case "$ENGINE" in')[0];

  assert.doesNotMatch(
    unconditional,
    /nodejs npm/
  );
});
