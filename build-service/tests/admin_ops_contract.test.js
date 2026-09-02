import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function text(path) {
  return fs.readFileSync(
    new URL(
      path,
      import.meta.url
    ),
    "utf8"
  );
}

test(
  "full admin production controls are wired",
  () => {
    const config =
      text(
        "../src/config.js"
      );

    const queue =
      text(
        "../src/jobQueue.js"
      );

    const rate =
      text(
        "../src/rateLimit.js"
      );

    const server =
      text(
        "../server.js"
      );

    const migration =
      text(
        "../sql/018_full_admin_access.sql"
      );

    assert.match(
      config,
      /adminActiveBuildLimit/
    );

    assert.match(
      config,
      /adminBuildPriority/
    );

    assert.match(
      queue,
      /plan =\s*isAdmin\s*\?\s*"admin"/
    );

    assert.match(
      queue,
      /config\.adminActiveBuildLimit/
    );

    assert.match(
      rate,
      /X-AppForge-RateLimit-Bypass/
    );

    assert.match(
      server,
      /\/api\/admin\/system-status/
    );

    assert.match(
      server,
      /\/api\/admin\/autoscale\/dispatch/
    );

    assert.match(
      server,
      /\/api\/admin\/build-statuses/
    );

    assert.match(
      migration,
      /28550040284a@gmail\.com/
    );

    assert.match(
      migration,
      /role = 'admin'/
    );

    assert.match(
      migration,
      /admin_full_access/
    );
  }
);
