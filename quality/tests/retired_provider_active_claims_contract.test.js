import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..", "..");

const read = relative =>
  fs.readFileSync(
    path.join(root, relative),
    "utf8"
  );

test("README does not advertise retired provider authorization", () => {
  const readme = read("README.md");

  assert.doesNotMatch(
    readme,
    /GitHub and Railway accounts can be authorized/i
  );

  assert.match(
    readme,
    /Railway authorization[\s\S]{0,160}retired/i
  );
});

test("README describes normal builds as device local without remote worker fallback", () => {
  const readme = read("README.md");

  assert.match(
    readme,
    /Normal Android project compilation is device-local/
  );

  assert.doesNotMatch(
    readme,
    /worker's CPU\/RAM|worker reaches its memory limit|GRADLE_PERFORMANCE_PROFILE/
  );

  assert.match(
    readme,
    /no Railway, Render, remote Worker or remote autoscale fallback/
  );
});

test("README exposes lifetime-only Pro wording", () => {
  const readme = read("README.md");

  assert.match(
    readme,
    /Pro Ömür Boyu/
  );

  assert.doesNotMatch(
    readme,
    /Pro Monthly|AYLIK 50 PROJE|subscribe_monthly/
  );
});

test("project overview does not treat Railway as a live provider", () => {
  const overview = read(
    "docs/wiki/01_Project/Project_Overview.md"
  );

  assert.match(
    overview,
    /Railway is retired from the active project-build architecture/
  );

  assert.doesNotMatch(
    overview,
    /Live GitHub, Railway, Play Console/
  );
});
