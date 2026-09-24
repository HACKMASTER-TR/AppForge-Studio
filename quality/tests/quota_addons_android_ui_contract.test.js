import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const main = fs.readFileSync(new URL("../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt", import.meta.url), "utf8");
const security = fs.readFileSync(new URL("../../android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt", import.meta.url), "utf8");

const start = main.indexOf("@Composable\nprivate fun ProUpgradeScreen(");
const end = start >= 0 ? main.indexOf("\n@Composable", start + 20) : -1;
const screen = start < 0 ? "" : end > start ? main.slice(start, end) : main.slice(start);

test("legacy security quota parser remains intact until migration", () => {
  assert.match(security, /data class QuotaStatus/);
  assert.match(security, /\/api\/projects\/quota/);
  assert.match(security, /buildQuota/);
});

test("lifetime Pro setup effect remains inside ProUpgradeScreen", () => {
  assert.ok(start >= 0, "ProUpgradeScreen missing");
  assert.match(screen, /LaunchedEffect\(serverUrl\)/);
  assert.doesNotMatch(screen, /LaunchedEffect\([\s\S]*?session\?\.token/);
});

test("Pro screen offers only lifetime purchase and restore", () => {
  assert.match(screen, /Pro Ömür Boyu/);
  assert.match(screen, /launchLifetime\s*\(/);
  assert.match(screen, /restorePurchases\s*\(/);
  assert.doesNotMatch(screen, /launchMonthly\s*\(|launchQuotaAddon\s*\(|redeemAddonPurchase|EK PAKETİ AL|AYLIK 50 PROJE/);
});

test("a Play receipt goes to the server and never grants Pro on its own", () => {
  assert.match(screen, /verifyLifetimePurchase\s*\(/);
  assert.match(screen, /onVerified\(status\)/);
  assert.match(screen, /ready\s*&&\s*prices\.lifetimeAvailable/);
  assert.match(screen, /Satın alma doğrulanamadı/);
  assert.doesNotMatch(screen, /session\s*!=\s*null\s*&&/);
});
