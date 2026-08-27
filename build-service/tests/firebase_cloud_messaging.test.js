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
  "AppForge Studio and generated apps wire Firebase Cloud Messaging",
  () => {
    const rootGradle =
      text(
        "android-app/build.gradle.kts"
      );

    const studioGradle =
      text(
        "android-app/app/build.gradle.kts"
      );

    const studioManifest =
      text(
        "android-app/app/src/main/AndroidManifest.xml"
      );

    const studioService =
      text(
        "android-app/app/src/main/java/com/appforge/studio/AppForgeFirebaseMessagingService.kt"
      );

    const draft =
      text(
        "android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt"
      );

    const api =
      text(
        "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
      );

    const engine =
      text(
        "build-service/src/buildEngine.js"
      );

    const fast =
      text(
        "build-service/src/fastBuild.js"
      );

    const debugWorkflow =
      text(
        ".github/workflows/android-debug.yml"
      );

    assert.match(
      rootGradle,
      /com\.google\.gms\.google-services/
    );

    assert.match(
      studioGradle,
      /firebase-messaging/
    );

    assert.match(
      studioManifest,
      /com\.google\.firebase\.MESSAGING_EVENT/
    );

    assert.match(
      studioService,
      /FirebaseMessagingService/
    );

    assert.match(
      studioService,
      /onNewToken/
    );

    assert.match(
      draft,
      /firebaseMessagingEnabled/
    );

    assert.match(
      api,
      /put\("messaging", draft\.firebaseMessagingEnabled\)/
    );

    assert.match(
      engine,
      /firebase\?\.messaging/
    );

    assert.match(
      engine,
      /firebase-messaging/
    );

    assert.match(
      engine,
      /AppForgeFirebaseMessagingService\.kt/
    );

    assert.match(
      engine,
      /com\.google\.firebase\.MESSAGING_EVENT/
    );

    assert.match(
      fast,
      /firebase\?\.messaging/
    );

    assert.match(
      debugWorkflow,
      /APPFORGE_FIREBASE_GOOGLE_SERVICES_B64/
    );
  }
);
