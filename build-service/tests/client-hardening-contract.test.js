import assert from "node:assert/strict";
import test from "node:test";

import {
  __clientHardeningTest
} from "../src/clientHardening.js";

const {
  nativeClientVersion,
  policyForVersion
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
  assert.equal(policyForVersion(522).state, "NORMAL");
  assert.equal(policyForVersion(521).state, "OPTIONAL");
  assert.equal(policyForVersion(520).state, "FORCED");
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
