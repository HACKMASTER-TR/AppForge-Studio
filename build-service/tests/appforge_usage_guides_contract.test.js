import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const knowledgeUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/ai/AppForgeKnowledgeBase.kt",
  import.meta.url
);

const mainUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
  import.meta.url
);

test("Play build exposes the four AppForge usage guides", async () => {
  const source = await readFile(knowledgeUrl, "utf8");

  const titles = [
    "AppForge Studio nasıl kullanılır?",
    "AppForge Terminal nasıl kullanılır?",
    "Excel Tools nasıl kullanılır?",
    "VideoForge nasıl kullanılır?"
  ];

  for (const title of titles) {
    assert.ok(
      source.includes(title),
      `Missing usage guide: ${title}`
    );
  }

  assert.ok(
    source.includes('"Kullanım Rehberi"'),
    "Usage guide category is missing"
  );

  assert.ok(
    source.includes("gettingStartedArticles +"),
    "Usage guides are not included in helpArticles()"
  );
});

test("Help Center visibly advertises the new guides", async () => {
  const source = await readFile(mainUrl, "utf8");

  assert.ok(
    source.includes(
      "AppForge, Terminal, Excel Tools ve VideoForge kullanım rehberi"
    )
  );

  assert.ok(
    source.includes(
      "AppForge, Terminal, Excel Tools, VideoForge, APK, AAB"
    )
  );
});
