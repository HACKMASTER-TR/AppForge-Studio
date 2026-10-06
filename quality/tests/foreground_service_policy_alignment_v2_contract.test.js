import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read =
  async path =>
    readFile(
      new URL(
        `../../${path}`,
        import.meta.url
      ),
      "utf8"
    );

const manifest =
  await read(
    "android-app/app/src/main/AndroidManifest.xml"
  );

const buildService =
  await read(
    "android-app/app/src/main/java/com/appforge/studio/BuildProgressService.kt"
  );

const videoService =
  await read(
    "android-app/app/src/main/java/com/hackmaster/videoforge/DubForegroundService.kt"
  );

test(
  "manifest declares exact build and VideoForge foreground-service capabilities",
  () => {
    assert.match(
      manifest,
      /android\.permission\.FOREGROUND_SERVICE_DATA_SYNC/
    );

    assert.match(
      manifest,
      /android\.permission\.FOREGROUND_SERVICE_MEDIA_PROCESSING/
    );

    assert.match(
      manifest,
      /android:name="\.BuildProgressService"[^>]*android:foregroundServiceType="dataSync"/s
    );

    assert.match(
      manifest,
      /android:name="com\.hackmaster\.videoforge\.DubForegroundService"[^>]*android:foregroundServiceType="dataSync\|mediaProcessing"/s
    );
  }
);

test(
  "AppForge build tracker explicitly runs as dataSync",
  () => {
    assert.match(
      buildService,
      /APPFORGE_FGS_POLICY_ALIGNMENT_V2/
    );

    assert.match(
      buildService,
      /ServiceCompat\.startForeground\([\s\S]*ServiceInfo\.FOREGROUND_SERVICE_TYPE_DATA_SYNC/
    );

    assert.doesNotMatch(
      buildService,
      /FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING/
    );
  }
);

test(
  "VideoForge routes model and URL acquisition to dataSync and media work to mediaProcessing",
  () => {
    assert.match(
      videoService,
      /MODE_MODELS,[\s\S]*MODE_URL_DUB[\s\S]*FOREGROUND_SERVICE_TYPE_DATA_SYNC/
    );

    assert.match(
      videoService,
      /MODE_DUB,[\s\S]*MODE_QUEUE[\s\S]*FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING/
    );

    assert.match(
      videoService,
      /activeForegroundType/
    );
  }
);

test(
  "URL VideoForge changes to mediaProcessing only after validated acquisition",
  () => {
    const download =
      videoService.indexOf(
        "UrlVideoImporter.downloadValidated"
      );

    const transition =
      videoService.indexOf(
        "switchForegroundType",
        download
      );

    const engine =
      videoService.indexOf(
        "OfflineDubEngine",
        transition
      );

    assert.ok(
      download >= 0
    );

    assert.ok(
      transition > download
    );

    assert.ok(
      engine > transition
    );

    assert.match(
      videoService.slice(
        transition,
        engine
      ),
      /FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING/
    );
  }
);

test(
  "Android 15 foreground-service timeout is fail-safe for both build and VideoForge",
  () => {
    assert.match(
      buildService,
      /override fun onTimeout\([\s\S]*stopSelf\([\s\S]*startId/
    );

    assert.match(
      videoService,
      /APPFORGE_FGS_TIMEOUT_V2/
    );

    assert.match(
      videoService,
      /override fun onTimeout\([\s\S]*STATE_CANCELLED[\s\S]*stopSelf\([\s\S]*startId/
    );

    assert.match(
      videoService,
      /VIDEOFORGE_TASK_REMOVAL_STOP_V1_5/
    );

    assert.match(
      videoService,
      /VIDEOFORGE_IMMEDIATE_FOREGROUND_V1/
    );
  }
);
