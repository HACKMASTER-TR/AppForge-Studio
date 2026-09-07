import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const server =
  fs.readFileSync(
    new URL(
      "../server.js",
      import.meta.url
    ),
    "utf8"
  );

const admin =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/AdminAccountsScreen.kt",
      import.meta.url
    ),
    "utf8"
  );

const migration =
  fs.readFileSync(
    new URL(
      "../sql/020_legacy_device_login_permission.sql",
      import.meta.url
    ),
    "utf8"
  );

test(
  "legacy device login is a per-account admin permission",
  () => {
    assert.match(
      migration,
      /allow_legacy_device_login/
    );

    assert.match(
      migration,
      /DEFAULT FALSE/
    );

    assert.match(
      server,
      /accountAllowsLegacyDeviceLogin/
    );

    assert.match(
      server,
      /resolveLoginDeviceId/
    );

    assert.match(
      server,
      /allow_legacy_device_login AS "allowLegacyDeviceLogin"/
    );

    assert.match(
      server,
      /\/api\/admin\/users\/:userId\/legacy-device-login/
    );

    assert.match(
      server,
      /target\.role ===\s*"admin"/
    );

    assert.match(
      server,
      /ADMIN hesaplarında eski APK cihaz muafiyeti açılamaz/
    );
  }
);

test(
  "legacy permission keeps normal device validation when header exists",
  () => {
    assert.match(
      server,
      /if \(rawDeviceId\)/
    );

    assert.match(
      server,
      /return requestDeviceId\(\s*req\s*\)/
    );

    assert.match(
      server,
      /Boolean\(\s*deviceId\s*\)/
    );
  }
);

test(
  "admin account screen exposes legacy APK permission",
  () => {
    assert.match(
      admin,
      /allowLegacyDeviceLogin/
    );

    assert.match(
      admin,
      /ESKİ APK GİRİŞİNE İZİN VER/
    );

    assert.match(
      admin,
      /ESKİ APK GİRİŞİNİ KAPAT/
    );

    assert.match(
      admin,
      /legacy-device-login/
    );

    assert.match(
      admin,
      /Parola ve 2FA kontrolleri devam eder/
    );
  }
);
