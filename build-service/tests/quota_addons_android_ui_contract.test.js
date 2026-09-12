import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const main =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
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


test(
  "Android can fetch project and successful-build quota",
  () => {
    assert.match(
      security,
      /data class QuotaStatus/
    );

    assert.match(
      security,
      /\/api\/projects\/quota/
    );

    assert.match(
      security,
      /buildQuota/
    );
  }
);


test(
  "quota refresh effect stays inside ProUpgradeScreen scope",
  () => {
    const proStart =
      main.indexOf(
        "@Composable\nprivate fun ProUpgradeScreen("
      );

    assert.ok(
      proStart >= 0
    );

    const marker =
      "LaunchedEffect(\n        serverUrl,\n        session?.token";

    const first =
      main.indexOf(
        marker
      );

    const insidePro =
      main.indexOf(
        marker,
        proStart
      );

    assert.ok(
      insidePro > proStart
    );

    assert.equal(
      first,
      insidePro
    );
  }
);


test(
  "purchase callback separates add-ons from Pro subscription",
  () => {
    assert.match(
      main,
      /redeemAddonPurchase/
    );

    assert.match(
      main,
      /quota10ProductId/
    );

    assert.match(
      main,
      /quota25ProductId/
    );

    assert.match(
      main,
      /quota50ProductId/
    );

    assert.match(
      main,
      /Bu ürün artık AppForge tarafından satılmıyor/
    );
  }
);


test(
  "billing manager receives all quota product ids",
  () => {
    assert.match(
      main,
      /quotaAddonProductIds[\s\S]*?quota10ProductId[\s\S]*?quota25ProductId[\s\S]*?quota50ProductId/
    );
  }
);


test(
  "Pro screen shows approved monthly quota model",
  () => {
    assert.match(
      main,
      /AYLIK 50 PROJE • 100 BUILD/
    );

    assert.match(
      main,
      /50 başarılı farklı proje/
    );

    assert.match(
      main,
      /100 başarılı build/
    );
  }
);


test(
  "Pro screen exposes all three add-on packages",
  () => {
    assert.match(
      main,
      /\+10 PROJE • \+20 BUILD/
    );

    assert.match(
      main,
      /\+25 PROJE • \+50 BUILD/
    );

    assert.match(
      main,
      /\+50 PROJE • \+100 BUILD/
    );
  }
);


test(
  "add-ons use Google Play formatted prices",
  () => {
    assert.match(
      main,
      /quotaAddonPrices/
    );

    assert.match(
      main,
      /quotaAddonAvailability/
    );
  }
);


test(
  "add-on UI is limited to active monthly Pro",
  () => {
    assert.match(
      main,
      /monthlyProActive/
    );

    assert.match(
      main,
      /google_play_subscription/
    );

    assert.match(
      main,
      /Sonraki aya devretmez/
    );
  }
);


test(
  "successful rebuild wording distinguishes project and build quota",
  () => {
    assert.match(
      main,
      /projectId tekrar build edilirse proje hakkı yeniden düşmez/
    );

    assert.match(
      main,
      /Her başarılı build build kotasından 1 düşer/
    );
  }
);
