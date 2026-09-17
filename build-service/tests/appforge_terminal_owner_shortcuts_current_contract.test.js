import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const source = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  ),
  "utf8"
);

test("owner terminal shortcuts use current commands", () => {
  for (const marker of [
    '"KOPYA"',
    '"APK"',
    '"DASH"',
    '"SUBMIT"',
    '"STATUS"',
    '"PREFLIGHT"',
    '"CI"',
    '"REPORT"',
    '"PERF"',
    '"ESC"',
    "appforge-apk",
    "./scripts/appforge dashboard",
    "./scripts/appforge submit",
    "./scripts/appforge status",
    "./scripts/appforge preflight",
    "./scripts/appforge ci",
    "./scripts/appforge report",
    "performanceSnapshot",
  ]) {
    assert.ok(
      source.includes(marker),
      `missing marker: ${marker}`
    );
  }
});

test("legacy PIPELINE label is gone", () => {
  assert.equal(
    source.includes('"PIPELINE"'),
    false
  );
});
