import crypto from "crypto";
import { query, tx } from "./db.js";
import { config } from "./config.js";

export function normalizeIdempotencyKey(value) {
  const key =
    String(value || "")
      .trim();

  if (!key) return null;

  if (
    key.length < 8 ||
    key.length > 200
  ) {
    throw new Error(
      "Idempotency-Key 8–200 karakter olmalı."
    );
  }

  if (
    !/^[A-Za-z0-9._:-]+$/.test(key)
  ) {
    throw new Error(
      "Idempotency-Key geçersiz karakter içeriyor."
    );
  }

  return key;
}

export function requestFingerprint(value) {
  return crypto
    .createHash("sha256")
    .update(
      typeof value === "string"
        ? value
        : JSON.stringify(value)
    )
    .digest("hex");
}

export async function resolveIdempotency(
  userId,
  key,
  requestHash
) {
  if (!key) return null;

  const result =
    await query(
      `SELECT
         request_hash,
         build_id,
         expires_at
       FROM appforge_idempotency_keys
       WHERE user_id = $1
         AND idempotency_key = $2
         AND expires_at > NOW()`,
      [
        userId,
        key
      ]
    );

  const row =
    result.rows[0];

  if (!row) return null;

  if (
    row.request_hash !==
    requestHash
  ) {
    const error =
      new Error(
        "Aynı Idempotency-Key farklı bir build isteğiyle tekrar kullanıldı."
      );

    error.statusCode =
      409;

    throw error;
  }

  return {
    buildId:
      row.build_id
  };
}

export async function rememberIdempotency(
  userId,
  key,
  requestHash,
  buildId
) {
  if (!key) return;

  await query(
    `INSERT INTO appforge_idempotency_keys(
       user_id,
       idempotency_key,
       request_hash,
       build_id,
       expires_at
     )
     VALUES(
       $1,$2,$3,$4,
       NOW() + ($5 || ' hours')::interval
     )
     ON CONFLICT(user_id,idempotency_key)
     DO NOTHING`,
    [
      userId,
      key,
      requestHash,
      buildId,
      String(
        config.idempotencyTtlHours
      )
    ]
  );
}

export async function cleanupIdempotency() {
  await query(
    `DELETE FROM appforge_idempotency_keys
     WHERE expires_at <= NOW()`
  );
}
