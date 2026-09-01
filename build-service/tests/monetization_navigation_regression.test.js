import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("AdMob optional units keep required Kotlin type imports", async () => {
  const source = await readFile(
    new URL("../src/buildEngine.js", import.meta.url),
    "utf8"
  );

  assert.match(
    source,
    /c\.admob\?\.enabled \? "com\.google\.android\.gms\.ads\.interstitial\.InterstitialAd"/
  );

  assert.match(
    source,
    /c\.admob\?\.enabled \? "com\.google\.android\.gms\.ads\.rewarded\.RewardedAd"/
  );
});

test("Billing 9 query callback has explicit result types", async () => {
  const source = await readFile(
    new URL("../src/buildEngine.js", import.meta.url),
    "utf8"
  );

  assert.match(
    source,
    /com\.android\.billingclient\.api\.QueryProductDetailsResult/
  );

  assert.match(
    source,
    /result: BillingResult,\s*detailsResult: QueryProductDetailsResult/
  );
});

test("terminal failed build state is cleared when leaving build step", async () => {
  const source = await readFile(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
      import.meta.url
    ),
    "utf8"
  );

  assert.match(
    source,
    /step == 10[\s\S]*?startsWith\([\s\S]*?"hata:"[\s\S]*?\|\|[\s\S]*?"failed"/
  );

  assert.match(
    source,
    /status\s*=\s*"Hazır"[\s\S]*?progress\s*=\s*0[\s\S]*?buildId\s*=\s*null/
  );
});
