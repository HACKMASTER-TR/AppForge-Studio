import test from "node:test";
import assert from "node:assert/strict";

function validArtifact(ref) {
  return Boolean(
    ref &&
    typeof ref.sha256 === "string" &&
    /^[a-f0-9]{64}$/.test(ref.sha256) &&
    Number(ref.sizeBytes) > 0
  );
}

test("artifact metadata accepts sha256 + size", () => {
  assert.equal(
    validArtifact({
      sha256:
        "a".repeat(64),
      sizeBytes: 123
    }),
    true
  );
});

test("artifact metadata rejects missing size", () => {
  assert.equal(
    validArtifact({
      sha256:
        "a".repeat(64),
      sizeBytes: 0
    }),
    false
  );
});
