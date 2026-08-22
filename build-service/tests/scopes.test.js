import test from "node:test";
import assert from "node:assert/strict";

function scopeAllowed(scopes, required) {
  return scopes.includes("*") || scopes.includes(required);
}

test("build read scope allows reads", () => {
  assert.equal(
    scopeAllowed(["build:read"], "build:read"),
    true
  );
});

test("read-only token cannot write", () => {
  assert.equal(
    scopeAllowed(["build:read"], "build:write"),
    false
  );
});

test("wildcard scope allows operation", () => {
  assert.equal(
    scopeAllowed(["*"], "build:write"),
    true
  );
});
