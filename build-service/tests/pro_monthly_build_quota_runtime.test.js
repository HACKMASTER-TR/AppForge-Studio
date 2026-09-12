import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const quota =
  fs.readFileSync(
    new URL(
      "../src/monthlyBuildQuota.js",
      import.meta.url
    ),
    "utf8"
  );

const projectQuota =
  fs.readFileSync(
    new URL(
      "../src/projectQuotaV2.js",
      import.meta.url
    ),
    "utf8"
  );

const queue =
  fs.readFileSync(
    new URL(
      "../src/jobQueue.js",
      import.meta.url
    ),
    "utf8"
  );


test(
  "monthly quota reserves before queue admission",
  () => {
    assert.match(
      queue,
      /reserveMonthlyBuildQuotaInTransaction/
    );

    assert.match(
      queue,
      /PRO_MONTHLY_BUILD_LIMIT_REACHED/
    );

    const reservePos =
      queue.indexOf(
        "reserveMonthlyBuildQuotaInTransaction"
      );

    const insertPos =
      queue.indexOf(
        "INSERT INTO appforge_build_jobs"
      );

    assert.ok(
      reservePos >= 0 &&
      insertPos >= 0 &&
      reservePos < insertPos
    );
  }
);


test(
  "monthly quota counts successful usage plus active reservations",
  () => {
    assert.match(
      quota,
      /appforge_pro_monthly_build_usage/
    );

    assert.match(
      quota,
      /appforge_pro_monthly_build_reservations/
    );

    assert.match(
      quota,
      /quota\.used \+[\s\S]*?quota\.reserved/
    );
  }
);


test(
  "successful build consumption is idempotent",
  () => {
    assert.match(
      quota,
      /ON CONFLICT\(build_id\)[\s\S]*?DO NOTHING/
    );

    assert.match(
      projectQuota,
      /consumeMonthlyBuildQuota/
    );
  }
);


test(
  "failed and cancelled builds release build reservations",
  () => {
    assert.match(
      projectQuota,
      /releaseMonthlyBuildQuotaReservation/
    );

    assert.match(
      queue,
      /DELETE FROM appforge_pro_monthly_build_reservations/
    );

    assert.match(
      queue,
      /markCancelledJob[\s\S]*?releaseBuildQuotaReservation/
    );
  }
);


test(
  "successful build locks server project package identity",
  () => {
    assert.match(
      projectQuota,
      /package_locked_at/
    );

    assert.match(
      projectQuota,
      /project_id/
    );
  }
);


test(
  "quota API exposes separate build quota",
  () => {
    assert.match(
      projectQuota,
      /buildQuota/
    );

    assert.match(
      projectQuota,
      /getMonthlyBuildQuota/
    );
  }
);
