import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const mainActivity = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  ),
  "utf8"
);

const home = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt",
    import.meta.url
  ),
  "utf8"
);

test("Android system back is owned by AppForge navigation", () => {
  assert.match(
    mainActivity,
    /screen !=[\s\S]*AppScreen\.ONBOARDING[\s\S]*!showExitConfirmation/
  );

  assert.match(
    mainActivity,
    /AppScreen\.EXCEL_TOOLS ->[\s\S]*AppScreen\.OTHER_APPS/
  );

  assert.match(
    mainActivity,
    /AppScreen\.OTHER_APPS,[\s\S]*AppScreen\.MODE_SELECT,[\s\S]*AppScreen\.QUICK,[\s\S]*AppScreen\.BUILDER,[\s\S]*AppScreen\.LIBRARY,[\s\S]*AppScreen\.HOME/
  );

  assert.match(
    mainActivity,
    /AppScreen\.TERMINAL ->[\s\S]*terminalReturnScreen/
  );
});

test("Home back requires an explicit exit confirmation", () => {
  assert.match(
    mainActivity,
    /AppScreen\.HOME ->[\s\S]*showExitConfirmation =[\s\S]*true/
  );

  assert.match(
    mainActivity,
    /Uygulamadan çıkmak istediğinize emin misiniz\?/
  );

  assert.match(
    mainActivity,
    /hostActivity[\s\S]*\?\.finish\(\)/
  );

  assert.match(
    mainActivity,
    /Text\([\s\S]*"Evet"[\s\S]*\)/
  );

  assert.match(
    mainActivity,
    /Text\([\s\S]*"Hayır"[\s\S]*\)/
  );
});

test("Successful Builds consumes system back before Home exit", () => {
  assert.match(
    home,
    /BackHandler\([\s\S]*enabled =[\s\S]*successfulApkFolderOpen\.value[\s\S]*successfulApkFolderOpen\.value =[\s\S]*false/
  );
});
