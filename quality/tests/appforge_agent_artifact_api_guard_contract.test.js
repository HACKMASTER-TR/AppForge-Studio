import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const artifactClient = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentArtifactClient.kt",
    import.meta.url
  ),
  "utf8"
);

const gradle = await readFile(
  new URL(
    "../../android-app/app/build.gradle.kts",
    import.meta.url
  ),
  "utf8"
);

test(
  "Unified Agent artifact export keeps API 29 MediaStore behind a Q guard",
  () => {
    assert.match(
      artifactClient,
      /Build\.VERSION\.SDK_INT\s*>=\s*Build\.VERSION_CODES\.Q/
    );

    assert.match(
      artifactClient,
      /@android\.annotation\.TargetApi\(Build\.VERSION_CODES\.Q\)\s+private fun exportMediaStore/
    );

    assert.match(
      artifactClient,
      /else\s*\{[\s\S]{0,500}?exportLegacy/
    );

    assert.match(
      gradle,
      /\bminSdk\s*=\s*26\b/
    );
  }
);
