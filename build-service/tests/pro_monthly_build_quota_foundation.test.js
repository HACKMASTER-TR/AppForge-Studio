import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const config =
  fs.readFileSync(
    new URL(
      "../src/config.js",
      import.meta.url
    ),
    "utf8"
  );

const migration =
  fs.readFileSync(
    new URL(
      "../sql/022_monthly_build_quota.sql",
      import.meta.url
    ),
    "utf8"
  );

test(
  "Pro Monthly defaults to 100 successful builds per cycle",
  () => {
    assert.match(
      config,
      /proMonthlyBuildLimit/
    );

    assert.match(
      config,
      /PRO_MONTHLY_BUILD_LIMIT/
    );

    assert.match(
      config,
      /PRO_MONTHLY_BUILD_LIMIT[\s\S]*?100/
    );
  }
);

test(
  "monthly build quota uses durable usage and reservation ledgers",
  () => {
    assert.match(
      migration,
      /appforge_pro_monthly_build_usage/
    );

    assert.match(
      migration,
      /appforge_pro_monthly_build_reservations/
    );

    assert.match(
      migration,
      /build_id UUID PRIMARY KEY/
    );

    assert.match(
      migration,
      /cycle_end TIMESTAMPTZ NOT NULL/
    );
  }
);

test(
  "build history deletion cannot restore successful build quota",
  () => {
    const usageBlock =
      migration.match(
        /CREATE TABLE IF NOT EXISTS appforge_pro_monthly_build_usage[\s\S]*?\);/
      )?.[0] || "";

    assert.doesNotMatch(
      usageBlock,
      /REFERENCES appforge_builds/
    );
  }
);

test(
  "project package-lock foundation exists",
  () => {
    assert.match(
      migration,
      /package_locked_at/
    );
  }
);
