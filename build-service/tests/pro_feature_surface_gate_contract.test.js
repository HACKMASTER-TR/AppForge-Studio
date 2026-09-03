import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const pro = fs.readFileSync(
  new URL(
    "../src/proEntitlements.js",
    import.meta.url
  ),
  "utf8"
);

const main = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  ),
  "utf8"
);

test(
  "AdMob Firebase Messaging Deep Link Billing and remote bridge are Pro protected",
  () => {
    assert.match(
      pro,
      /c\?\.admob\?\.enabled/
    );

    assert.match(
      pro,
      /c\?\.firebase\?\.messaging/
    );

    assert.match(
      pro,
      /c\?\.deepLink\?\.enabled/
    );

    assert.match(
      pro,
      /c\?\.billing\?\.enabled/
    );

    assert.match(
      pro,
      /allowRemote/
    );

    assert.match(
      main,
      /Google AdMob • PRO/
    );

    assert.match(
      main,
      /Google Play Billing • PRO/
    );

    assert.match(
      main,
      /Firebase Analytics • PRO/
    );

    assert.match(
      main,
      /Firebase Crashlytics • PRO/
    );

    assert.match(
      main,
      /Firebase Cloud Messaging • PRO/
    );

    assert.match(
      main,
      /Deep Link aktif • PRO/
    );

    assert.match(
      main,
      /Uzak URL'de Native Bridge • PRO/
    );

    assert.match(
      main,
      /ProFeatureRequiredDialog/
    );
  }
);
