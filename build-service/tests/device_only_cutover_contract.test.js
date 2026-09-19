import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "..", "..");
const repoFile = relative => path.join(repoRoot, relative);


const read = target =>
  fs.readFileSync(
    path.isAbsolute(target)
      ? target
      : repoFile(target),
    "utf8"
  );

test("normal Android build client is device-local", () => {
  const client = read(repoFile("android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"));
  assert.match(client, /DeviceBuildEngine\.start/);
  assert.doesNotMatch(client, /HttpURLConnection|\/api\/builds|createDirectProjectUpload/);
});

test("device engine has real local toolchain and APK-AAB paths", () => {
  const engine = read(repoFile("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"));
  assert.match(engine, /AndroidLinuxRuntimeManager/);
  assert.match(engine, /LinuxShellEngine/);
  assert.match(engine, /assemble\$variant/);
  assert.match(engine, /bundle\$variant/);
  assert.match(engine, /python-android/);
  assert.match(engine, /node-web/);
  assert.match(engine, /android-gradle/);
});

test("Studio home is simplified", () => {
  const home = read(repoFile("android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"));
  assert.match(home, /Yeni proje/);
  assert.match(home, /AppForge AI/);
  assert.match(home, /Terminal/);
  assert.match(home, /Derlemeler/);
  assert.ok(home.split("\n").length < 280, "Home V2 tekrar aşırı büyümemeli");
});

test("Firebase Messaging is removed from Studio runtime", () => {
  const gradle = read(repoFile("android-app/app/build.gradle.kts"));
  const manifest = read(repoFile("android-app/app/src/main/AndroidManifest.xml"));
  assert.doesNotMatch(gradle, /firebase-messaging/);
  assert.doesNotMatch(manifest, /FirebaseMessagingService/);
});


test("retired provider configuration is absent", () => {
  const debugWorkflow =
    read(repoFile(".github/workflows/android-debug.yml"));

  const playWorkflow =
    read(repoFile(".github/workflows/android-play-release.yml"));

  const appGradle =
    read(repoFile("android-app/app/build.gradle.kts"));

  const connections =
    read(
      "android-app/app/src/main/java/" +
      "com/appforge/studio/terminal/ConnectionsPanel.kt"
    );

  for (const workflow of [debugWorkflow, playWorkflow]) {
    assert.doesNotMatch(
      workflow,
      /APPFORGE_FIREBASE_GOOGLE_SERVICES_B64/
    );

    assert.doesNotMatch(
      workflow,
      /APPFORGE_RAILWAY_OAUTH_CLIENT_ID/
    );
  }

  assert.doesNotMatch(
    appGradle,
    /firebase-messaging/
  );

  assert.doesNotMatch(
    appGradle,
    /oauthClientId\("APPFORGE_RAILWAY_OAUTH_CLIENT_ID"\)/
  );

  assert.doesNotMatch(
    connections,
    /provider\s*=\s*ExternalProvider\.RAILWAY/
  );
});
