import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
const billing = fs.readFileSync(new URL("../../android-app/app/src/main/java/com/appforge/studio/security/StudioBillingManager.kt", import.meta.url), "utf8");
const main = fs.readFileSync(new URL("../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt", import.meta.url), "utf8");
test("Android Billing offers one lifetime non-consumable INAPP only", () => {
  assert.match(billing, /APPFORGE_LIFETIME_PRODUCT_ID\s*=\s*"appforge_pro_lifetime"/);
  assert.match(billing, /\.setProductType\(BillingClient\.ProductType\.INAPP\)/);
  assert.match(billing, /formattedPrice/);
  assert.doesNotMatch(billing, /ProductType\.SUBS|fun launchMonthly\s*\(|fun launchQuotaAddon\s*\(|quotaAddonProductIds|consumeAsync/);
});
test("restored and new purchases submit PURCHASED receipts for verification", () => {
  assert.match(billing, /fun restorePurchases\s*\(/);
  assert.match(billing, /Purchase\.PurchaseState\.PURCHASED/);
  assert.match(billing, /purchase\.purchaseToken\.isNotBlank\(\)/);
  assert.match(billing, /onPurchase\(StudioPurchaseResult/);
  assert.doesNotMatch(billing, /consumeAsync/);
});
test("Pro screen offers lifetime-only product; no obsolete monthly or addons", () => {
  const start=main.indexOf("private fun ProUpgradeScreen(");
  assert.ok(start>=0,"ProUpgradeScreen missing");
  const end=main.indexOf("\n@Composable",start+20);
  const screen=end>start?main.slice(start,end):main.slice(start);
  assert.match(screen,/Pro Ömür Boyu/);
  assert.match(screen,/launchLifetime\s*\(/);
  assert.doesNotMatch(screen,/launchMonthly\s*\(|EK PAKETİ AL|AYLIK 50 PROJE|subscribe_monthly/);
});
