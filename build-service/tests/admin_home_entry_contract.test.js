import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const home =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt",
      import.meta.url
    ),
    "utf8"
  );

const main =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
      import.meta.url
    ),
    "utf8"
  );

test(
  "full admin has a visible home entry to AdminOps",
  () => {
    assert.match(
      home,
      /onOpenAdmin:\s*\(\)\s*->\s*Unit/
    );

    assert.match(
      home,
      /YÖNETİCİ PANELİ/
    );

    assert.match(
      home,
      /fullAdmin[\s\S]*?onOpenAdmin\(\)/
    );

    assert.match(
      home,
      /Hesap yönetimi • PRO yetkileri • Sistem durumu • Autoscale/
    );

    assert.match(
      main,
      /onOpenAdmin\s*=\s*\{[\s\S]*?AppScreen\.ADMIN_OPS/
    );

    assert.match(
      main,
      /AppScreen\.ADMIN_OPS\s*->\s*AdminOpsScreen/
    );
  }
);
