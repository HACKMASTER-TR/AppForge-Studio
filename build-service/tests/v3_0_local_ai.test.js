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
