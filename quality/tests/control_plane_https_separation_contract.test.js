import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..", "..");
const read = relative =>
  fs.readFileSync(path.join(root, relative), "utf8");

test("device build and HTTPS control plane stay separated", () => {
  const draft = read(
    "android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt"
  );
  assert.match(
    draft,
    /DEFAULT_BUILD_SERVICE_URL[\s\S]{0,80}?"device:\/\/local"/
  );
  // Local builds never become remote builds. Only HTTPS
  // control-plane routing differs between Debug and Release.
  assert.match(
    draft,
    /DEFAULT_CONTROL_PLANE_URL[\s\S]{0,200}?BuildConfig\.DEBUG[\s\S]{0,150}?"https:\/\/appforge-control-plane\.28550040284a\.workers\.dev"/
  );
  assert.match(
    draft,
    /else\s*\{\s*"https:\/\/api\.appforgecloud\.com"/
  );

  const buildClient = read(
    "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  );
  assert.match(buildClient, /DeviceBuildEngine\.start/);
  assert.doesNotMatch(
    buildClient,
    /HttpURLConnection|\/api\/builds/
  );
});

test("Account Admin Pro and Update use HTTPS control plane", () => {
  const main = read(
    "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  );
  const update = read(
    "android-app/app/src/main/java/com/appforge/studio/UpdateGateActivity.kt"
  );
  const purchases = read(
    "android-app/app/src/main/java/com/appforge/studio/ProPurchasesActivity.kt"
  );

  assert.match(
    main,
    /AppScreen\.ACCOUNT[\s\S]{0,250}?DEFAULT_CONTROL_PLANE_URL/
  );
  assert.match(
    main,
    /AppScreen\.ADMIN_OPS[\s\S]{0,300}?DEFAULT_CONTROL_PLANE_URL/
  );
  assert.match(
    main,
    /AppScreen\.PRO[\s\S]{0,350}?DEFAULT_CONTROL_PLANE_URL/
  );
  assert.match(
    main,
    /AUTO_PRO_STATUS_REFRESH_V1[\s\S]{0,1800}?DEFAULT_CONTROL_PLANE_URL/
  );

  assert.match(update, /DEFAULT_CONTROL_PLANE_URL/);
  assert.doesNotMatch(update, /DEFAULT_BUILD_SERVICE_URL/);

  assert.match(purchases, /DEFAULT_CONTROL_PLANE_URL/);
  assert.doesNotMatch(purchases, /DEFAULT_BUILD_SERVICE_URL/);
});

test("account client exposes real account routes and refuses device protocol", () => {
  const account = read(
    "android-app/app/src/main/java/com/appforge/studio/net/AppForgeAccountClient.kt"
  );

  const routes =
    new Set(
      [...account.matchAll(/path\s*=\s*"([^"]*\/api\/auth\/[^"]+)"/g)]
        .map(match => match[1])
    );

  assert.ok(
    routes.size >= 7,
    `expected at least 7 named account routes, got ${routes.size}`
  );

  assert.match(account, /https:\/\//);
  assert.match(account, /controlPlaneBaseUrl/);
});

test("admin screen has no autoscale or real build stress controls", () => {
  const admin = read(
    "android-app/app/src/main/java/com/appforge/studio/AdminOpsScreen.kt"
  );

  assert.doesNotMatch(admin, /Autoscale/i);
  assert.doesNotMatch(admin, /GERÇEK BUILD TESTİ/i);
  assert.doesNotMatch(admin, /startLoadTest/);
  assert.doesNotMatch(admin, /dispatchAutoscale/);
  assert.doesNotMatch(admin, /BuildApiClient/);

  assert.match(admin, /\/api\/admin\/system-status/);
  assert.match(admin, /fullAccess/);
  assert.match(admin, /Authorization/);
  assert.match(admin, /https:\/\//);
});

test("admin accounts auto-load from HTTPS service", () => {
  const accounts = read(
    "android-app/app/src/main/java/com/appforge/studio/AdminAccountsScreen.kt"
  );

  assert.match(
    accounts,
    /LaunchedEffect\(\s*serverUrl\s*,\s*apiKey\s*\)[\s\S]{0,250}?refresh\(\)/
  );
  assert.match(accounts, /\/api\/admin\/users/);
  assert.match(accounts, /controlPlaneBaseUrl/);
});

test("Pro status remains server verified", () => {
  const security = read(
    "android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt"
  );

  assert.match(security, /\/api\/pro\/status/);
  assert.match(security, /Authorization/);
  assert.match(security, /https:\/\//);
});

test("update failure keeps an offline continuation path", () => {
  const update = read(
    "android-app/app/src/main/java/com/appforge/studio/UpdateGateActivity.kt"
  );

  assert.match(
    update,
    /GateUiState\.Error[\s\S]{0,500}?canContinueOffline\s*=\s*true/
  );
  assert.match(
    update,
    /onContinue\s*=\s*::openStudio/
  );
  assert.match(
    update,
    /Çevrimdışı devam et/
  );
});
