import assert from "node:assert/strict";
import test from "node:test";

import {
  __clientHardeningTest
} from "../src/clientHardening.js";

const {
  nativeClientVersion,
  policyForVersion,
  shouldForceNativeUpdate
} = __clientHardeningTest;

function request(headers = {}) {
  const normalized = Object.fromEntries(
    Object.entries(headers).map(([key, value]) => [key.toLowerCase(), value])
  );

  return {
    get(name) {
      return normalized[String(name).toLowerCase()] || "";
    }
  };
}

test("Android update policy distinguishes normal optional and forced", () => {
  const release = {
    latestVersionCode: 525,
    minSupportedVersionCode: 521
  };

  assert.equal(policyForVersion(525, release).state, "NORMAL");
  assert.equal(policyForVersion(524, release).state, "OPTIONAL");
  assert.equal(policyForVersion(520, release).state, "FORCED");
});

test("invalid or absent rollout boundaries cannot create a forced lockout", () => {
  const policy = policyForVersion(
    1,
    {
      latestVersionCode: 0,
      minSupportedVersionCode: 0
    }
  );

  assert.equal(policy.state, "NORMAL");
  assert.equal(policy.latestVersionCode, 1);
  assert.equal(policy.minSupportedVersionCode, 1);
});

test("native client version prefers explicit version header", () => {
  assert.deepEqual(
    nativeClientVersion(
      request({
        "X-AppForge-Version-Code": "522",
        "User-Agent": "Dalvik/2.1.0"
      })
    ),
    {
      native: true,
      versionCode: 522,
      source: "header"
    }
  );
});

test("legacy Dalvik client is recognized without trusting a version", () => {
  assert.deepEqual(
    nativeClientVersion(
      request({
        "User-Agent": "Dalvik/2.1.0 (Linux; Android 16)"
      })
    ),
    {
      native: true,
      versionCode: null,
      source: "legacy_android"
    }
  );
});


test("legacy Dalvik grace prevents false forced update until the Play floor advances", () => {
  const legacy =
    nativeClientVersion(
      request({
        "User-Agent":
          "Dalvik/2.1.0 (Linux; Android 16)"
      })
    );

  assert.equal(
    shouldForceNativeUpdate(
      legacy,
      {
        minSupportedVersionCode: 517,
        legacyGraceVersionCode: 517
      }
    ),
    false
  );

  assert.equal(
    shouldForceNativeUpdate(
      legacy,
      {
        minSupportedVersionCode: 518,
        legacyGraceVersionCode: 517
      }
    ),
    true
  );
});

test("explicit Android version continues to obey the minimum version floor", () => {
  const oldClient =
    nativeClientVersion(
      request({
        "X-AppForge-Version-Code": "516",
        "User-Agent":
          "Dalvik/2.1.0"
      })
    );

  assert.equal(
    shouldForceNativeUpdate(
      oldClient,
      {
        minSupportedVersionCode: 517,
        legacyGraceVersionCode: 517
      }
    ),
    true
  );

  const currentClient =
    nativeClientVersion(
      request({
        "X-AppForge-Version-Code": "517",
        "User-Agent":
          "Dalvik/2.1.0"
      })
    );

  assert.equal(
    shouldForceNativeUpdate(
      currentClient,
      {
        minSupportedVersionCode: 517,
        legacyGraceVersionCode: 517
      }
    ),
    false
  );
});
