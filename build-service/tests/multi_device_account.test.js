import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const auth =
  fs.readFileSync(
    new URL(
      "../src/auth.js",
      import.meta.url
    ),
    "utf8"
  );

const migration =
  fs.readFileSync(
    new URL(
      "../sql/016_multi_account_devices.sql",
      import.meta.url
    ),
    "utf8"
  );

test(
  "account device unique user constraint is removed",
  () => {
    assert.match(
      migration,
      /DROP CONSTRAINT IF EXISTS appforge_account_devices_user_id_key/
    );
  }
);

test(
  "account supports multiple verified devices",
  () => {
    assert.match(
      auth,
      /MAX_ACCOUNT_DEVICES/
    );

    assert.match(
      auth,
      /DEVICE_ADD_REQUIRED/
    );

    assert.match(
      auth,
      /DEVICE_LIMIT_REACHED/
    );

    assert.doesNotMatch(
      auth,
      /30 günde bir/
    );
  }
);
