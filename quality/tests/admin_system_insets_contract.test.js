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

const accounts = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/AdminAccountsScreen.kt",
    import.meta.url
  ),
  "utf8"
);

test("admin screens respect Android system bars", () => {
  for (const source of [ops, accounts]) {
    assert.match(
      source,
      /\.fillMaxSize\(\)[\s\S]*?\.statusBarsPadding\(\)[\s\S]*?\.navigationBarsPadding\(\)/
    );
  }
});
