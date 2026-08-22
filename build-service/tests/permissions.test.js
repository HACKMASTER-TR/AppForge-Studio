import test from "node:test";
import assert from "node:assert/strict";
import {
  effectivePermissions,
  PERMISSIONS
} from "../src/permissions.js";

test("owner gets every permission", () => {
  const p = effectivePermissions("owner", {});
  for (const key of PERMISSIONS) {
    assert.equal(p[key], true);
  }
});

test("viewer cannot create build by default", () => {
  const p = effectivePermissions("viewer", {});
  assert.equal(p["build.create"], false);
  assert.equal(p["build.read"], true);
});

test("override can enable one viewer permission", () => {
  const p = effectivePermissions(
    "viewer",
    { "build.create": true }
  );
  assert.equal(p["build.create"], true);
});
