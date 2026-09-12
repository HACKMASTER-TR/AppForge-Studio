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

const catalog =
  fs.readFileSync(
    new URL(
      "../src/quotaAddonProducts.js",
      import.meta.url
    ),
    "utf8"
  );

const migration =
  fs.readFileSync(
    new URL(
      "../sql/023_quota_addons.sql",
      import.meta.url
    ),
    "utf8"
  );

const projects =
  fs.readFileSync(
    new URL(
      "../src/projectQuotaV2.js",
      import.meta.url
    ),
    "utf8"
  );

const builds =
  fs.readFileSync(
    new URL(
      "../src/monthlyBuildQuota.js",
      import.meta.url
    ),
    "utf8"
  );


test(
  "quota add-on product ids are stable",
  () => {
    assert.match(
      config,
      /appforge_quota_10/
    );

    assert.match(
      config,
      /appforge_quota_25/
    );

    assert.match(
      config,
      /appforge_quota_50/
    );
  }
);


test(
  "quota add-on catalog matches approved model",
  () => {
    assert.match(
      catalog,
      /projectBonus:\s*10[\s\S]*?buildBonus:\s*20/
    );

    assert.match(
      catalog,
      /projectBonus:\s*25[\s\S]*?buildBonus:\s*50/
    );

    assert.match(
      catalog,
      /projectBonus:\s*50[\s\S]*?buildBonus:\s*100/
    );
  }
);


test(
  "purchase token can grant at most once",
  () => {
    assert.match(
      migration,
      /purchase_token_hash TEXT PRIMARY KEY/
    );
  }
);


test(
  "add-ons are tied to one subscription cycle",
  () => {
    assert.match(
      migration,
      /cycle_end TIMESTAMPTZ NOT NULL/
    );

    assert.match(
      migration,
      /status IN\s*\([\s\S]*?'granted'[\s\S]*?\)/
    );
  }
);


test(
  "project quota adds granted cycle bonuses",
  () => {
    assert.match(
      projects,
      /SUM\(project_bonus\)/
    );

    assert.match(
      projects,
      /proMonthlyProjectLimit \+[\s\S]*?addonProjectBonus/
    );
  }
);


test(
  "build quota adds granted cycle bonuses",
  () => {
    assert.match(
      builds,
      /SUM\(build_bonus\)/
    );

    assert.match(
      builds,
      /proMonthlyBuildLimit \+[\s\S]*?addonBuildBonus/
    );
  }
);
