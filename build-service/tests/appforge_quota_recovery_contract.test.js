import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

async function read(path) {
  return readFile(
    new URL(
      `../../${path}`,
      import.meta.url
    ),
    "utf8"
  );
}

test(
  "backend preserves actionable quota error protocol",
  async () => {
    const server =
      await read(
        "build-service/server.js"
      );

    const quota =
      await read(
        "build-service/src/projectQuotaV2.js"
      );

    const monthly =
      await read(
        "build-service/src/monthlyBuildQuota.js"
      );

    const routeStart =
      server.indexOf(
        'app.post(\n  "/api/builds",'
      );

    const routeEnd =
      server.indexOf(
        "async function loadOwnedBuildForInsights",
        routeStart
      );

    assert.ok(routeStart >= 0);
    assert.ok(routeEnd > routeStart);

    const buildRoute =
      server.slice(
        routeStart,
        routeEnd
      );

    assert.match(
      buildRoute,
      /code:\s*error\?\.code/
    );

    assert.match(
      buildRoute,
      /quota:\s*error\?\.quota/
    );

    assert.match(
      buildRoute,
      /UPGRADE_PRO/
    );

    assert.match(
      buildRoute,
      /BUY_QUOTA_ADDON/
    );

    assert.match(
      quota,
      /FREE_PROJECT_LIMIT_REACHED/
    );

    assert.match(
      quota,
      /PRO_MONTHLY_PROJECT_LIMIT_REACHED/
    );

    assert.match(
      monthly,
      /PRO_MONTHLY_BUILD_LIMIT_REACHED/
    );
  }
);

test(
  "monthly addon quota context cannot recurse into itself",
  async () => {
    const source =
      await read(
        "build-service/src/monthlyBuildQuota.js"
      );

    const start =
      source.indexOf(
        "async function monthlyContextWithAddons("
      );

    const end =
      source.indexOf(
        "function monthlyContext(",
        start
      );

    assert.ok(start >= 0);
    assert.ok(end > start);

    const block =
      source.slice(
        start,
        end
      );

    assert.doesNotMatch(
      block,
      /await\s+monthlyContextWithAddons/
    );

    assert.match(
      block,
      /monthlyContext\(\s*entitlement\s*\)/
    );

    assert.match(
      block,
      /addonBuildBonusFromClient/
    );
  }
);

test(
  "Android routes quota exhaustion to user-initiated purchase surfaces",
  async () => {
    const client =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
      );

    const main =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    const billing =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/security/StudioBillingManager.kt"
      );

    assert.match(
      client,
      /class BuildApiException\(/
    );

    assert.match(
      client,
      /"recoveryAction"/
    );

    assert.match(
      client,
      /"code"/
    );

    assert.match(
      client,
      /"quota"/
    );

    for (const code of [
      "FREE_PROJECT_LIMIT_REACHED",
      "PRO_MONTHLY_PROJECT_LIMIT_REACHED",
      "PRO_MONTHLY_BUILD_LIMIT_REACHED"
    ]) {
      assert.ok(
        main.includes(`"${code}"`),
        `${code} Android recovery içinde yok`
      );
    }

    assert.match(
      main,
      /UPGRADE_PRO/
    );

    assert.match(
      main,
      /BUY_QUOTA_ADDON/
    );

    assert.match(
      main,
      /Pro Aylık paketine geç/
    );

    assert.match(
      main,
      /\+10, \+25 veya \+50 ek kota/
    );

    assert.match(
      main,
      /screen\s*=\s*AppScreen\.PRO/
    );

    assert.match(
      billing,
      /fun launchMonthly\(/
    );

    assert.match(
      billing,
      /fun launchQuotaAddon\(/
    );
  }
);
