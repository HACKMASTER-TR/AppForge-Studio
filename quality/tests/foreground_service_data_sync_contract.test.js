import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const manifest =
  await readFile(
    new URL(
      "../../android-app/app/src/main/AndroidManifest.xml",
      import.meta.url
    ),
    "utf8"
  );

const buildService =
  await readFile(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/BuildProgressService.kt",
      import.meta.url
    ),
    "utf8"
  );

test(
  "AppForge retains only the active build dataSync foreground-service declaration",
  () => {
    assert.match(
      manifest,
      /android\.permission\.FOREGROUND_SERVICE_DATA_SYNC/
    );

    assert.match(
      manifest,
      /android:name="\.BuildProgressService"[^>]*android:foregroundServiceType="dataSync"/s
    );

    assert.doesNotMatch(
      manifest,
      /FOREGROUND_SERVICE_MEDIA_PROCESSING/
    );

    assert.doesNotMatch(
      manifest,
      /mediaProcessing/
    );

    assert.doesNotMatch(
      manifest,
      /VideoForge|videoforge/
    );
  }
);

test(
  "build foreground service uses explicit dataSync runtime type and timeout shutdown",
  () => {
    assert.match(
      buildService,
      /ServiceInfo\.FOREGROUND_SERVICE_TYPE_DATA_SYNC/
    );

    assert.match(
      buildService,
      /override fun onTimeout\([\s\S]*stopSelf\([\s\S]*startId/
    );

    assert.doesNotMatch(
      buildService,
      /FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING/
    );
  }
);
