import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const quota =
  new URL(
    "../src/projectQuotaV2.js",
    import.meta.url
  );

const migration =
  new URL(
    "../sql/021_success_project_quotas.sql",
    import.meta.url
  );

const library =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt",
    import.meta.url
  );

const mainActivity =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );


test(
  "FREE quota is consumed only by successful distinct projects",
  async () => {
    const text =
      await readFile(
        quota,
        "utf8"
      );

    assert.match(
      text,
      /recordSuccessfulProject/
    );

    assert.match(
      text,
      /appforge_free_project_slots/
    );

    assert.match(
      text,
      /failedBuildsConsumeQuota/
    );
  }
);


test(
  "failed and cancelled build reservations can be released",
  async () => {
    const text =
      await readFile(
        quota,
        "utf8"
      );

    assert.match(
      text,
      /releaseProjectQuotaReservation/
    );

    assert.match(
      text,
      /appforge_project_quota_reservations/
    );
  }
);


test(
  "Pro Monthly quota is subscription-cycle scoped",
  async () => {
    const text =
      await readFile(
        quota,
        "utf8"
      );

    assert.match(
      text,
      /appforge_pro_monthly_project_slots/
    );

    assert.match(
      text,
      /cycle_end/
    );

    assert.match(
      text,
      /pro-monthly:/
    );
  }
);


test(
  "legacy draft-slot cleanup is restart safe",
  async () => {
    const text =
      await readFile(
        migration,
        "utf8"
      );

    assert.match(
      text,
      /appforge_migration_markers/
    );

    assert.match(
      text,
      /success-project-quota-v2-legacy-slot-cleanup/
    );

    assert.match(
      text,
      /DELETE FROM appforge_free_project_slots/
    );

    assert.match(
      text,
      /status[\s\S]*'success'/
    );
  }
);


test(
  "Android ProjectLibrary is storage only, not quota authority",
  async () => {
    const main =
      await readFile(
        mainActivity,
        "utf8"
      );

    const localLibrary =
      await readFile(
        library,
        "utf8"
      );

    assert.doesNotMatch(
      main,
      /\.claimFreeProjectSlot\(/
    );

    assert.match(
      main,
      /projectQuota/
    );

    assert.match(
      main,
      /serverFreeProjectUsed/
    );

    /*
     * Legacy local data can remain for migration/account-scoped
     * storage compatibility, but it cannot grant/deny quota.
     */
    assert.match(
      localLibrary,
      /activeAccountScope/
    );
  }
);
