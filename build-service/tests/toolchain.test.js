import test from "node:test";
import assert from "node:assert/strict";
import {
  compareVersions
} from "../src/toolchain.js";

test("same version compares equal", () => {
  assert.equal(
    compareVersions(
      "9.3.1",
      "9.3.1"
    ),
    0
  );
});

test("newer gradle passes minimum", () => {
  assert.equal(
    compareVersions(
      "9.5.1",
      "9.3.1"
    ) > 0,
    true
  );
});

test("older gradle fails minimum", () => {
  assert.equal(
    compareVersions(
      "9.2.1",
      "9.3.1"
    ) < 0,
    true
  );
});
