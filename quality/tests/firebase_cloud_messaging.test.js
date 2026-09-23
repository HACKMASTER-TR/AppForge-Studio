import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root =
  path.resolve(
    import.meta.dirname,
    "..",
    ".."
  );

function text(rel) {
  return fs.readFileSync(
    path.join(root, rel),
    "utf8"
  );
}

test(
  "AppForge Studio FCM runtime stays retired",
  () => {
    assert.throws(
      () =>
        text(
          "android-app/app/src/main/java/com/appforge/studio/AppForgeFirebaseMessagingService.kt"
        ),
      error =>
        error?.code === "ENOENT"
    );

    assert.doesNotMatch(
      text(
        "android-app/app/build.gradle.kts"
      ),
      /firebase-messaging/
    );

    assert.doesNotMatch(
      text(
        "android-app/app/src/main/AndroidManifest.xml"
      ),
      /com\.google\.firebase\.MESSAGING_EVENT/
    );
  }
);
