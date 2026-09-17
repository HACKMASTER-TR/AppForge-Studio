import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const root =
  "../../android-app/app/src/main/java";

const gate = await readFile(
  new URL(
    `${root}/com/appforge/studio/tools/OtherAppsUsageGate.kt`,
    import.meta.url
  ),
  "utf8"
);

const otherApps = await readFile(
  new URL(
    `${root}/com/appforge/studio/ui/OtherAppsScreen.kt`,
    import.meta.url
  ),
  "utf8"
);

const excel = await readFile(
  new URL(
    `${root}/com/appforge/studio/tools/excel/ExcelToolsScreen.kt`,
    import.meta.url
  ),
  "utf8"
);

const video = await readFile(
  new URL(
    `${root}/com/hackmaster/videoforge/VideoForgeActivity.kt`,
    import.meta.url
  ),
  "utf8"
);

test(
  "Free gets one use and PRO gets five uses per tool",
  () => {
    assert.match(
      gate,
      /FREE_LIMIT\s*=\s*\n?\s*1/
    );

    assert.match(
      gate,
      /PRO_LIMIT\s*=\s*\n?\s*5/
    );

    assert.match(
      gate,
      /EXCEL_TOOLS/
    );

    assert.match(
      gate,
      /VIDEO_FORGE/
    );

    assert.match(
      gate,
      /tool\.storageKey/
    );
  }
);

test(
  "Excel and VideoForge use independent counters",
  () => {
    assert.match(
      excel,
      /Tool\.EXCEL_TOOLS/
    );

    assert.match(
      video,
      /Tool\.VIDEO_FORGE/
    );

    assert.match(
      otherApps,
      /Tool\.EXCEL_TOOLS/
    );

    assert.match(
      otherApps,
      /Tool\.VIDEO_FORGE/
    );
  }
);

test(
  "PRO is no longer unlimited",
  () => {
    for (
      const source of [
        gate,
        otherApps,
        excel,
        video,
      ]
    ) {
      assert.doesNotMatch(
        source,
        /Sınırsız kullanım/
      );
    }
  }
);

test(
  "old shared five-use copy is removed",
  () => {
    for (
      const source of [
        otherApps,
        excel,
        video,
      ]
    ) {
      assert.doesNotMatch(
        source,
        /ortak 5|5 ücretsiz ortak|toplam 5 ücretsiz/i
      );
    }
  }
);
