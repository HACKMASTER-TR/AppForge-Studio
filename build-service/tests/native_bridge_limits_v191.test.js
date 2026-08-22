
import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const enginePath =
  new URL(
    "../src/buildEngine.js",
    import.meta.url
  );

test("Native Bridge overall payload limit is 64 KB", async () => {
  const text = await readFile(enginePath, "utf8");
  assert.equal(text.includes("65536"), true);
});

test("Native Bridge share and clipboard limits are doubled", async () => {
  const text = await readFile(enginePath, "utf8");
  assert.equal(text.includes('safeText(title, 400)'), true);
  assert.equal(text.includes('safeText(text, 40000)'), true);
});

test("Native Bridge product and offer-token limits are doubled", async () => {
  const text = await readFile(enginePath, "utf8");
  assert.equal(text.includes('safeText(productId, 400)'), true);
  assert.equal(text.includes('safeText(offerToken, 8192)'), true);
});

test("scanner payload limit is doubled", async () => {
  const text = await readFile(enginePath, "utf8");
  assert.equal(text.includes(".take(8192)"), true);
});
