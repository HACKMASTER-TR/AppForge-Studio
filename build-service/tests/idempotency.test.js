import test from "node:test";
import assert from "node:assert/strict";
import {
  normalizeIdempotencyKey,
  requestFingerprint
} from "../src/idempotency.js";

test("valid idempotency key", () => {
  assert.equal(
    normalizeIdempotencyKey(
      "build-12345678"
    ),
    "build-12345678"
  );
});

test("short idempotency key rejected", () => {
  assert.throws(
    () =>
      normalizeIdempotencyKey(
        "short"
      )
  );
});

test("fingerprint is stable for identical text", () => {
  assert.equal(
    requestFingerprint("abc"),
    requestFingerprint("abc")
  );
});
