import test from "node:test";
import assert from "node:assert/strict";

import {
  computeCacheKey,
  cacheKeyDescriptor,
  cacheLookupKeys,
  cacheSupportsOutput,
  outputsForRequest
} from "../src/buildCache.js";

const baseConfig = {
  appName: "Cache Test",
  packageName: "com.appforge.cachetest",
  versionCode: 1,
  versionName: "1.0.0",
  sourceMode: "LOCAL",
  signing: {
    mode: "DEBUG"
  },
  firebase: {
    analytics: true,
    crashlytics: true,
    messaging: true
  }
};

test(
  "non-Android outputs do not collide with Android cache identities",
  async () => {
    const outputs = [
      "apk",
      "aab",
      "both",
      "exe"
    ];

    const keys =
      await Promise.all(
        outputs.map(
          buildOutput =>
            computeCacheKey(
              {
                ...baseConfig,
                buildOutput
              },
              {
                projectIdentity:
                  "same-project"
              }
            )
        )
      );

    assert.equal(
      new Set(keys).size,
      outputs.length
    );
    assert.equal(
      cacheKeyDescriptor(
        keys.at(-1)
      ).output,
      "exe"
    );
    assert.deepEqual(
      cacheLookupKeys(
        keys.at(-1)
      ),
      [keys.at(-1)]
    );
  }
);

test(
  "APK AAB BOTH share build identity but keep request output suffix",
  async () => {
    const apk =
      await computeCacheKey(
        {
          ...baseConfig,
          buildOutput: "apk"
        },
        {
          projectIdentity:
            "same-project"
        }
      );

    const aab =
      await computeCacheKey(
        {
          ...baseConfig,
          buildOutput: "aab"
        },
        {
          projectIdentity:
            "same-project"
        }
      );

    const both =
      await computeCacheKey(
        {
          ...baseConfig,
          buildOutput: "both"
        },
        {
          projectIdentity:
            "same-project"
        }
      );

    const apkInfo =
      cacheKeyDescriptor(apk);

    const aabInfo =
      cacheKeyDescriptor(aab);

    const bothInfo =
      cacheKeyDescriptor(both);

    assert.ok(apkInfo);
    assert.ok(aabInfo);
    assert.ok(bothInfo);

    assert.equal(
      apkInfo.identity,
      aabInfo.identity
    );

    assert.equal(
      apkInfo.identity,
      bothInfo.identity
    );

    assert.equal(
      apkInfo.output,
      "apk"
    );

    assert.equal(
      aabInfo.output,
      "aab"
    );

    assert.equal(
      bothInfo.output,
      "both"
    );

    assert.notEqual(apk, aab);
    assert.notEqual(apk, both);
  }
);

test(
  "APK and AAB requests can fall back to BOTH cache",
  async () => {
    const apk =
      await computeCacheKey(
        {
          ...baseConfig,
          buildOutput: "apk"
        },
        {
          projectIdentity:
            "same-project"
        }
      );

    const identity =
      cacheKeyDescriptor(
        apk
      ).identity;

    assert.deepEqual(
      cacheLookupKeys(apk),
      [
        apk,
        `${identity}:both`
      ]
    );
  }
);

test(
  "cache hit requires requested artifact reference",
  () => {
    const apkRef = {
      driver: "s3",
      key: "build/app-release.apk"
    };

    const aabRef = {
      driver: "s3",
      key: "build/app-release.aab"
    };

    assert.equal(
      cacheSupportsOutput(
        { apk: apkRef },
        "apk"
      ),
      true
    );

    assert.equal(
      cacheSupportsOutput(
        { apk: apkRef },
        "aab"
      ),
      false
    );

    assert.equal(
      cacheSupportsOutput(
        {
          apk: apkRef,
          aab: aabRef
        },
        "both"
      ),
      true
    );

    assert.equal(
      cacheSupportsOutput(
        {
          apk: apkRef
        },
        "both"
      ),
      false
    );
  }
);

test(
  "single-artifact fallback does not expose the other BOTH artifact",
  () => {
    const outputs = {
      apk: {
        driver: "s3",
        key: "build/app-release.apk"
      },
      aab: {
        driver: "s3",
        key: "build/app-release.aab"
      }
    };

    assert.deepEqual(
      outputsForRequest(
        outputs,
        "apk"
      ),
      {
        apk: outputs.apk
      }
    );
    assert.deepEqual(
      outputsForRequest(
        outputs,
        "aab"
      ),
      {
        aab: outputs.aab
      }
    );
  }
);
