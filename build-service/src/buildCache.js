import crypto from "crypto";
import { promises as fs } from "fs";
import { query } from "./db.js";
import { config } from "./config.js";
import {
  outputExists
} from "./storage.js";
import {
  cacheGetJson,
  cacheSetJson
} from "./redis.js";

const BUILD_OUTPUTS =
  new Set([
    "apk",
    "aab",
    "both"
  ]);

function normalizeBuildOutput(
  value
) {
  const output =
    String(
      value || "both"
    )
      .trim()
      .toLowerCase();

  return BUILD_OUTPUTS.has(
    output
  )
    ? output
    : "both";
}

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

function sanitizedConfig(
  configObject
) {
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

  /*
   * APK / AAB / BOTH artifact seçimi uygulamanın
   * kaynak kimliğini değiştirmez.
   *
   * buildOutput cache identity hash'inden çıkarılır,
   * ancak computeCacheKey sonunda ayrı bir suffix olarak
   * korunur. Böylece:
   *
   *   <identity>:apk
   *   <identity>:aab
   *   <identity>:both
   *
   * aynı uygulamaya ait artifact'ları güvenli biçimde
   * ilişkilendirebiliriz.
   */
  delete clone.buildOutput;

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

export function cacheKeyDescriptor(
  cacheKey
) {
  const match =
    /^([a-f0-9]{64}):(apk|aab|both)$/
      .exec(
        String(
          cacheKey || ""
        )
      );

  if (!match) {
    return null;
  }

  return {
    identity: match[1],
    output: match[2]
  };
}

export function cacheLookupKeys(
  cacheKey
) {
  const descriptor =
    cacheKeyDescriptor(
      cacheKey
    );

  /*
   * Eski 64 karakterlik cache key'leri
   * geriye dönük olarak yalnız exact-match ile destekle.
   */
  if (!descriptor) {
    return [cacheKey];
  }

  if (
    descriptor.output === "apk" ||
    descriptor.output === "aab"
  ) {
    /*
     * Önce aynı output için exact cache'e bak.
     * Bulunamazsa BOTH build'in artifact'ını kullan.
     */
    return [
      cacheKey,
      `${descriptor.identity}:both`
    ];
  }

  return [cacheKey];
}

function validArtifactRef(
  value
) {
  return Boolean(
    value &&
    typeof value === "object" &&
    typeof value.key === "string" &&
    value.key.trim()
  );
}

export function cacheSupportsOutput(
  outputs,
  requestedOutput
) {
  const output =
    normalizeBuildOutput(
      requestedOutput
    );

  const hasApk =
    validArtifactRef(
      outputs?.apk
    );

  const hasAab =
    validArtifactRef(
      outputs?.aab
    );

  if (output === "apk") {
    return hasApk;
  }

  if (output === "aab") {
    return hasAab;
  }

  return (
    hasApk &&
    hasAab
  );
}

async function cacheArtifactsExist(
  outputs,
  requestedOutput
) {
  const output =
    normalizeBuildOutput(
      requestedOutput
    );

  if (
    output === "apk"
  ) {
    return outputExists(
      outputs?.apk
    );
  }

  if (
    output === "aab"
  ) {
    return outputExists(
      outputs?.aab
    );
  }

  const [
    apkExists,
    aabExists
  ] =
    await Promise.all([
      outputExists(
        outputs?.apk
      ),
      outputExists(
        outputs?.aab
      )
    ]);

  return (
    apkExists &&
    aabExists
  );
}

function ttlFromRow(row) {
  return Math.max(
    1,
    Math.floor(
      (
        new Date(
          row.expires_at
        ).getTime() -
        Date.now()
      ) / 1000
    )
  );
}

async function cacheRowInRedis(
  key,
  row
) {
  await cacheSetJson(
    "build-cache",
    key,
    row,
    ttlFromRow(row)
  );
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
  const requestedOutput =
    normalizeBuildOutput(
      configObject?.buildOutput
    );

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
    /*
     * schema=2:
     * output-aware artifact cache identity.
     * Eski cache'lerle yanlış eşleşme yapılmaz.
     */
    schema: 2,
    config:
      stable(
        sanitizedConfig(
          configObject
        )
      ),
    inputs:
      stable(inputHashes)
  };

  const identity =
    crypto
      .createHash("sha256")
      .update(
        JSON.stringify(payload)
      )
      .digest("hex");

  return (
    `${identity}:${requestedOutput}`
  );
}

export async function findCache(
  cacheKey
) {
  if (!config.buildCacheEnabled) {
    return null;
  }

  const descriptor =
    cacheKeyDescriptor(
      cacheKey
    );

  const requestedOutput =
    descriptor?.output ||
    null;

  const lookupKeys =
    cacheLookupKeys(
      cacheKey
    );

  for (const lookupKey of lookupKeys) {
    const hot =
      await cacheGetJson(
        "build-cache",
        lookupKey
      );

    if (
      hot &&
      (
        !requestedOutput ||
        (
          cacheSupportsOutput(
            hot.outputs,
            requestedOutput
          ) &&
          await cacheArtifactsExist(
            hot.outputs,
            requestedOutput
          )
        )
      )
    ) {
      /*
       * BOTH fallback bulunduysa istenen output key'i
       * için de kısa yol Redis alias'ı oluştur.
       */
      if (
        lookupKey !== cacheKey &&
        hot.expires_at
      ) {
        await cacheRowInRedis(
          cacheKey,
          hot
        );
      }

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
        [lookupKey]
      );

    const row =
      result.rows[0] ||
      null;

    if (!row) {
      continue;
    }

    /*
     * Cache kaydı mevcut olsa bile gerçekten
     * istenen artifact referansını içermiyorsa
     * cache HIT sayma; normal build'e düş.
     */
    if (
      requestedOutput &&
      (
        !cacheSupportsOutput(
          row.outputs,
          requestedOutput
        ) ||
        !await cacheArtifactsExist(
          row.outputs,
          requestedOutput
        )
      )
    ) {
      continue;
    }

    await cacheRowInRedis(
      lookupKey,
      row
    );

    if (lookupKey !== cacheKey) {
      await cacheRowInRedis(
        cacheKey,
        row
      );
    }

    return row;
  }

  return null;
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