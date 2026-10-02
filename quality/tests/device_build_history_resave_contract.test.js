import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = await readFile(new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt",
  import.meta.url
), "utf8");

const engine = await readFile(new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt",
  import.meta.url
), "utf8");

test("history resave keeps live artifacts and resolves only exact persisted build", () => {
  const start = source.indexOf("fun createDownloadTicket(");
  const fallback = source.indexOf("private fun persistedDeviceArtifact(", start);
  assert.ok(start >= 0 && fallback > start);
  const ticket = source.slice(start, fallback);
  assert.match(ticket, /DeviceBuildEngine\.artifact\s*\(\s*buildId\s*,\s*kind\s*\)/);
  assert.match(ticket, /persistedDeviceArtifact\s*\(\s*buildId\s*,\s*kind\s*\)/);
  assert.ok(ticket.indexOf("DeviceBuildEngine.artifact") < ticket.indexOf("persistedDeviceArtifact"));
  const resolver = source.slice(fallback, source.indexOf("fun projectQuota(", fallback));
  assert.match(resolver, /Regex\("\^local-\[0-9a-f\]\{20\}\$"\)/);
  assert.match(
    resolver,
    /ProjectLibrary\s*\.loadBuilds\s*\(\s*context\s*\)/
  );
  assert.match(resolver, /it\.id\s*==\s*buildId/);
  assert.match(resolver, /saved\.status\.equals\s*\(\s*"success"/);
  assert.match(resolver, /saved\.apkUrl/);
  assert.match(resolver, /saved\.aabUrl/);
  assert.match(resolver, /saved\.exeUrl/);
  assert.match(resolver, /directory\.parentFile\s*!=\s*root/);
  assert.match(resolver, /directory\.name\s*!=\s*buildId/);
  assert.match(resolver, /file\.parentFile\s*==\s*directory/);
  assert.match(resolver, /saved\.buildNo/);
  assert.match(resolver, /matches\.singleOrNull\(\)/);
  assert.doesNotMatch(resolver, /appforge-owner-vault-v1|Downloads\/AppForgeStudio/);
  assert.match(engine, /"device-build\/artifacts\/\$\{state\.id\}"/);
});

test("APK AAB and EXE must resolve to the same requested build ID only", () => {
  const resolver = source.slice(source.indexOf("private fun persistedDeviceArtifact("),
    source.indexOf("fun projectQuota("));
  for (const extension of ["apk", "aab", "exe"]) {
    assert.ok(resolver.includes(`"${extension}"`));
  }
  assert.match(
    resolver,
    /normalizedArtifactTicketKind\s*\(\s*kind\s*\)[\s\S]{0,160}?\?:\s*return@runCatching\s+null/
  );
  assert.match(resolver, /advertised\.isNullOrBlank\(\)/);
  assert.match(resolver, /file\.isFile && file\.length\(\) > 0L/);
});
