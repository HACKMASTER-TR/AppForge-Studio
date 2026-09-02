import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const main = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  ),
  "utf8"
);

test(
  "FREE users cannot select Release Keystore while PRO users can",
  () => {
    assert.match(
      main,
      /isPro:\s*Boolean/
    );

    assert.match(
      main,
      /Release Keystore • PRO/
    );

    assert.match(
      main,
      /if\s*\(\s*isPro\s*\)[\s\S]*?SigningMode\.CUSTOM/
    );

    assert.match(
      main,
      /showProRequired\s*=\s*true/
    );

    assert.match(
      main,
      /Release Keystore ile kendi imzalama anahtarını kullanmak için AppForge PRO gereklidir/
    );

    assert.match(
      main,
      /onOpenPro\(\)/
    );

    assert.match(
      main,
      /proStatus\?\.active\s*==\s*true/
    );

    assert.match(
      main,
      /AppScreen\.PRO/
    );
  }
);
