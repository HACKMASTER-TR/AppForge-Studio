import test from "node:test";
import assert from "node:assert/strict";

function clampPriority(value) {
  return Math.max(
    1,
    Math.min(
      1000,
      Number(value || 100)
    )
  );
}

test("priority lower bound", () => {
  assert.equal(
    clampPriority(-5),
    1
  );
});

test("priority upper bound", () => {
  assert.equal(
    clampPriority(5000),
    1000
  );
});

test("priority normal value", () => {
  assert.equal(
    clampPriority(50),
    50
  );
});
