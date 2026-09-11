import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const quota =
  new URL(
    "../src/projectQuotaV2.js",
    import.meta.url
  );

const server =
  new URL(
    "../server.js",
    import.meta.url
  );

const config =
  new URL(
    "../src/config.js",
    import.meta.url
  );

const buildClient =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt",
    import.meta.url
  );

test(
  "FREE successful-project limit defaults to one",
  async () => {
    const text =
      await readFile(
        config,
        "utf8"
      );

    assert.match(
      text,
      /FREE_PROJECT_LIMIT[\s\S]*\|\|[\s\S]*1/
    );
  }
);

test(
  "quota is atomic and server enforced",
  async () => {
    const text =
      await readFile(
        quota,
        "utf8"
      );

    assert.match(
      text,
      /pg_advisory_xact_lock/
    );

    assert.match(
      text,
      /FREE_PROJECT_LIMIT_REACHED/
    );

    assert.match(
      text,
      /appforge_project_quota_reservations/
    );

    assert.match(
      text,
      /quota\.used[\s\S]*quota\.reserved/
    );
  }
);

test(
  "raw build reserves quota on backend",
  async () => {
    const text =
      await readFile(
        server,
        "utf8"
      );

    const route =
      text.indexOf(
        '"/api/builds"'
      );

    const reserve =
      text.indexOf(
        "await reserveProjectQuota(",
        route
      );

    assert.equal(
      route >= 0,
      true
    );

    assert.equal(
      reserve > route,
      true
    );
  }
);

test(
  "quota endpoint exists",
  async () => {
    const text =
      await readFile(
        server,
        "utf8"
      );

    assert.equal(
      text.includes(
        '"/api/projects/quota"'
      ),
      true
    );
  }
);

test(
  "build client sends saved bearer session",
  async () => {
    const text =
      await readFile(
        buildClient,
        "utf8"
      );

    assert.match(
      text,
      /SecureAccountStore/
    );

    assert.match(
      text,
      /"Authorization"/
    );

    assert.match(
      text,
      /"Bearer \$it"/
    );
  }
);
