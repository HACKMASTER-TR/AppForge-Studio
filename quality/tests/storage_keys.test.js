import test from "node:test";
import assert from "node:assert/strict";

function inputKey(buildId, name) {
  return `inputs/${buildId}/${name}`;
}

function outputKey(buildId, name) {
  return `${buildId}/${name}`;
}

test("input object key is build scoped", () => {
  assert.equal(
    inputKey("abc", "project.zip"),
    "inputs/abc/project.zip"
  );
});

test("output object key is build scoped", () => {
  assert.equal(
    outputKey("abc", "app-release.aab"),
    "abc/app-release.aab"
  );
});
