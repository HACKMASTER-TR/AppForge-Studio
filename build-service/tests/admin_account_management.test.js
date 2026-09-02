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

const auth =
  fs.readFileSync(
    new URL(
      "../src/auth.js",
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

test(
  "admin account management is protected and wired",
  () => {
    assert.match(
      server,
      /\/api\/admin\/users/
    );

    assert.match(
      server,
      /\/api\/admin\/users\/:userId\/pro/
    );

    assert.match(
      server,
      /adminRequired/
    );

    assert.match(
      server,
      /grantPro/
    );

    assert.match(
      server,
      /revokePro/
    );

    assert.match(
      auth,
      /adminOwnedDevice/
    );

    assert.match(
      admin,
      /FREE HESAP OLUŞTUR/
    );

    assert.match(
      admin,
      /HESAP OLUŞTUR \+ PRO VER/
    );

    assert.match(
      admin,
      /PRO KALDIR/
    );

    assert.match(
      admin,
      /PRO VER/
    );
  }
);


test(
  "Google Play Pro cannot be changed from admin panel",
  () => {
    assert.match(
      server,
      /currentSource\.startsWith\(\s*"google_play"\s*\)/
    );

    assert.match(
      server,
      /Google Play PRO satın alma tarafından yönetilir/
    );

    assert.match(
      admin,
      /GOOGLE PLAY PRO/
    );

    assert.match(
      admin,
      /SATIN ALMA İLE YÖNETİLİR/
    );

    assert.match(
      admin,
      /!googlePlayManaged/
    );

    assert.match(
      admin,
      /Admin paneli bu PRO yetkisini değiştiremez/
    );
  }
);
