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
      /val fullAdmin\s*=\s*OwnerAccessPolicy[\s\S]{0,300}?isActiveOwner/
    );

    assert.match(
      home,
      /if\s*\(fullAdmin\)[\s\S]{0,180}?TextButton\(onClick = onOpenAdmin\)[\s\S]{0,120}?Yönetici/
    );

    assert.ok(
      home.indexOf('Text("Yönetici")') <
        home.indexOf('Text("Ayarlar")')
    );

    assert.match(
      main,
      /onOpenAdmin\s*=\s*\{[\s\S]{0,260}?OwnerAccessPolicy[\s\S]{0,160}?isActiveOwner[\s\S]{0,220}?AppScreen\.ADMIN_OPS/
    );

    assert.doesNotMatch(home, /OwnerAdminCard\(/);
    assert.doesNotMatch(home, /GİRİŞ YAP/);

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
