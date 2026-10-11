import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const ops = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/AdminOpsScreen.kt",
    import.meta.url
  ),
  "utf8"
);

test("active admin screen respects Android system bars", () => {
  assert.match(
    ops,
    /\.fillMaxSize\(\)[\s\S]*?\.statusBarsPadding\(\)[\s\S]*?\.navigationBarsPadding\(\)/
  );
});
