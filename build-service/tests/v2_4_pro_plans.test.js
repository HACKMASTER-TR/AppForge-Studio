import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const main =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

const billing =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/security/StudioBillingManager.kt",
    import.meta.url
  );

const pro =
  new URL(
    "../src/proEntitlements.js",
    import.meta.url
  );

const quota =
  new URL(
    "../src/projectQuotaV2.js",
    import.meta.url
  );

const config =
  new URL(
    "../src/config.js",
    import.meta.url
  );


test(
  "Pro screen exposes monthly 50-project plan only",
  async () => {
    const text =
      await readFile(
        main,
        "utf8"
      );

    const start =
      text.indexOf(
        "private fun ProUpgradeScreen("
      );

    assert.ok(
      start >= 0,
      "ProUpgradeScreen missing"
    );

    const nextComposable =
      text.indexOf(
        "\n@Composable",
        start + 20
      );

    const proScreen =
      nextComposable > start
        ? text.slice(
            start,
            nextComposable
          )
        : text.slice(
            start
          );

    assert.match(
      proScreen,
      /ProPlanCard/
    );

    assert.match(
      proScreen,
      /pro_monthly/
    );

    assert.match(
      proScreen,
      /AYLIK 50 PROJE/
    );

    assert.match(
      proScreen,
      /50 başarılı farklı proje/
    );

    assert.doesNotMatch(
      proScreen,
      /"TEK SEFERLİK"/
    );

    assert.doesNotMatch(
      proScreen,
      /\.launchLifetime\(/
    );

    assert.doesNotMatch(
      proScreen,
      /Sınırsız proje oluşturma/
    );
  }
);


test(
  "Google Play monthly subscription uses SUBS",
  async () => {
    const text =
      await readFile(
        billing,
        "utf8"
      );

    assert.match(
      text,
      /BillingClient/
    );

    assert.match(
      text,
      /ProductType\s*\.\s*SUBS/
    );
  }
);


test(
  "server rejects new lifetime Pro activation",
  async () => {
    const text =
      await readFile(
        pro,
        "utf8"
      );

    assert.match(
      text,
      /LIFETIME_PRO_RETIRED/
    );

    assert.match(
      text,
      /plan\s*!==\s*"monthly"/
    );

    assert.match(
      text,
      /productType:[\s\S]*"subs"/
    );
  }
);


test(
  "legacy lifetime entitlement remains grandfathered",
  async () => {
    const text =
      await readFile(
        quota,
        "utf8"
      );

    assert.match(
      text,
      /planKind:[\s\S]*"legacy"/
    );

    assert.match(
      text,
      /unlimited:[\s\S]*true/
    );
  }
);


test(
  "monthly plan has 50 successful-project quota",
  async () => {
    const text =
      await readFile(
        config,
        "utf8"
      );

    assert.match(
      text,
      /STUDIO_PRO_MONTHLY_PRODUCT_ID/
    );

    assert.match(
      text,
      /PRO_MONTHLY_PROJECT_LIMIT[\s\S]*\|\|[\s\S]*50/
    );
  }
);
