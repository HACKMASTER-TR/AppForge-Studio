import test from "node:test";
import assert from "node:assert/strict";
import {
  redactLogLine
} from "../src/buildLogs.js";

test("store password is redacted", () => {
  const line =
    redactLogLine(
      "APPFORGE_STORE_PASSWORD=secret"
    );

  assert.equal(
    line.includes("secret"),
    false
  );
});

test("key password is redacted", () => {
  const line =
    redactLogLine(
      "APPFORGE_KEY_PASSWORD=supersecret"
    );

  assert.equal(
    line.includes("supersecret"),
    false
  );
});
