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

const config =
  new URL(
    "../src/config.js",
    import.meta.url
  );

test("Pro screen exposes lifetime and monthly plans", async () => {
  const text =
    await readFile(
      main,
      "utf8"
    );

  assert.equal(
    text.includes(
      "ProPlanCard"
    ),
    true
  );

  assert.equal(
    text.includes(
      "pro_lifetime"
    ),
    true
  );

  assert.equal(
    text.includes(
      "pro_monthly"
    ),
    true
  );
});

test("Android uses Google Play Billing 9 plan manager", async () => {
  const text =
    await readFile(
      billing,
      "utf8"
    );

  assert.equal(
    text.includes(
      "BillingClient"
    ),
    true
  );

  assert.equal(
    text.includes(
      "ProductType.INAPP"
    ),
    true
  );

  assert.equal(
    text.includes(
      "ProductType.SUBS"
    ),
    true
  );
});

test("server supports monthly and lifetime entitlement activation", async () => {
  const text =
    await readFile(
      pro,
      "utf8"
    );

  assert.equal(
    text.includes(
      'plan === "monthly"'
    ),
    true
  );

  assert.equal(
    text.includes(
      '"subs"'
    ),
    true
  );

  assert.equal(
    text.includes(
      '"inapp"'
    ),
    true
  );
});

test("monthly product id is configurable", async () => {
  const text =
    await readFile(
      config,
      "utf8"
    );

  assert.equal(
    text.includes(
      "STUDIO_PRO_MONTHLY_PRODUCT_ID"
    ),
    true
  );
});
