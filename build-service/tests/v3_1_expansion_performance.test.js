import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root = new URL("../../", import.meta.url);
const read = path => readFile(new URL(path, root), "utf8");

test("expanded system templates are seeded and materialized", async () => {
  const [migration, factory, main] = await Promise.all([
    read("build-service/sql/013_expanded_system_templates.sql"),
    read("android-app/app/src/main/java/com/appforge/studio/io/TemplateProjectFactory.kt"),
    read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt")
  ]);

  for (const slug of [
    "task-manager",
    "inventory-panel",
    "booking-form",
    "restaurant-menu",
    "event-invitation"
  ]) {
    assert.ok(migration.includes(`'${slug}'`), `seed ${slug}`);
    assert.ok(factory.includes(`"${slug}"`), `materialize ${slug}`);
  }

  for (const category of ["Verimlilik", "İşletme", "E-ticaret ve Menü", "Etkinlik"]) {
    assert.ok(main.includes(category), category);
  }
});

test("additional Android permissions propagate securely", async () => {
  const [draft, library, client, engine, fast] = await Promise.all([
    read("android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt"),
    read("android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt"),
    read("android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"),
    read("build-service/src/buildEngine.js"),
    read("build-service/src/fastBuild.js")
  ]);

  for (const field of ["microphone", "networkState", "wakeLock", "nfc"]) {
    assert.ok(draft.includes(`var ${field}: Boolean`), field);
    assert.ok(library.includes(`"${field}"`), `persist ${field}`);
    assert.ok(client.includes(`"${field}"`), `payload ${field}`);
  }

  for (const permission of ["RECORD_AUDIO", "ACCESS_NETWORK_STATE", "WAKE_LOCK", "NFC"]) {
    assert.ok(engine.includes(`android.permission.${permission}`), permission);
    assert.ok(fast.includes(`android.permission.${permission}`), `fast ${permission}`);
  }

  for (const forbidden of ['put("storePassword"', 'put("keyPassword"', 'put("buildApiKey"']) {
    assert.equal(library.includes(forbidden), false, forbidden);
  }

  for (const permission of [
    "BLUETOOTH_SCAN", "USE_BIOMETRIC", "READ_CALENDAR", "READ_CONTACTS",
    "ACCESS_BACKGROUND_LOCATION", "SCHEDULE_EXACT_ALARM", "READ_MEDIA_IMAGES",
    "READ_MEDIA_VIDEO", "ACTIVITY_RECOGNITION"
  ]) {
    assert.ok(engine.includes(`"${permission}"`), `allowlist ${permission}`);
    assert.ok(fast.includes(`"${permission}"`), `fast allowlist ${permission}`);
  }
  assert.ok(draft.includes("additionalPermissions: Set<String>"));
  assert.ok(client.includes('"additionalPermissions"'));
});

test("uploaded sources auto-detect every exposed permission", async () => {
  const [analyzer, main] = await Promise.all([
    read("android-app/app/src/main/java/com/appforge/studio/io/SourceCapabilityAnalyzer.kt"),
    read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt")
  ]);

  for (const marker of [
    "android.permission.camera",
    "android.permission.record_audio",
    "android.permission.access_fine_location",
    "android.permission.post_notifications",
    "android.permission.access_network_state",
    "android.permission.wake_lock",
    "android.permission.nfc",
    "getusermedia",
    "navigator.geolocation",
    "navigator.wakelock",
    "ndefreader"
  ]) {
    assert.ok(analyzer.toLowerCase().includes(marker), marker);
  }

  for (const assignment of [
    "microphone =\n                            analysis.microphone",
    "networkState =\n                            analysis.networkState",
    "wakeLock =\n                            analysis.wakeLock",
    "nfc =\n                            analysis.nfc"
  ]) {
    assert.ok(main.includes(assignment), assignment);
  }

  assert.ok(main.includes("otomatik algılanıp işaretlenir"));
});

test("unchanged sources reuse ZIP and local AI uses fast routing", async () => {
  const [zip, main, integration, assistant] = await Promise.all([
    read("android-app/app/src/main/java/com/appforge/studio/io/ZipUtils.kt"),
    read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"),
    read("android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAssistantIntegration.kt"),
    read("android-app/app/src/main/java/com/appforge/studio/ai/AppForgeLocalAssistant.kt")
  ]);

  assert.ok(zip.includes("cachedZipDirectory"));
  assert.ok(zip.includes("sourceFingerprint"));
  assert.ok(main.includes("hızlı ZIP önbelleği"));
  assert.ok(integration.includes("quickGuidance"));
  assert.ok(main.includes("hızlı yönlendirme"));
  assert.match(assistant, /maxNumTokens\s*=\s*768/);
});

test("v3 complete pack adds validated Firebase, instant commands and safe build defaults", async () => {
  const [firebase, commands, main, zip, config, migration, factory] = await Promise.all([
    read("android-app/app/src/main/java/com/appforge/studio/io/FirebaseConfigInspector.kt"),
    read("android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAiCommandParser.kt"),
    read("android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"),
    read("android-app/app/src/main/java/com/appforge/studio/io/ZipUtils.kt"),
    read("build-service/src/config.js"),
    read("build-service/sql/014_more_system_templates.sql"),
    read("android-app/app/src/main/java/com/appforge/studio/io/TemplateProjectFactory.kt")
  ]);

  assert.ok(firebase.includes("packageMatches"));
  assert.ok(firebase.includes("2 * 1024 * 1024"));
  assert.ok(main.includes("FirebaseConfigInspector.inspect"));
  assert.ok(commands.includes("webMixedContentAllowed = false"));
  assert.ok(commands.includes("remoteBridgeAllowed = false"));
  assert.ok(main.includes("AppForgeAiCommandParser"));
  assert.ok(zip.includes("Deflater.BEST_SPEED"));
  assert.match(config, /WORKER_POLL_MS \|\| 500/);

  for (const slug of ["visual-designer", "personnel-tracker", "qr-menu", "education-quiz", "firebase-login"]) {
    assert.ok(migration.includes(`'${slug}'`), `seed ${slug}`);
    assert.ok(factory.includes(`"${slug}"`), `materialize ${slug}`);
  }
});
