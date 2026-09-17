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

test("AppForge records real screen history for system back", () => {
  assert.match(
    mainActivity,
    /APP_SCREEN_HISTORY_V1/
  );

  assert.match(
    mainActivity,
    /appScreenBackStack/
  );

  assert.match(
    mainActivity,
    /lastObservedAppScreen/
  );

  assert.match(
    mainActivity,
    /screen ==[\s\S]*AppScreen\.HOME[\s\S]*emptyList\(\)/
  );

  assert.match(
    mainActivity,
    /returningToTop[\s\S]*dropLast\(1\)/
  );
});

test("Home system back only opens explicit exit confirmation", () => {
  assert.match(
    mainActivity,
    /screen ==[\s\S]*AppScreen\.HOME[\s\S]*!showExitConfirmation/
  );

  assert.match(
    mainActivity,
    /Uygulamadan çıkmak istediğinize emin misiniz\?/
  );

  assert.match(
    mainActivity,
    /hostActivity[\s\S]*\?\.finish\(\)/
  );
});

test("normal AppForge routes use the late central back handler", () => {
  assert.match(
    mainActivity,
    /LATE_APP_ROUTE_BACK_HANDLER_V1/
  );

  assert.match(
    mainActivity,
    /visibleScreen !=[\s\S]*AppScreen\.HOME[\s\S]*visibleScreen !=[\s\S]*AppScreen\.TERMINAL/
  );

  assert.match(
    mainActivity,
    /BackHandler \{[\s\S]*navigateAppSystemBack\(\)/
  );
});

test("Builder step is restored when back returns to Builder", () => {
  assert.match(
    mainActivity,
    /previous\.first ==[\s\S]*AppScreen\.BUILDER[\s\S]*step =[\s\S]*previous\.second/
  );
});

test("Successful Builds keeps its nested Home back behavior", () => {
  assert.match(
    home,
    /BackHandler\([\s\S]*successfulApkFolderOpen\.value[\s\S]*successfulApkFolderOpen\.value =[\s\S]*false/
  );
});
