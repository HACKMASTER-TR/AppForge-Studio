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

const maps = {
  project: "docs/wiki/01_Project/Project_Overview.md",
  features: "docs/wiki/06_Product_And_Features/Feature_Overview.md",
  config: "docs/wiki/02_Codebase_Map/Config_And_Env_Map.md",
  database: "docs/wiki/02_Codebase_Map/Database_Schema_Coverage.md",
  integrations: "docs/wiki/07_Integrations/Integration_Index.md",
  android: "docs/wiki/02_Codebase_Map/Android_App_Map.md"
};

// Inspect prose units so historical mentions remain legitimate, independent
// of line wrapping, Markdown emphasis or exact sentence wording.
const prose = text => text.split("---").slice(2).join("---")
  .replace(/[`*_]/g, "");
const units = text => prose(text).split(/\n\s*\n|(?<=[.!?])\s+/);
const retired = /historical|retired|former|not (?:a |the )?current/i;

for (const [name, relative] of Object.entries(maps)) {
  test(`${name} map preserves local builds and separate current control plane`, () => {
    const text = prose(read(relative));
    assert.match(text, /device-local/i);
    assert.match(text, /(?:separate|distinct)[\s\S]{0,160}(?:HTTPS|control.plane)|(?:HTTPS|control.plane)[\s\S]{0,160}(?:separate|distinct)/i);
    assert.match(text, /Cloudflare[\s\S]{0,100}Worker/i);
    assert.match(text, /\bD1\b/);
    assert.match(text, /(?:Build Service[\s\S]{0,100}retired|retired[\s\S]{0,100}(?:Build Service|remote build))/i);
  });
}

test("introductory maps do not assign active ownership to the retired backend", () => {
  const project = prose(read(maps.project));
  assert.match(project, /DeviceBuildEngine/);
  assert.match(project, /DeviceBuildRuntimeV3/);
  assert.doesNotMatch(project, /backed by (?:a |the )?Node\.js build service/i);
  for (const unit of units(read(maps.project))) {
    if (/Express|PostgreSQL|Redis|Docker/i.test(unit)) assert.match(unit, retired);
  }
  assert.doesNotMatch(prose(read(maps.features)), /the build service\s+(?:provides|owns|handles|manages)/i);
  for (const [name, stale] of [
    ["config", /build-service\/src\/config\.js/i],
    ["database", /build-service\/sql|src\/db\.js/i]
  ]) {
    for (const unit of units(read(maps[name]))) {
      if (stale.test(unit)) assert.match(unit, retired);
    }
  }
  assert.match(prose(read(maps.database)), /cloudflare\/control-plane\/migrations/);
  assert.match(prose(read(maps.database)), /(?:not|do not)[\s\S]{0,100}(?:live|production|migrated)/i);
  const android = prose(read(maps.android));
  assert.doesNotMatch(android, /(?:net\/[^\n]*and\s+)?build\/:\s*(?:remote\s+)?Build Service clients?/i);
  assert.match(android, /build\/:\s*device-local/i);
  assert.match(android, /net\/:\s*HTTPS/i);
});

test("integration inventory confines former database, queue and storage infrastructure to retirement history", () => {
  for (const unit of units(read(maps.integrations))) {
    if (/PostgreSQL|Redis|MinIO/i.test(unit)) assert.match(unit, retired);
  }
  assert.match(prose(read(maps.integrations)), /authenticated checks[\s\S]{0,100}(?:external|production|status)/i);
});
