import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = path => fs.readFileSync(new URL(`../../${path}`, import.meta.url), "utf8");

const screen = read("android-app/app/src/main/java/com/appforge/studio/AdminAiRouterScreen.kt");
const collector = read("android-app/app/src/main/java/com/appforge/studio/ai/AiProjectContextCollector.kt");
const main = read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt");
const gateway = read("cloudflare/control-plane/src/admin_ai_router.mjs");

test("admin AI receives current project read-only context without enabling tools", () => {
  assert.match(screen, /AiProjectContextCollector[\s\S]{0,200}?\.collect\(/);
  assert.match(screen, /\.put\("projectContext",\s*projectContext\.toJson\(\)\)/);
  assert.match(main, /AdminAiRouterScreen\([\s\S]{0,220}?projectId\s*=\s*currentProjectId[\s\S]{0,120}?draft\s*=\s*draft/);
  assert.doesNotMatch(screen, /runCommand\(|DeviceBuildEngine\.start|WorkspaceFileService\.writeText/);
});

test("context collector is bounded and secret-aware", () => {
  assert.match(collector, /MAX_CONTEXT_FILES\s*=\s*6/);
  assert.match(collector, /canonicalFile/);
  assert.match(collector, /node_modules/);
  assert.match(collector, /\.env/);
  assert.match(collector, /REDACTED_SECRET-LIKE LINE/);
  assert.match(collector, /sourceFileExtensions/);
  assert.match(collector, /"html"/);
  assert.match(collector, /name == "index\.html"/);
  assert.match(collector, /isContextCandidate/);
});

test("gateway treats repository context as untrusted and fails closed on invalid paths", () => {
  assert.match(gateway, /UNTRUSTED DATA/);
  assert.match(gateway, /invalid_project_context/);
  assert.match(gateway, /projectContextUsed/);
  assert.match(gateway, /safeRelativePath/);
  assert.match(gateway, /This endpoint is read-only/);
});
