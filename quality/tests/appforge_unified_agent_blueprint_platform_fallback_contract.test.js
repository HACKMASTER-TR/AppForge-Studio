import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function read(relative) {
  return fs.readFileSync(
    new URL(`../../${relative}`, import.meta.url),
    "utf8"
  );
}

const json = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentBlueprintJson.kt"
);
const route = read(
  "android-app/app/src/main/java/com/appforge/studio/UnifiedAgentStudioRoute.kt"
);
const recovery = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentBlueprintRecovery.kt"
);

test("selected platform can safely fill only a missing Blueprint platform", () => {
  assert.match(
    json,
    /fallbackPlatform:\s*AppForgeAgentPlatform\?\s*=\s*null/
  );
  assert.match(
    json,
    /optionalString\([\s\S]*?"platform"[\s\S]*?"root\.platform"[\s\S]*?\)/
  );
  assert.match(
    json,
    /\?:\s*fallbackPlatform\s*\?:\s*fail\("root\.platform zorunlu\."\)/
  );
});

test("Unified Agent passes the selected platform through strict recovery parsing", () => {
  assert.match(route, /AppForgeAgentBlueprintRecovery\.generate\(/);
  assert.match(route, /platform\s*=\s*state\.platform/);
  assert.match(recovery, /AppForgeAgentBlueprintJson\.parse\(/);
  assert.match(recovery, /fallbackPlatform\s*=\s*platform/);
  assert.match(recovery, /require\(parsed\.platform == platform\)/);
});

test("strict schema and unknown-field validation remain enabled", () => {
  assert.match(json, /root\.requireOnly/);
  assert.match(json, /AppForgeAgentBlueprintValidator\.validate\(blueprint\)/);
});
