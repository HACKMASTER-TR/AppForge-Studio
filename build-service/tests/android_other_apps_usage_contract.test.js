import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const read = async path =>
  readFile(
    new URL(
      `../../${path}`,
      import.meta.url
    ),
    "utf8"
  );

const gate = await read(
  "android-app/app/src/main/java/com/appforge/studio/tools/OtherAppsUsageGate.kt"
);

const otherApps = await read(
  "android-app/app/src/main/java/com/appforge/studio/ui/OtherAppsScreen.kt"
);

const excel = await read(
  "android-app/app/src/main/java/com/appforge/studio/tools/excel/ExcelToolsScreen.kt"
);

const video = await read(
  "android-app/app/src/main/java/com/hackmaster/videoforge/VideoForgeActivity.kt"
);

const client = await read(
  "android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt"
);

const quota = await read(
  "build-service/src/projectQuotaV2.js"
);

const server = await read(
  "build-service/server.js"
);

const config = await read(
  "build-service/src/config.js"
);


test(
  "Free keeps one independent use per tool",
  () => {
    assert.match(
      gate,
      /FREE_LIMIT\s*=\s*\n?\s*1/
    );

    assert.match(
      gate,
      /EXCEL_TOOLS/
    );

    assert.match(
      gate,
      /VIDEO_FORGE/
    );

    assert.doesNotMatch(
      gate,
      /PRO_LIMIT/
    );
  }
);


test(
  "PRO Monthly project quota defaults to 50",
  () => {
    assert.match(
      config,
      /PRO_MONTHLY_PROJECT_LIMIT[\s\S]{0,120}50/
    );
  }
);


test(
  "PRO other-app usage is server authoritative",
  () => {
    assert.match(
      quota,
      /consumeOtherAppProjectQuota/
    );

    assert.match(
      quota,
      /appforge_pro_monthly_project_slots/
    );

    assert.match(
      quota,
      /usageId/
    );

    assert.match(
      server,
      /\/api\/projects\/quota\/other-app-use/
    );

    assert.match(
      client,
      /consumeOtherAppProjectQuota/
    );
  }
);


test(
  "Excel and VideoForge charge project quota when PRO",
  () => {
    assert.match(
      excel,
      /consumeOtherAppProjectQuota/
    );

    assert.match(
      excel,
      /excel_tools/
    );

    assert.match(
      video,
      /consumeOtherAppProjectQuota/
    );

    assert.match(
      video,
      /video_forge/
    );
  }
);


test(
  "PRO five-use copy is removed",
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
        /PRO kalan hak/
      );

      assert.doesNotMatch(
        source,
        /5 PRO kullanım/
      );

      assert.doesNotMatch(
        source,
        /PRO • Sınırsız kullanım/
      );
    }
  }
);


test(
  "VideoForge receives server URL without exporting activity",
  () => {
    assert.match(
      otherApps,
      /EXTRA_SERVER_URL/
    );

    assert.match(
      video,
      /SecureAccountStore/
    );

    assert.match(
      video,
      /EXTRA_SERVER_URL/
    );
  }
);
