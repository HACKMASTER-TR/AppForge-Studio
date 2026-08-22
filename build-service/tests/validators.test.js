import test from "node:test";
import assert from "node:assert/strict";
import {
  isValidPackageName,
  isHttpsUrl,
  validateBuildConfig
} from "../src/validators.js";

test("valid package names", () => {
  assert.equal(isValidPackageName("com.example.app"), true);
  assert.equal(isValidPackageName("com.example.app_2"), true);
  assert.equal(isValidPackageName("bad"), false);
  assert.equal(isValidPackageName("1com.example"), false);
});

test("https validation", () => {
  assert.equal(isHttpsUrl("https://example.com"), true);
  assert.equal(isHttpsUrl("HTTP://example.com"), false);
  assert.equal(isHttpsUrl("http://example.com"), false);
});

test("local build requires project zip", () => {
  const result = validateBuildConfig({
    appName: "Test",
    packageName: "com.example.test",
    versionCode: 1,
    versionName: "1.0.0",
    sourceMode: "LOCAL",
    signing: { mode: "DEBUG" },
    firebase: {}
  }, {
    hasProject: false,
    hasKeystore: false,
    hasFirebaseConfig: false
  });

  assert.ok(
    result.errors.some(x => x.includes("ZIP"))
  );
});

test("firebase enabled requires config", () => {
  const result = validateBuildConfig({
    appName: "Test",
    packageName: "com.example.test",
    versionCode: 1,
    versionName: "1.0.0",
    sourceMode: "URL",
    webUrl: "https://example.com",
    signing: { mode: "DEBUG" },
    firebase: { analytics: true }
  }, {
    hasProject: false,
    hasKeystore: false,
    hasFirebaseConfig: false
  });

  assert.ok(
    result.errors.some(x => x.includes("google-services.json"))
  );
});
