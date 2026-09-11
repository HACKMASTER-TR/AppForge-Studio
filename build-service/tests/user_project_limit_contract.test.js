import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const root =
  new URL(
    "../../",
    import.meta.url
  );

function read(relativePath) {
  return fs.readFileSync(
    new URL(
      relativePath,
      root
    ),
    "utf8"
  );
}


test(
  "FREE successful-project quota supports persistent custom limit",
  () => {
    const quota =
      read(
        "build-service/src/projectQuotaV2.js"
      );

    assert.match(
      quota,
      /appforge_user_project_limits/
    );

    assert.match(
      quota,
      /effectiveFreeLimit/
    );

    assert.match(
      quota,
      /appforge_free_project_slots/
    );
  }
);


test(
  "default FREE successful-project limit is 1",
  () => {
    const config =
      read(
        "build-service/src/config.js"
      );

    assert.match(
      config,
      /FREE_PROJECT_LIMIT[\s\S]*\|\|[\s\S]*1/
    );
  }
);


test(
  "Pro Monthly successful-project limit is 50",
  () => {
    const config =
      read(
        "build-service/src/config.js"
      );

    const quota =
      read(
        "build-service/src/projectQuotaV2.js"
      );

    assert.match(
      config,
      /PRO_MONTHLY_PROJECT_LIMIT[\s\S]*\|\|[\s\S]*50/
    );

    assert.match(
      quota,
      /appforge_pro_monthly_project_slots/
    );
  }
);


test(
  "admin can update per-user FREE limit",
  () => {
    const server =
      read(
        "build-service/server.js"
      );

    assert.match(
      server,
      /\/api\/admin\/users\/:userId\/project-limit/
    );

    assert.match(
      server,
      /customFreeProjectLimit/
    );
  }
);


test(
  "Android consumes server quota V2",
  () => {
    const main =
      read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    const api =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
      );

    const advisor =
      read(
        "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
      );

    assert.match(
      api,
      /\/api\/projects\/quota/
    );

    assert.match(
      api,
      /planKind/
    );

    assert.match(
      api,
      /failedBuildsConsumeQuota/
    );

    assert.doesNotMatch(
      main,
      /\.claimFreeProjectSlot\(/
    );

    assert.match(
      advisor,
      /free_project_limit_reached/
    );

    assert.match(
      advisor,
      /pro_monthly_project_limit_reached/
    );
  }
);
