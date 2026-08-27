import crypto from "crypto";
import { promises as fs } from "fs";
import { query } from "./db.js";
import { config } from "./config.js";
import {
  cacheGetJson,
  cacheSetJson
} from "./redis.js";

function stable(value) {
  if (Array.isArray(value)) {
    return value.map(stable);
  }

  if (
    value &&
    typeof value === "object" &&
    !(value instanceof Date)
  ) {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map(key => [
          key,
          stable(value[key])
        ])
    );
  }

  return value;
}

function sanitizedConfig(configObject) {
  const clone =
    JSON.parse(
      JSON.stringify(
        configObject || {}
      )
    );

  if (clone?.signing) {
    delete clone.signing.storePassword;
    delete clone.signing.keyPassword;
  }

  return clone;
}

async function fileSha256(file) {
  if (!file) return null;

  const hash =
    crypto.createHash("sha256");

  const handle =
    await fs.open(file, "r");

  try {
    const stream =
      handle.createReadStream();

    for await (const chunk of stream) {
      hash.update(chunk);
    }

    return hash.digest("hex");
  } finally {
    await handle.close();
  }
}

export async function computeCacheKey(
  configObject,
  {
    projectFile,
    projectIdentity = null,
    keystoreFile,
    iconFile,
    firebaseConfigFile
  } = {}
) {
  const inputHashes = {
    project:
      projectIdentity ||
      await fileSha256(projectFile),
    keystore:
      await fileSha256(keystoreFile),
    icon:
      await fileSha256(iconFile),
    firebase:
      await fileSha256(
        firebaseConfigFile
      )
  };

  const payload = {
    schema: 1,
    config:
      stable(
        sanitizedConfig(
          configObject
        )
      ),
    inputs:
      stable(inputHashes)
  };

  return crypto
    .createHash("sha256")
    .update(
      JSON.stringify(payload)
    )
    .digest("hex");
}

export async function findCache(
  cacheKey
) {
  if (!config.buildCacheEnabled) {
    return null;
  }

  const hot =
    await cacheGetJson(
      "build-cache",
      cacheKey
    );

  if (hot) {
    return hot;
  }

  const result =
    await query(
      `SELECT
         cache_key,
         source_build_id,
         outputs,
         metadata,
         expires_at
       FROM appforge_build_cache
       WHERE cache_key = $1
         AND expires_at > NOW()`,
      [cacheKey]
    );

  const row =
    result.rows[0] || null;

  if (row) {
    const ttlSeconds =
      Math.max(
        1,
        Math.floor(
          (
            new Date(row.expires_at)
              .getTime() -
            Date.now()
          ) / 1000
        )
      );

    await cacheSetJson(
      "build-cache",
      cacheKey,
      row,
      ttlSeconds
    );
  }

  return row;
}

export async function storeCache({
  cacheKey,
  sourceBuildId,
  outputs,
  metadata = {}
}) {
  if (
    !config.buildCacheEnabled ||
    !cacheKey
  ) {
    return;
  }

  await query(
    `INSERT INTO appforge_build_cache(
       cache_key,
       source_build_id,
       outputs,
       metadata,
       expires_at
     )
     VALUES(
       $1,$2,$3::jsonb,$4::jsonb,
       NOW() + ($5 || ' hours')::interval
     )
     ON CONFLICT(cache_key)
     DO UPDATE SET
       source_build_id = EXCLUDED.source_build_id,
       outputs = EXCLUDED.outputs,
       metadata = EXCLUDED.metadata,
       created_at = NOW(),
       expires_at = EXCLUDED.expires_at`,
    [
      cacheKey,
      sourceBuildId,
      JSON.stringify(outputs || {}),
      JSON.stringify(metadata || {}),
      String(config.buildCacheTtlHours)
    ]
  );

  const ttlSeconds =
    config.buildCacheTtlHours *
    60 * 60;

  await cacheSetJson(
    "build-cache",
    cacheKey,
    {
      cache_key: cacheKey,
      source_build_id:
        sourceBuildId,
      outputs:
        outputs || {},
      metadata:
        metadata || {},
      expires_at:
        new Date(
          Date.now() +
          ttlSeconds * 1000
        ).toISOString()
    },
    ttlSeconds
  );
}

export async function cleanupCache() {
  await query(
    `DELETE FROM appforge_build_cache
     WHERE expires_at <= NOW()`
  );
}
