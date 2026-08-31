import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";


async function detectorSource() {
  return fs.readFile(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectTechnologyDetector.kt",
      import.meta.url
    ),
    "utf8"
  );
}


test(
  "Android Gradle Kotlin DSL files do not make a Java project Kotlin",
  async () => {
    const source =
      await detectorSource();

    assert.equal(
      source.includes(
        'val kotlin = hasExt("kt", "kts")'
      ),
      false,
      "Gradle .kts files must not be treated as Kotlin app source"
    );

    for (
      const marker of [
        'path.startsWith("app/src/")',
        'path.endsWith(".kt")',
        '!path.startsWith("app/src/test/")',
        '!path.startsWith("app/src/androidtest/")',
        '"Android / Java"',
        '"Android / Kotlin"'
      ]
    ) {
      assert.ok(
        source.includes(marker),
        `ProjectTechnologyDetector missing ${marker}`
      );
    }
  }
);
