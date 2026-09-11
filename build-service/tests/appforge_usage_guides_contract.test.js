import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const knowledgeUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ai/AppForgeKnowledgeBase.kt",
    import.meta.url
  );

const mainUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

test(
  "Play build exposes the four AppForge usage guides",
  async () => {
    const source =
      await readFile(
        knowledgeUrl,
        "utf8"
      );

    for (const title of [
      "AppForge Studio nasıl kullanılır?",
      "AppForge Terminal nasıl kullanılır?",
      "Excel Tools nasıl kullanılır?",
      "VideoForge nasıl kullanılır?"
    ]) {
      assert.ok(
        source.includes(title),
        `Missing usage guide: ${title}`
      );
    }

    assert.match(
      source,
      /Kullanım Rehberi/
    );

    assert.match(
      source,
      /helpArticles/
    );
  }
);

test(
  "Help Center route remains available",
  async () => {
    const source =
      await readFile(
        mainUrl,
        "utf8"
      );

    assert.match(
      source,
      /AppScreen\.HELP/
    );

    assert.match(
      source,
      /AppScreen\.PLAY_GUIDE/
    );
  }
);
