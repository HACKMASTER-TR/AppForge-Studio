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
  "admin panel always returns to Studio home",
  () => {
    const routeHeader =
      "AppScreen.ADMIN_OPS -> AdminOpsScreen(";

    const routeStart =
      main.indexOf(routeHeader);

    assert.ok(routeStart >= 0);

    const routeTail =
      main.slice(
        routeStart + routeHeader.length
      );

    const nextRouteOffset =
      routeTail.search(
        /^\s*AppScreen\.[A-Z0-9_]+\s*->/m
      );

    assert.ok(nextRouteOffset >= 0);

    const routeEnd =
      routeStart +
      routeHeader.length +
      nextRouteOffset;

    const route =
      main.slice(routeStart, routeEnd);

    assert.match(
      route,
      /onBack\s*=\s*\{[\s\S]*?AppScreen\.HOME/
    );

    assert.doesNotMatch(
      route,
      /AppScreen\.BUILDER/
    );

    const backStart =
      main.indexOf("BackHandler(");

    const backEnd =
      main.indexOf(
        "var serverUrl",
        backStart
      );

    const back =
      main.slice(backStart, backEnd);

    assert.match(
      back,
      /AppScreen\.ADMIN_OPS/
    );

    assert.match(
      back,
      /AppScreen\.ADMIN_OPS\s*->[\s\S]*?AppScreen\.HOME/
    );

    assert.match(
      main,
      /onOpenAdmin\s*=\s*\{[\s\S]*?screen\s*=[\s\S]*?AppScreen\.ADMIN_OPS/
    );
  }
);
