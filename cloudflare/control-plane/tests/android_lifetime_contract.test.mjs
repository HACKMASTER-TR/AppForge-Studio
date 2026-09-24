import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const file = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const prefix = 'android-app/app/src/main/java/com/appforge/studio/';
const billing = file(prefix + 'security/StudioBillingManager.kt');
const main = file(prefix + 'MainActivity.kt');
const purchase = file(prefix + 'ProPurchasesActivity.kt');
const security = file(prefix + 'security/StudioSecurityClient.kt');
const worker = file('cloudflare/control-plane/src/index.mjs');
const pro = main.split('private fun ProUpgradeScreen(')[1].split('private fun ProPlanCard(')[0];
const account = main.split('private fun AccountScreen(')[1].split('private data class TemplateCategorySpec(')[0];
test('only one non-consumable Google Play INAPP product is queried', () => {
  assert.match(billing, /APPFORGE_LIFETIME_PRODUCT_ID = "appforge_pro_lifetime"/);
  assert.match(billing, /BillingClient\.ProductType\.INAPP/);
  assert.doesNotMatch(billing, /ProductType\.SUBS|consumeAsync|launchMonthly|launchQuotaAddon|monthlyProductId|quotaAddonProductIds/);
});
test('purchase restore checks purchased state and exact product', () => {
  assert.match(billing, /Purchase\.PurchaseState\.PURCHASED/);
  assert.match(billing, /lifetimeProductId in purchase\.products/);
  assert.match(billing, /purchase\.purchaseToken\.isNotBlank/);
  assert.match(billing, /distinctBy \{ it\.purchaseToken \}/);
});
test('purchase screens expose no monthly or add-on offers', () => {
  assert.doesNotMatch(pro + purchase, /launchMonthly|launchQuotaAddon|onBuyMonthly|Pro Aylık|Ek Kota Paketleri|abonelik dönemi|subscribe_monthly/);
  assert.match(pro, /PRO'YU ÖMÜR BOYU AÇ/);
  assert.match(purchase, /PRO'YU ÖMÜR BOYU AÇ/);
});
test('normal-user account form has been retired without deleting stored credentials', () => {
  assert.doesNotMatch(account, /Kayıt Ol|AppForgeAccountClient|forgotPassword|verifyEmail|resetPassword|transferDevice/);
  assert.match(account, /Hesapsız Kullanım/);
  assert.doesNotMatch(account, /clearSession|deleteAccount/);
});
test('server verification is required for paid access', () => {
  assert.match(security, /suspend fun verifyLifetimePurchase\(/);
  assert.match(security, /path = "\/api\/pro\/activate"/);
  assert.match(security, /json\.optBoolean\("active", false\)/);
  assert.match(security, /APPFORGE_LIFETIME_PRODUCT_ID/);
  assert.match(pro + purchase, /verifyLifetimePurchase/);
  assert.match(worker, /play_or_device_verification_not_configured/);
});
test('purchase stays disabled while the staging API is not ready', () => {
  assert.match(pro, /enabled = ready && prices\.lifetimeAvailable/);
  assert.match(purchase, /enabled = state\.serverReady && state\.prices\.lifetimeAvailable/);
  assert.doesNotMatch(worker, /\/api\/security\/config.*return json/);
});
