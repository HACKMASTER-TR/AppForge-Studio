import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const source = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt",
    import.meta.url
  ),
  "utf8"
);

test("device Web APK manifest starts with XML declaration despite multiline permissions", () => {
  const start = source.indexOf("private fun webManifest(");
  const end = source.indexOf("private fun detectGradleVersion(", start);

  assert.ok(start >= 0 && end > start);

  const manifest = source.slice(start, end);

  assert.match(manifest, /<\?xml version="1\.0" encoding="utf-8"\?>/);
  assert.match(manifest, /val permissions = buildString/);
  assert.match(manifest, /"""\.trimIndent\(\)\.trimStart\(\)/);
  assert.match(source, /writeText\(webManifest\(draft\)\)/);
});
