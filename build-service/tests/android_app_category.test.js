import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  resolveAndroidAppCategory,
  androidAppCategoryAttribute
} from "../src/androidAppCategory.js";

test("Android app category is inferred for games and can be overridden", () => {
  assert.equal(
    resolveAndroidAppCategory({
      appCategory: "auto",
      sourceBuildEngine: "unity-android"
    }),
    "game"
  );

  assert.equal(
    resolveAndroidAppCategory({
      appCategory: "auto",
      appName: "Uzay Oyunu"
    }),
    "game"
  );

  assert.equal(
    resolveAndroidAppCategory({
      appCategory: "none",
      appName: "Puzzle Game"
    }),
    null
  );

  assert.equal(
    resolveAndroidAppCategory({
      appCategory: "game",
      appName: "Notlar"
    }),
    "game"
  );

  assert.equal(
    androidAppCategoryAttribute({
      appCategory: "game"
    }).includes('android:appCategory="game"'),
    true
  );
});

test("full and fast generated manifests use the category resolver", async () => {
  const full = await readFile(
    new URL("../src/buildEngine.js", import.meta.url),
    "utf8"
  );
  const fast = await readFile(
    new URL("../src/fastBuild.js", import.meta.url),
    "utf8"
  );

  assert.equal(full.includes("androidAppCategoryAttribute"), true);
  assert.equal(fast.includes("androidAppCategoryAttribute"), true);
});
