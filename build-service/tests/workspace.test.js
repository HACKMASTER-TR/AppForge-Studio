import test from "node:test";
import assert from "node:assert/strict";
import {
  normalizeProjectPath,
  lineDiff
} from "../src/workspace.js";

test("project path allows nested relative path", () => {
  assert.equal(
    normalizeProjectPath("pages/about.html"),
    "pages/about.html"
  );
});

test("project path blocks traversal", () => {
  assert.throws(
    () =>
      normalizeProjectPath("../secret.txt")
  );
});

test("project path blocks absolute path", () => {
  assert.throws(
    () =>
      normalizeProjectPath("/etc/passwd")
  );
});

test("line diff detects additions and removals", () => {
  const diff =
    lineDiff(
      "a\nb\nc",
      "a\nx\nc"
    );

  assert.ok(
    diff.some(
      row =>
        row.type === "remove" &&
        row.text === "b"
    )
  );

  assert.ok(
    diff.some(
      row =>
        row.type === "add" &&
        row.text === "x"
    )
  );
});
