import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const server = fs.readFileSync(new URL("../server.js", import.meta.url), "utf8");
const security = fs.readFileSync(new URL("../../android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt", import.meta.url), "utf8");
const billing = fs.readFileSync(new URL("../../android-app/app/src/main/java/com/appforge/studio/security/StudioBillingManager.kt", import.meta.url), "utf8");

test("legacy build-service quota endpoints remain isolated from new Cloudflare product", () => {
  assert.match(server, /quota10ProductId/);
  assert.match(server, /quota25ProductId/);
  assert.match(server, /quota50ProductId/);
  assert.match(security, /\/api\/quota\/addons\/redeem/);
});

test("Android Billing Manager offers exactly one non-consumable lifetime INAPP", () => {
  assert.match(billing, /APPFORGE_LIFETIME_PRODUCT_ID\s*=\s*"appforge_pro_lifetime"/);
  assert.match(billing, /\.setProductType\(BillingClient\.ProductType\.INAPP\)/);
  assert.match(billing, /formattedPrice/);
  assert.doesNotMatch(billing, /ProductType\.SUBS|fun launchMonthly\s*\(|fun launchQuotaAddon\s*\(|quotaAddonProductIds|consumeAsync/);
});

test("restored and new purchases are PURCHASED receipts sent for verification", () => {
  assert.match(billing, /fun restorePurchases\s*\(/);
  assert.match(billing, /Purchase\.PurchaseState\.PURCHASED/);
  assert.match(billing, /purchase\.purchaseToken\.isNotBlank\(\)/);
  assert.match(billing, /onPurchase\(StudioPurchaseResult/);
  assert.doesNotMatch(billing, /consumeAsync/);
});
