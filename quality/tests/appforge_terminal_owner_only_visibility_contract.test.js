import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = async (path) =>
  readFile(
    new URL(
      `../../${path}`,
      import.meta.url
    ),
    "utf8"
  );

test(
  "public Home hides Terminal and Admin; Settings retains server-verified Admin route",
  async () => {
    const home = await read(
      "android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
    );
    const settings = await read(
      "android-app/app/src/main/java/com/appforge/studio/AppForgeSettingsScreens.kt"
    );
    const main = await read(
      "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
    );
    const admin = await read(
      "android-app/app/src/main/java/com/appforge/studio/AdminOpsScreen.kt"
    );

    assert.doesNotMatch(home, /OwnerAdminCard\s*\(/);
    assert.doesNotMatch(home, /onClick = onOpenTerminal/);
    assert.doesNotMatch(home, /TextButton\(onClick = onOpenAdmin\)/);
    assert.doesNotMatch(home, /YÖNETİCİ GİRİŞİ/);
    assert.match(settings, /versionTapCount\s*>=\s*7/);
    assert.match(settings, /onOpenAdmin\(\)/);
    assert.match(main, /AppScreen\.ADMIN_OPS\s*->\s*AdminOpsScreen/);
    assert.match(admin, /adminApi\.systemStatus\(token\)/);
  }
);

test(
  "terminal owner state is declared before Terminal entry uses it",
  async () => {
    const main = await read(
      "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
    );

    const declaration =
      main.indexOf(
        "val terminalOwner ="
      );

    const terminalEntry =
      main.indexOf(
        "onOpenTerminal = {"
      );

    assert.ok(
      declaration >= 0,
      "terminalOwner declaration must exist"
    );

    assert.ok(
      terminalEntry >= 0,
      "Terminal entry callback must exist"
    );

    assert.ok(
      declaration < terminalEntry,
      "terminalOwner must be declared before Terminal entry uses it"
    );
  }
);

test(
  "Terminal route rejects non-owner accounts",
  async () => {
    const main = await read(
      "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
    );

    /*
     * Terminal has one authoritative owner state and derives it
     * directly from the centralized OwnerAccessPolicy.
     */
    const terminalOwnerDeclarations =
      main.match(
        /val terminalOwner\s*=/g
      ) ?? [];

    assert.equal(
      terminalOwnerDeclarations.length,
      1,
      "terminalOwner must have exactly one declaration"
    );

    assert.match(
      main,
      /val terminalOwner\s*=\s*[\s\S]{0,150}?OwnerAccessPolicy\.isActiveOwner\(\s*context\s*\)/
    );

    /*
     * AdminOps independently uses the same centralized policy.
     */
    assert.match(
      main,
      /val isAdminOpsAccount\s*=\s*terminalOwner\b/
    );

    /*
     * User-facing Terminal entry rejects non-owner accounts.
     */
    assert.match(
      main,
      /onOpenTerminal\s*=\s*\{[\s\S]{0,1500}?if\s*\(terminalOwner\)[\s\S]{0,1500}?openWorkspaceScreen\(\s*AppScreen\.TERMINAL\s*\)[\s\S]{0,900}?else\s*\{[\s\S]{0,600}?AppScreen\.HOME/
    );

    /*
     * Defense in depth:
     * even restored/internal Terminal navigation is forced HOME
     * for a non-owner.
     */
    assert.match(
      main,
      /val visibleScreen[\s\S]{0,1000}?AppScreen\.TERMINAL[\s\S]{0,350}?!terminalOwner[\s\S]{0,600}?AppScreen\.HOME/
    );

    /*
     * Retired Railway authorization routes must stay absent.
     */
    assert.doesNotMatch(
      main,
      /externalAuthorizationUri|externalAuthorizationSequence|consumeExternalAuthorization|\/railway/i
    );
  }
);

test(
  "BUG-7 has no remaining active runtime blocker after device PASS",
  async () => {
    const blockers =
      JSON.parse(
        await read(
          ".appforge/runtime-blockers.json"
        )
      );

    assert.ok(
      Array.isArray(blockers.active)
    );

    assert.equal(
      blockers.active.some(
        (item) =>
          item?.id === "BUG-7"
      ),
      false
    );
  }
);
