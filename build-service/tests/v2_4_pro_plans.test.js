import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const main = new URL("../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt", import.meta.url);
const billing = new URL("../../android-app/app/src/main/java/com/appforge/studio/security/StudioBillingManager.kt", import.meta.url);
const pro = new URL("../src/proEntitlements.js", import.meta.url);
const quota = new URL("../src/projectQuotaV2.js", import.meta.url);
const config = new URL("../src/config.js", import.meta.url);

test("current Android Pro screen exposes lifetime only", async () => {
  const text = await readFile(main, "utf8");
  const start = text.indexOf("private fun ProUpgradeScreen(");
  assert.ok(start >= 0, "ProUpgradeScreen missing");
  const next = text.indexOf("\n@Composable", start + 20);
  const screen = next > start ? text.slice(start, next) : text.slice(start);
  assert.match(screen, /Pro Ömür Boyu/);
  assert.match(screen, /launchLifetime\s*\(/);
  assert.doesNotMatch(screen, /launchMonthly\s*\(|EK PAKETİ AL|AYLIK 50 PROJE|subscribe_monthly/);
});

test("current Android Billing uses lifetime INAPP and no SUBS", async () => {
  const text = await readFile(billing, "utf8");
  assert.match(text, /APPFORGE_LIFETIME_PRODUCT_ID/);
  assert.match(text, /ProductType\.INAPP/);
  assert.doesNotMatch(text, /ProductType\.SUBS|consumeAsync|launchMonthly\s*\(/);
});

test("retired build-service still rejects lifetime activation; never route new Android client there", async () => {
  const text = await readFile(pro, "utf8");
  assert.match(text, /LIFETIME_PRO_RETIRED/);
  assert.match(text, /plan\s*!==\s*"monthly"/);
  assert.match(text, /productType:[\s\S]*"subs"/);
});

test("legacy lifetime entitlement remains grandfathered in retired backend", async () => {
  const text = await readFile(quota, "utf8");
  assert.match(text, /planKind:[\s\S]*"legacy"/);
  assert.match(text, /unlimited:[\s\S]*true/);
});

test("retired build-service monthly limits remain unchanged by Cloudflare migration", async () => {
  const text = await readFile(config, "utf8");
  assert.match(text, /STUDIO_PRO_MONTHLY_PRODUCT_ID/);
  assert.match(text, /PRO_MONTHLY_PROJECT_LIMIT[\s\S]*\|\|[\s\S]*50/);
});
