import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../..");
const read = relative => fs.readFileSync(path.join(repo, relative), "utf8");

const classifier = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentPromptProductClassifier.kt"
);
const renderer = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentCodegenRenderers.kt"
);
const runner = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentBuildServiceStageRunner.kt"
);
const route = read(
  "android-app/app/src/main/java/com/appforge/studio/UnifiedAgentStudioRoute.kt"
);
const screen = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeUnifiedAgentStudioScreen.kt"
);

test("prompt classifier distinguishes application and game intent", () => {
  assert.match(classifier, /AppForgeAgentProductKind\.APPLICATION/);
  assert.match(classifier, /AppForgeAgentProductKind\.GAME/);
  assert.match(classifier, /AppForgeAgentGameMode\.RACING/);
  assert.match(classifier, /"oyun"/);
  assert.match(classifier, /"yarış"/);
});

test("game web renderer is actually playable and touch aware", () => {
  assert.match(renderer, /renderGame\(/);
  assert.match(renderer, /<canvas id="game"/);
  assert.match(renderer, /requestAnimationFrame/);
  assert.match(renderer, /pointerdown/);
  assert.match(renderer, /localStorage/);
  assert.match(renderer, /updateRacing/);
});

test("Unified Agent remains device-local and opportunistically adds EXE", () => {
  assert.match(runner, /"device:\/\/local"/);
  assert.match(runner, /WindowsPortableHostStore\.isInstalled\(appContext\)/);
  assert.match(runner, /"all"/);
  assert.doesNotMatch(runner, /AppForge Build Service'e güvenli kaynak yükleniyor/);
  assert.doesNotMatch(route, /AppForge Cloud BUILD/);
});

test("product UI explicitly accepts application or game prompts", () => {
  assert.match(screen, /AI ile Uygulama \/ Oyun Oluştur/);
  assert.match(screen, /uygulamayı veya oyunu normal dille anlat/);
});
