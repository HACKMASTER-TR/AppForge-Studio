import test from "node:test";
import assert from "node:assert/strict";

function acceptablePath(name) {
  const normalized = String(name || "").replaceAll("\\", "/");
  const depth = normalized.split("/").filter(Boolean).length;
  return normalized.length <= 240 && depth <= 20 && !normalized.split("/").includes("..");
}

test("normal zip path is accepted", () => {
  assert.equal(
    acceptablePath("assets/js/app.js"),
    true
  );
});

test("deep zip path is rejected", () => {
  const path = Array.from(
    { length: 21 },
    (_, i) => `d${i}`
  ).join("/") + "/x.txt";

  assert.equal(
    acceptablePath(path),
    false
  );
});

test("traversal segment is rejected", () => {
  assert.equal(
    acceptablePath("../secret.txt"),
    false
  );
});
