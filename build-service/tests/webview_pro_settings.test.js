import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";

test(
  "WebView Pro settings propagate through Studio and Android builds",
  async () => {
    const [
      draft,
      mainActivity,
      apiClient,
      projectLibrary,
      backupManager,
      buildEngine,
      fastBuild,
      fastRuntime
    ] = await Promise.all([
      fs.readFile(
        new URL(
          "../../android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt",
          import.meta.url
        ),
        "utf8"
      ),
      fs.readFile(
        new URL(
          "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
          import.meta.url
        ),
        "utf8"
      ),
      fs.readFile(
        new URL(
          "../../android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt",
          import.meta.url
        ),
        "utf8"
      ),
      fs.readFile(
        new URL(
          "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt",
          import.meta.url
        ),
        "utf8"
      ),
      fs.readFile(
        new URL(
          "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectBackupManager.kt",
          import.meta.url
        ),
        "utf8"
      ),
      fs.readFile(
        new URL(
          "../src/buildEngine.js",
          import.meta.url
        ),
        "utf8"
      ),
      fs.readFile(
        new URL(
          "../src/fastBuild.js",
          import.meta.url
        ),
        "utf8"
      ),
      fs.readFile(
        new URL(
          "../fast-runtime/FastActivity.java",
          import.meta.url
        ),
        "utf8"
      )
    ]);

    for (const field of [
      "webJavaScriptEnabled",
      "webDomStorageEnabled",
      "webZoomEnabled",
      "webWideViewPortEnabled",
      "webOverviewModeEnabled",
      "webMediaAutoplayEnabled",
      "webMixedContentAllowed"
    ]) {
      assert.ok(
        draft.includes(field),
        `ProjectDraft missing ${field}`
      );

      assert.ok(
        mainActivity.includes(field),
        `Studio UI missing ${field}`
      );

      assert.ok(
        projectLibrary.includes(field),
        `ProjectLibrary missing ${field}`
      );

      assert.ok(
        backupManager.includes(field),
        `Backup manager missing ${field}`
      );
    }

    assert.ok(
      apiClient.includes(
        'put("webView", JSONObject().apply'
      )
    );

    assert.ok(
      buildEngine.includes(
        "mediaPlaybackRequiresUserGesture"
      )
    );

    assert.ok(
      buildEngine.includes(
        "MIXED_CONTENT_COMPATIBILITY_MODE"
      )
    );

    assert.ok(
      buildEngine.includes(
        "useWideViewPort"
      )
    );

    assert.ok(
      fastBuild.includes(
        "webMixedContentAllowed"
      )
    );

    assert.ok(
      fastRuntime.includes(
        "setMediaPlaybackRequiresUserGesture"
      )
    );

    assert.ok(
      fastRuntime.includes(
        "setMixedContentMode"
      )
    );

    assert.ok(
      fastRuntime.includes(
        "setUseWideViewPort"
      )
    );

    assert.ok(
      fastRuntime.includes(
        "applyZoomPolicy"
      )
    );

    assert.ok(
      fastRuntime.includes(
        "user-scalable=no"
      )
    );

    assert.ok(
      buildEngine.includes(
        "zoomLockOnPageFinished"
      )
    );

    assert.ok(
      buildEngine.includes(
        "user-scalable=no"
      )
    );

    const buildFlow =
      mainActivity.slice(
        mainActivity.indexOf(
          "val startBuildWithDraft"
        ),
        mainActivity.indexOf(
          "LaunchedEffect(\n        conversionApkUri"
        )
      );

    assert.equal(
      /\bstep\s*=\s*9\b/.test(
        buildFlow
      ),
      false,
      "Build flow must not return to step 9"
    );

    assert.equal(
      (
        buildFlow.match(
          /\bstep\s*=\s*10\b/g
        ) || []
      ).length,
      3,
      "Build flow must route all states to step 10"
    );
  }
);
