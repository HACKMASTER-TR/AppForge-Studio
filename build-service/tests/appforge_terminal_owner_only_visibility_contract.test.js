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
  "Terminal home entry is owner-only",
  async () => {
    const home = await read(
      "android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
    );

    assert.match(
      home,
      /OwnerAccessPolicy\s*\.\s*isActiveOwner\(\s*context,\s*accountEmail\s*\)/
    );

    assert.doesNotMatch(
      home,
      /28550040284a@gmail\.com/
    );

    const card =
      home.indexOf(
        "onClick = onOpenTerminal"
      );

    assert.ok(
      card >= 0,
      "Terminal card source must still exist for owner"
    );

    const guard =
      home.lastIndexOf(
        "if (fullAdmin)",
        card
      );

    assert.ok(
      guard >= 0 &&
        card - guard < 2500,
      "Terminal card must be inside owner-only guard"
    );
  }
);

test(
  "Terminal route rejects non-owner accounts",
  async () => {
    const main = await read(
      "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
    );

    assert.match(
      main,
      /val terminalOwner\s*=\s*OwnerAccessPolicy[\s\S]{0,240}?isActiveOwner/
    );

    assert.match(
      main,
      /target == AppScreen\.TERMINAL[\s\S]{0,180}?!terminalOwner/
    );

    assert.match(
      main,
      /externalAuthorizationUri[\s\S]{0,900}?if \(terminalOwner\)[\s\S]{0,900}?consumeExternalAuthorization/
    );

    assert.match(
      main,
      /val visibleScreen[\s\S]{0,500}?AppScreen\.TERMINAL[\s\S]{0,500}?!terminalOwner[\s\S]{0,500}?AppScreen\.HOME/
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
