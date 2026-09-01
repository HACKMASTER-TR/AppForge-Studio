import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test(
  "AdMob optional interstitial and rewarded IDs do not create unresolved Kotlin references",
  async () => {
    const source = await readFile(
      new URL("../src/buildEngine.js", import.meta.url),
      "utf8"
    );

    assert.ok(
      source.includes(
        '${c.admob?.enabled ? "private var interstitialAd: InterstitialAd? = null" : ""}'
      )
    );

    assert.ok(
      source.includes(
        '${c.admob?.enabled ? "private var rewardedAd: RewardedAd? = null" : ""}'
      )
    );

    assert.ok(
      !source.includes(
        '${c.admob?.enabled && c.admob?.interstitialUnitId ? "private var interstitialAd: InterstitialAd? = null" : ""}'
      )
    );

    assert.ok(
      !source.includes(
        '${c.admob?.enabled && c.admob?.rewardedUnitId ? "private var rewardedAd: RewardedAd? = null" : ""}'
      )
    );
  }
);
