import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const server =
  fs.readFileSync(
    new URL(
      "../server.js",
      import.meta.url
    ),
    "utf8"
  );

const security =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt",
      import.meta.url
    ),
    "utf8"
  );

const billing =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/security/StudioBillingManager.kt",
      import.meta.url
    ),
    "utf8"
  );


test(
  "security config publishes quota product ids",
  () => {
    assert.match(
      server,
      /quota10ProductId/
    );

    assert.match(
      server,
      /quota25ProductId/
    );

    assert.match(
      server,
      /quota50ProductId/
    );
  }
);


test(
  "Android security config receives all quota products",
  () => {
    assert.match(
      security,
      /quota10ProductId/
    );

    assert.match(
      security,
      /quota25ProductId/
    );

    assert.match(
      security,
      /quota50ProductId/
    );
  }
);


test(
  "Android redeem request uses Integrity and official server endpoint",
  () => {
    assert.match(
      security,
      /quota_addon_redeem/
    );

    assert.match(
      security,
      /\/api\/quota\/addons\/redeem/
    );

    assert.match(
      security,
      /purchaseToken/
    );
  }
);


test(
  "Billing Manager queries quota products as INAPP",
  () => {
    assert.match(
      billing,
      /quotaAddonProductIds/
    );

    assert.match(
      billing,
      /ProductType[\s\S]*?INAPP/
    );

    assert.match(
      billing,
      /quotaAddonDetails/
    );
  }
);


test(
  "Billing Manager exposes Play formatted prices",
  () => {
    assert.match(
      billing,
      /quotaAddonPrices/
    );

    assert.match(
      billing,
      /formattedPrice/
    );
  }
);


test(
  "Billing Manager can launch a quota add-on purchase",
  () => {
    assert.match(
      billing,
      /fun launchQuotaAddon/
    );

    assert.match(
      billing,
      /quotaAddonDetails/
    );
  }
);
