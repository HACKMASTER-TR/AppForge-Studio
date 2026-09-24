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

const tokens =
  read(
    "android-app/app/src/main/java/com/appforge/studio/AppForgeUiTokens.kt"
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
      /PROJE ÜRETİMİ/
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
        "AI ile Oluştur",
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

    assert.doesNotMatch(
      home,
      /OwnerAdminCard\(/
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

test(
  "dashboard avoids invalid scoped weight import",
  () => {
    assert.doesNotMatch(
      dashboard,
      /import androidx\.compose\.foundation\.layout\.weight/
    );

    assert.match(
      dashboard,
      /Modifier\.weight\(1f\)/
    );
  }
);

test(
  "shared AppForge UI V2 theme is preserved",
  () => {
    assert.match(
      tokens,
      /internal fun AppForgeTheme/
    );

    assert.match(
      tokens,
      /0xFF050B18/
    );

    assert.match(
      tokens,
      /0xFF43D7FF/
    );

    assert.match(
      tokens,
      /0xFF7A5CFF/
    );

    assert.doesNotMatch(
      dashboard,
      /DEVICE BUILD V3/
    );
  }
);
