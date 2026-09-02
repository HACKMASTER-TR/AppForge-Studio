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

test("Billing 9 query uses explicit ProductDetailsResponseListener", async () => {
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
    /com\.android\.billingclient\.api\.ProductDetailsResponseListener/
  );

  assert.match(
    source,
    /object\s*:\s*ProductDetailsResponseListener/
  );

  assert.match(
    source,
    /override fun onProductDetailsResponse\(\s*result: BillingResult,\s*detailsResult: QueryProductDetailsResult/
  );
});

test("Billing empty consumable ids generate a typed empty set", async () => {
  const source = await readFile(
    new URL("../src/buildEngine.js", import.meta.url),
    "utf8"
  );

  assert.match(
    source,
    /consumableIds\.length[\s\S]*?"emptySet\(\)"/
  );
});

test("all terminal build states are cleared when leaving build step", async () => {
  const source = await readFile(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
      import.meta.url
    ),
    "utf8"
  );

  assert.match(
    source,
    /step == 10[\s\S]*?startsWith\([\s\S]*?"hata:"/
  );

  for (const terminalStatus of [
    "success",
    "failed",
    "cancelled",
    "canceled",
  ]) {
    assert.match(
      source,
      new RegExp(`terminalStatus\\s*==\\s*"${terminalStatus}"`)
    );
  }

  assert.match(
    source,
    /status\s*=\s*"Hazır"[\s\S]*?progress\s*=\s*0[\s\S]*?buildId\s*=\s*null/
  );

  assert.match(
    source,
    /logs\s*=\s*emptyList\(\)[\s\S]*?preflight\s*=\s*emptyList\(\)[\s\S]*?buildNo\s*=\s*null/
  );

  assert.match(
    source,
    /apkUrl\s*=\s*null[\s\S]*?aabUrl\s*=\s*null[\s\S]*?exeUrl\s*=\s*null/
  );
});
