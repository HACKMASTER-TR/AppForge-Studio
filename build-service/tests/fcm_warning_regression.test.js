import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "..", "..");

const file =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/AppForgeFirebaseMessagingService.kt",
    import.meta.url
  );

test(
  "AppForge Studio FCM runtime stays retired",
  async () => {
    await assert.rejects(
      readFile(
        file,
        "utf8"
      ),
      {
        code: "ENOENT"
      }
    );
  }
);
