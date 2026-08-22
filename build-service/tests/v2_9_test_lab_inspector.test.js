import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const server =
  new URL(
    "../server.js",
    import.meta.url
  );

const analysis =
  new URL(
    "../src/artifactAnalysis.js",
    import.meta.url
  );

const compare =
  new URL(
    "../src/buildCompare.js",
    import.meta.url
  );

const storage =
  new URL(
    "../src/storage.js",
    import.meta.url
  );

const main =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

test("Test Lab, compare and release note routes exist", async () => {
  const text =
    await readFile(
      server,
      "utf8"
    );

  for (const route of [
    "/api/builds/:id/test-lab",
    "/api/builds/compare",
    "/api/builds/:id/release-notes"
  ]) {
    assert.equal(
      text.includes(route),
      true,
      `Missing ${route}`
    );
  }
});

test("artifact analyzer categorizes archive contents", async () => {
  const text =
    await readFile(
      analysis,
      "utf8"
    );

  for (const token of [
    "topFiles",
    "groups",
    "uncompressedBytes",
    "javascript",
    "native",
    "resources"
  ]) {
    assert.equal(
      text.includes(token),
      true,
      `Missing ${token}`
    );
  }
});

test("build compare redacts sensitive fields", async () => {
  const text =
    await readFile(
      compare,
      "utf8"
    );

  assert.equal(
    text.includes(
      "SECRET_KEY"
    ),
    true
  );

  assert.equal(
    text.includes(
      "[REDACTED]"
    ),
    true
  );
});

test("preview inspector has console network performance security", async () => {
  const text =
    await readFile(
      main,
      "utf8"
    );

  for (const token of [
    "PreviewInspectorTab.CONSOLE",
    "PreviewInspectorTab.NETWORK",
    "PreviewInspectorTab.PERFORMANCE",
    "PreviewInspectorTab.SECURITY",
    "onConsoleMessage",
    "shouldInterceptRequest",
    "performance.getEntriesByType"
  ]) {
    assert.equal(
      text.includes(token),
      true,
      `Missing ${token}`
    );
  }
});

test("S3 output materialization prefers async streaming", async () => {
  const text =
    await readFile(
      storage,
      "utf8"
    );

  assert.equal(
    text.indexOf(
      "Symbol.asyncIterator"
    ) <
    text.indexOf(
      "transformToByteArray"
    ),
    true
  );

  assert.equal(
    text.includes(
      'error?.code !==\n      "EXDEV"'
    ),
    true
  );
});
