import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const usage = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/tools/OtherAppsUsageGate.kt",
    import.meta.url
  ),
  "utf8"
);

const otherApps = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ui/OtherAppsScreen.kt",
    import.meta.url
  ),
  "utf8"
);

const excel = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/tools/excel/ExcelToolsScreen.kt",
    import.meta.url
  ),
  "utf8"
);

const video = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/hackmaster/videoforge/VideoForgeActivity.kt",
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

test("FREE keeps the existing one-use local policy", () => {
  assert.match(
    usage,
    /FREE_LIMIT\s*=\s*1/
  );

  assert.match(
    video,
    /1 ücretsiz kullanım hakkını kullandın/
  );

  assert.match(
    excel,
    /1 ücretsiz kullanım hakkını kullandın/
  );
});

test("Lifetime Pro and verified Owner are unlimited", () => {
  assert.match(
    otherApps,
    /PRO \/ Yönetici • Sınırsız kullanım/
  );

  assert.match(
    video,
    /PRO \/ Yönetici • Sınırsız kullanım/
  );

  assert.match(
    excel,
    /PRO \/ Yönetici: Sınırsız kullanım/
  );
});

test("VideoForge no longer uses project quota", () => {
  assert.doesNotMatch(
    video,
    /SecureAccountStore/
  );

  assert.doesNotMatch(
    video,
    /StudioSecurityClient/
  );

  assert.doesNotMatch(
    video,
    /consumeOtherAppProjectQuota/
  );

  assert.doesNotMatch(
    video,
    /proje kotası/i
  );

  assert.match(
    video,
    /if \(proUnlocked\) \{\s*onGranted\(\)\s*return\s*\}/
  );
});

test("Excel Tools no longer uses project quota", () => {
  assert.doesNotMatch(
    excel,
    /SecureAccountStore/
  );

  assert.doesNotMatch(
    excel,
    /StudioSecurityClient/
  );

  assert.doesNotMatch(
    excel,
    /consumeOtherAppProjectQuota/
  );

  assert.doesNotMatch(
    excel,
    /proje kotası/i
  );
});

test("verified Owner bypasses Pro status for Other Apps", () => {
  const otherStart =
    main.indexOf(
      "AppScreen.OTHER_APPS ->"
    );

  const excelStart =
    main.indexOf(
      "AppScreen.EXCEL_TOOLS ->"
    );

  const modeStart =
    main.indexOf(
      "AppScreen.MODE_SELECT ->"
    );

  assert.ok(
    otherStart >= 0
  );

  assert.ok(
    excelStart > otherStart
  );

  assert.ok(
    modeStart > excelStart
  );

  const otherRoute =
    main.slice(
      otherStart,
      excelStart
    );

  const excelRoute =
    main.slice(
      excelStart,
      modeStart
    );

  assert.match(
    otherRoute,
    /proUnlocked\s*=\s*terminalOwner\s*\|\|\s*proStatus\?\.active == true/
  );

  assert.match(
    excelRoute,
    /proUnlocked\s*=\s*terminalOwner\s*\|\|\s*proStatus\?\.active == true/
  );
});
