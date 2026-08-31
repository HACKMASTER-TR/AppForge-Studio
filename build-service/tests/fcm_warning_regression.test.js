import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const file =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/AppForgeFirebaseMessagingService.kt",
    import.meta.url
  );

test(
  "FCM token callback handles SDK deprecation without removing refresh support",
  async () => {
    const text =
      await readFile(
        file,
        "utf8"
      );

    assert.ok(
      text.includes(
        '@Suppress("OVERRIDE_DEPRECATION")'
      )
    );

    assert.ok(
      text.includes(
        "override fun onNewToken"
      )
    );

    assert.ok(
      text.includes(
        '"token"'
      )
    );
  }
);
