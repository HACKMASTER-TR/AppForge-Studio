import test from "node:test";
import assert from "node:assert/strict";

function slugify(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 60);
}

test("team slug normalizes text", () => {
  assert.equal(slugify("My Team 123"), "my-team-123");
});

test("team slug strips punctuation", () => {
  assert.equal(slugify("  Test!! Team  "), "test-team");
});
