import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import AdmZip from "adm-zip";
import { bumpVersion, createV5Scaffold } from "../src/v5Studio.js";

const androidMain = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
  import.meta.url
);
const server = new URL("../server.js", import.meta.url);
const buildProgressService = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/BuildProgressService.kt",
  import.meta.url
);
const knowledgeBase = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/ai/AppForgeKnowledgeBase.kt",
  import.meta.url
);

test("V5 version manager increments semantic patch and Android code", () => {
  assert.deepEqual(bumpVersion("2.4.9", 29), {
    versionName: "2.4.10",
    versionCode: 30
  });
  assert.deepEqual(bumpVersion("invalid", 0), {
    versionName: "1.0.1",
    versionCode: 2
  });
});

test("V5 scaffold creates working UI, data, backend, auth and notification flows", () => {
  const result = createV5Scaffold({
    appName: "Görevler",
    entity: "tasks",
    fields: ["title", "completed"],
    autoVersion: true,
    versionName: "1.3.0",
    versionCode: 7
  });

  assert.equal(result.config.versionName, "1.3.1");
  assert.equal(result.config.versionCode, 8);
  assert.match(result.files["index.html"], /Bildirimleri aç/);
  assert.match(result.files["app.js"], /localStorage/);
  assert.doesNotThrow(() => new Function(result.files["app.js"]));
  assert.match(result.files["backend\/server.js"], /\/api\/tasks/);
  assert.match(result.files["database/schema.sql"], /CREATE TABLE IF NOT EXISTS tasks/);
  assert.deepEqual(
    JSON.parse(result.files["database/schema.json"]).fields.map((x) => x.name),
    ["title", "completed"]
  );
});

test("V5 scaffold safely imports HTML ZIP sources", () => {
  const zip = new AdmZip();
  zip.addFile("index.html", Buffer.from("<h1>Imported</h1>"));
  zip.addFile("assets/app.js", Buffer.from("globalThis.imported=true"));
  zip.addFile("../unsafe.js", Buffer.from("throw new Error('unsafe')"));
  const result = createV5Scaffold({
    sourceZipBase64: zip.toBuffer().toString("base64")
  });
  assert.equal(result.files["index.html"], "<h1>Imported</h1>");
  assert.equal(result.files["assets/app.js"], "globalThis.imported=true");
  assert.equal(result.files["../unsafe.js"], undefined);
});

test("Android Quick and Advanced screens expose automatic version increment", async () => {
  const android = await readFile(androidMain, "utf8");
  assert.ok(android.match(/Otomatik sürüm arttır/g).length >= 2);
});

test("V5 scaffold API is authenticated and wired into the service", async () => {
  const text = await readFile(server, "utf8");
  assert.match(text, /"\/api\/v5\/scaffold",\s*authRequired/);
  assert.match(text, /createV5Scaffold/);
});

test("Android keeps a remote build visible as a background notification", async () => {
  const [main, service] = await Promise.all([
    readFile(androidMain, "utf8"),
    readFile(buildProgressService, "utf8")
  ]);
  assert.match(main, /BuildProgressService\.track/);
  assert.match(main, /BuildProgressService\.startPending/);
  assert.match(main, /BuildProgressService\.stop/);
  assert.match(service, /startForeground/);
  assert.match(service, /appforge_open_builds/);
  assert.match(service, /client\.getBuild\(\s*buildId\s*\)/);
  assert.match(service, /"AppForge derlemeleri"/);
});

test("local AI describes the current AppForge feature set", async () => {
  const knowledge = await readFile(knowledgeBase, "utf8");
  assert.match(knowledge, /FULL_FEATURES_ANSWER/);
  assert.match(knowledge, /Windows EXE/);
  assert.match(knowledge, /Flutter, React Native, Expo/);
  assert.match(knowledge, /otomatik sürüm artırma/);
  assert.match(knowledge, /Firebase Analytics\/Crashlytics\/Cloud Messaging/);
  assert.match(knowledge, /GitHub ve takım akışları/);
  assert.match(knowledge, /arka planda.*bildirimi/);
  assert.match(knowledge, /Yerel AI/);
});
