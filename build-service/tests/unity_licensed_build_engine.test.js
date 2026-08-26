import test from "node:test";
import assert from "node:assert/strict";

import {
  unityOutputFormats,
  createUnityBuildScript
} from "../src/unityLicensedBuild.js";

test(
  "Unity output routing maps APK, AAB and BOTH",
  () => {
    assert.deepEqual(
      unityOutputFormats(
        "apk"
      ),
      [
        "apk"
      ]
    );

    assert.deepEqual(
      unityOutputFormats(
        "aab"
      ),
      [
        "aab"
      ]
    );

    assert.deepEqual(
      unityOutputFormats(
        "both"
      ),
      [
        "apk",
        "aab"
      ]
    );
  }
);

test(
  "Unity generated batchmode build script contains Android overrides without secrets",
  () => {
    const source =
      createUnityBuildScript(
        {
          appName:
            "AppForge Unity",
          packageName:
            "com.example.unityapp",
          versionCode:
            42,
          versionName:
            "4.2.0"
        }
      );

    assert.match(
      source,
      /NamedBuildTarget\.Android/
    );

    assert.match(
      source,
      /com\.example\.unityapp/
    );

    assert.match(
      source,
      /bundleVersionCode\s*=\s*42/
    );

    assert.match(
      source,
      /buildAppBundle/
    );

    assert.match(
      source,
      /useCustomKeystore\s*=\s*false/
    );

    assert.doesNotMatch(
      source,
      /storePassword|keyPassword|DATABASE_URL|S3_SECRET/
    );
  }
);
