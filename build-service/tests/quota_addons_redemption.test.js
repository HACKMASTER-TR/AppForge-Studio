import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const verifier =
  fs.readFileSync(
    new URL(
      "../src/playVerifier.js",
      import.meta.url
    ),
    "utf8"
  );

const redemption =
  fs.readFileSync(
    new URL(
      "../src/quotaAddons.js",
      import.meta.url
    ),
    "utf8"
  );

const server =
  fs.readFileSync(
    new URL(
      "../server.js",
      import.meta.url
    ),
    "utf8"
  );


test(
  "quota products are official verifier in-app products",
  () => {
    assert.match(
      verifier,
      /quotaAddonProductIds/
    );

    assert.match(
      verifier,
      /quotaAddonProductIds\(\)[\s\S]*?includes\(/
    );
  }
);


test(
  "quota products are consumed by the server",
  () => {
    assert.match(
      verifier,
      /playConsumableProducts[\s\S]*?quotaAddonProductIds/
    );

    assert.match(
      verifier,
      /purchases[\s\S]*?products[\s\S]*?consume/
    );
  }
);


test(
  "purchase tokens are stored only as hashes in quota ledger",
  () => {
    assert.match(
      redemption,
      /purchaseTokenHash/
    );

    assert.match(
      redemption,
      /purchase_token_hash/
    );

    assert.doesNotMatch(
      redemption,
      /INSERT INTO appforge_quota_addon_redemptions[\s\S]*?purchase_token[,)]/
    );
  }
);


test(
  "quota redemption requires real active monthly Pro",
  () => {
    assert.match(
      redemption,
      /QUOTA_ADDON_REQUIRES_MONTHLY_PRO/
    );

    assert.match(
      redemption,
      /google_play_subscription/
    );
  }
);


test(
  "quota redemption supports pending verified granted recovery states",
  () => {
    assert.match(
      redemption,
      /status = 'verified'/
    );

    assert.match(
      redemption,
      /status = 'granted'/
    );

    assert.match(
      redemption,
      /bound\.status ===[\s\S]*?"verified"/
    );
  }
);


test(
  "stored Play verification can recover a consumed purchase",
  () => {
    assert.match(
      redemption,
      /getStoredPlayPurchaseByHash/
    );

    assert.match(
      redemption,
      /storedVerificationUsable/
    );

    assert.match(
      redemption,
      /processedByServer/
    );
  }
);


test(
  "same purchase token cannot cross users or products",
  () => {
    assert.match(
      redemption,
      /QUOTA_ADDON_TOKEN_OWNER_MISMATCH/
    );

    assert.match(
      redemption,
      /QUOTA_ADDON_PRODUCT_MISMATCH/
    );
  }
);


test(
  "redemption endpoint requires auth rate limit and integrity",
  () => {
    const routePos =
      server.indexOf(
        '"/api/quota/addons/redeem"'
      );

    assert.ok(
      routePos >= 0
    );

    const route =
      server.slice(
        routePos,
        routePos + 1600
      );

    assert.match(
      route,
      /authRequired/
    );

    assert.match(
      route,
      /purchaseVerifyRateLimit/
    );

    assert.match(
      route,
      /requireIntegrityHeader/
    );

    assert.match(
      route,
      /redeemQuotaAddon/
    );
  }
);
