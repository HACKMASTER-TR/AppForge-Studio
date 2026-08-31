import test from "node:test";
import assert from "node:assert/strict";

function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.keys(value).sort().map(k => [k, stable(value[k])])
    );
  }
  return value;
}

test("stable canonicalization ignores key order", () => {
  const a = JSON.stringify(stable({ b: 2, a: 1 }));
  const b = JSON.stringify(stable({ a: 1, b: 2 }));
  assert.equal(a, b);
});
