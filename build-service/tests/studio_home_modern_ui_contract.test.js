import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const root =
  new URL(
    "../../",
    import.meta.url
  );

const read = path =>
  fs.readFileSync(
    new URL(
      path,
      root
    ),
    "utf8"
  );

const home =
  read(
    "android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
  );

const dashboard =
  read(
    "android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeDashboard.kt"
  );

const source =
  `${home}\n${dashboard}`;

test(
  "home coordinator stays compact",
  () => {
    assert.ok(
      home.split("\n").length < 280,
      "StudioHomeV2 coordinator must stay below 280 lines"
    );
  }
);

test(
  "modern dashboard hierarchy is preserved",
  () => {
    assert.match(
      source,
      /ModernHomeHero/
    );

    assert.match(
      source,
      /Brush\.linearGradient/
    );

    assert.match(
      source,
      /DEVICE BUILD V3/
    );

    assert.match(
      home,
      /Hızlı erişim/
    );

    assert.match(
      home,
      /Projelerin/
    );

    assert.match(
      home,
      /Proje araçları/
    );
  }
);

test(
  "home surfaces production actions",
  () => {
    for (
      const label of [
        "AppForge AI",
        "Unified Agent",
        "Derlemeler",
        "Dönüştür",
        "İçe aktar",
        "Görevler",
        "Şablonlar",
        "Geçmiş"
      ]
    ) {
      assert.equal(
        home.includes(label),
        true,
        `Missing home action: ${label}`
      );
    }
  }
);

test(
  "terminal remains owner-only and directly wired",
  () => {
    assert.match(
      home,
      /OwnerAccessPolicy\s*\.\s*isActiveOwner/
    );

    assert.match(
      home,
      /if \(fullAdmin\)[\s\S]{0,1800}?onClick = onOpenTerminal/
    );

    assert.doesNotMatch(
      home,
      /28550040284a@gmail\.com/
    );
  }
);

test(
  "retired five-build tool does not return",
  () => {
    assert.doesNotMatch(
      source,
      /5 Build Testi/
    );

    assert.doesNotMatch(
      source,
      /fiveParallel/
    );
  }
);
