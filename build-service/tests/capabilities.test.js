import test from "node:test";
import assert from "node:assert/strict";

function supports(worker, required) {
  return required.every(x => worker.includes(x));
}

test("worker supports exact capability set", () => {
  assert.equal(
    supports(
      ["android-api-37", "java-17", "gradle"],
      ["android-api-37", "gradle"]
    ),
    true
  );
});

test("worker rejects missing capability", () => {
  assert.equal(
    supports(
      ["android-api-37", "java-17"],
      ["android-api-37", "gradle"]
    ),
    false
  );
});
