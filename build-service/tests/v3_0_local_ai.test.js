import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const gradle =
  new URL(
    "../../android-app/app/build.gradle.kts",
    import.meta.url
  );

const main =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

const runtime =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ai/AppForgeLocalAssistant.kt",
    import.meta.url
  );

const store =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ai/LocalAiModelStore.kt",
    import.meta.url
  );

const knowledge =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ai/AppForgeKnowledgeBase.kt",
    import.meta.url
  );

const integration =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAssistantIntegration.kt",
    import.meta.url
  );

const home =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/StudioHomeScreen.kt",
    import.meta.url
  );

test("LiteRT-LM runtime is integrated", async () => {
  const text =
    await readFile(
      gradle,
      "utf8"
    );

  assert.equal(
    text.includes(
      "com.google.ai.edge.litertlm:litertlm-android:0.11.0"
    ),
    true
  );

  assert.match(
    text,
    /sourceCompatibility\s*=\s*JavaVersion\.VERSION_17/
  );

  assert.match(
    text,
    /targetCompatibility\s*=\s*JavaVersion\.VERSION_17/
  );
});

test("assistant uses local Engine and streaming conversation", async () => {
  const text = await readFile(runtime, "utf8");
  for (const token of [
    "EngineConfig",
    "Backend.CPU()",
    "Backend.GPU()",
    "createConversation",
    "sendMessageAsync",
    "AppForgeKnowledgeBase"
  ]) {
    assert.equal(text.includes(token), true, `Missing ${token}`);
  }
});

test("model import is private and fingerprints model", async () => {
  const text = await readFile(store, "utf8");
  assert.equal(text.includes("context.filesDir"), true);
  assert.equal(text.includes("SHA-256"), true);
  assert.equal(text.includes(".litertlm"), true);
});

test("assistant UI is project aware without secrets", async () => {
  const ui = await readFile(main, "utf8");
  const kb = await readFile(knowledge, "utf8");
  assert.equal(ui.includes("AppScreen.AI_ASSISTANT"), true);
  assert.equal(ui.includes("Mevcut proje bağlamını kullan"), true);
  assert.equal(kb.includes("projectContext"), true);
  assert.equal(kb.includes("buildApiKey"), false);
  assert.equal(kb.includes("keystorePassword"), false);
});

test("assistant technology guidance matches live source engines", async () => {
  const text =
    await readFile(
      knowledge,
      "utf8"
    );

  for (
    const marker of [
      "Android Kotlin/Java Gradle",
      "Flutter/Dart",
      "React Native",
      "Expo managed",
      "Python/Flask/Django",
      "appforge_main",
      "net10.0-android",
      "appforge.remote.json",
      "dedicated Unity Worker"
    ]
  ) {
    assert.equal(
      text.includes(marker),
      true,
      `Missing technology guidance: ${marker}`
    );
  }

  assert.equal(
    text.includes(
      "doğrudan kaynak-proje build desteği henüz eklenmemiştir"
    ),
    false
  );
});

test("assistant receives the complete application map and safe runtime context", async () => {
  const map = await readFile(integration, "utf8");
  const kb = await readFile(knowledge, "utf8");
  const runtimeText = await readFile(runtime, "utf8");

  for (const destination of [
    "PROJECTS",
    "QUICK_CREATE",
    "ADVANCED_CREATE",
    "CONVERSION",
    "SOURCE",
    "PERMISSIONS",
    "FEATURES",
    "APPEARANCE",
    "NATIVE_BRIDGE",
    "MONETIZATION",
    "DEEP_LINK",
    "SIGNING",
    "BUILD_SETTINGS",
    "BUILD",
    "PREVIEW",
    "PRODUCTION",
    "TEST_LAB",
    "TEMPLATES",
    "HISTORY",
    "TRASH",
    "SETTINGS",
    "ACCOUNT",
    "HELP",
    "PLAY_GUIDE",
    "PRO",
    "KEYSTORES"
  ]) {
    assert.equal(map.includes(destination), true, `Missing ${destination}`);
  }

  assert.equal(kb.includes("applicationMap()"), true);
  assert.equal(kb.includes("runtimeSummary("), true);
  assert.equal(runtimeText.includes("runtimeContext"), true);

  for (const secret of [
    "buildApiKey",
    "storePassword",
    "keyPassword",
    "firebaseConfigUri",
    "keystoreUri"
  ]) {
    assert.equal(map.includes(secret), false, `Runtime map leaks ${secret}`);
  }
});

test("assistant can diagnose builds and navigate to relevant app screens", async () => {
  const ui = await readFile(main, "utf8");
  const map = await readFile(integration, "utf8");

  for (const token of [
    "suggestedActions",
    "navigateFromAssistant",
    "onNavigate",
    "buildLogs",
    "buildPreflight",
    "diagnosisAnswer"
  ]) {
    assert.equal(ui.includes(token), true, `Missing AI integration ${token}`);
  }

  assert.equal(map.includes("actionsFor"), true);
  assert.equal(map.includes("Güvenli log ipucu"), true);
});

test("home keeps one clear primary action and one navigation path per section", async () => {
  const ui = await readFile(home, "utf8");
  const mainUi = await readFile(main, "utf8");

  assert.equal(ui.includes('text = "Yeni proje"'), true);
  assert.equal(ui.includes('"AI Asistan"'), true);
  assert.equal(ui.includes('label = "Yerel AI"'), false);
  assert.equal(ui.includes('label = "Pro"'), false);
  assert.equal(mainUi.includes("BuilderShortcutBar("), false);
  assert.equal(mainUi.includes('"Adım $step/10 • "'), true);
});
