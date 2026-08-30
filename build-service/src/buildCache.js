import AdmZip from "adm-zip";
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

function cacheDebug(
  cacheKey,
  status,
  detail = ""
) {
  const safeKey =
    String(cacheKey || "")
      .slice(0, 12);

  console.log(
    `[APPFORGE-CACHE ${safeKey}] ${status}` +
    (detail ? ` • ${detail}` : "")
  );
}

function normalizeBuildOutput(
  value
) {
  const output =
    String(
      value || "both"
    )
      .trim()
      .toLowerCase();

  return /^[a-z0-9][a-z0-9_-]{0,63}$/
    .test(output)
    ? output
    : `other-${crypto
        .createHash("sha256")
        .update(output)
        .digest("hex")}`;
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


function normalizeArchiveEntryName(
  value
) {
  const raw =
    String(
      value || ""
    )
      .replaceAll(
        "\\",
        "/"
      );

  if (
    !raw ||
    raw.startsWith("/") ||
    raw.includes("\0")
  ) {
    return null;
  }

  const parts = [];

  for (
    const part of
    raw.split("/")
  ) {
    if (
      !part ||
      part === "."
    ) {
      continue;
    }

    if (
      part === ".."
    ) {
      return null;
    }

    parts.push(part);
  }

  if (
    !parts.length ||
    parts.length > 20
  ) {
    return null;
  }

  const normalized =
    parts.join("/");

  if (
    normalized.length > 240
  ) {
    return null;
  }

  return normalized;
}

/*
 * Proje ZIP'i yeniden paketlendiğinde:
 *
 * - ZIP timestamp
 * - ZIP entry sırası
 * - compression metadata
 *
 * değişebilir.
 *
 * Bunlar uygulama kaynak içeriğini değiştirmez.
 * Artifact cache bu metadata yerine gerçek dosya
 * içeriklerinin SHA-256 kimliğini kullanır.
 */
export async function projectContentSha256(
  file
) {
  if (!file) {
    return null;
  }

  const rawFallback =
    () =>
      fileSha256(file);

  try {
    const zip =
      new AdmZip(file);

    const entries =
      zip.getEntries();

    if (
      !entries.length ||
      entries.length > 10_000
    ) {
      return rawFallback();
    }

    const records = [];
    const seen =
      new Set();

    let declaredTotal = 0;
    let actualTotal = 0;

    const maxTotal =
      180 *
      1024 *
      1024;

    for (
      const entry of entries
    ) {
      const name =
        normalizeArchiveEntryName(
          entry.entryName
        );

      if (
        !name ||
        seen.has(name)
      ) {
        return rawFallback();
      }

      seen.add(name);

      const mode =
        (
          Number(
            entry.header?.attr ||
            0
          ) >>> 16
        ) &
        0xffff;

      if (
        (
          mode &
          0o170000
        ) ===
        0o120000
      ) {
        return rawFallback();
      }

      if (
        entry.isDirectory
      ) {
        records.push([
          name,
          "directory",
          0,
          ""
        ]);

        continue;
      }

      const declared =
        Math.max(
          0,
          Number(
            entry.header?.size ||
            0
          )
        );

      declaredTotal +=
        declared;

      if (
        declaredTotal >
        maxTotal
      ) {
        return rawFallback();
      }

      const data =
        entry.getData();

      actualTotal +=
        data.length;

      if (
        actualTotal >
        maxTotal
      ) {
        return rawFallback();
      }

      const digest =
        crypto
          .createHash(
            "sha256"
          )
          .update(data)
          .digest("hex");

      records.push([
        name,
        "file",
        data.length,
        digest
      ]);
    }

    records.sort(
      (a, b) =>
        a[0] < b[0]
          ? -1
          : a[0] > b[0]
            ? 1
            : 0
    );

    return crypto
      .createHash(
        "sha256"
      )
      .update(
        "appforge-project-content-v1\n"
      )
      .update(
        JSON.stringify(
          records
        )
      )
      .digest("hex");
  } catch {
    /*
     * ZIP değilse veya güvenli canonicalization
     * yapılamazsa eski exact-byte davranışına dön.
     */
    return rawFallback();
  }
}

export function cacheKeyDescriptor(
  cacheKey
) {
  const match =
    /^([a-f0-9]{64}):([a-z0-9][a-z0-9_-]{0,69})$/
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

  if (output === "both") {
    return (
      hasApk &&
      hasAab
    );
  }

  return validArtifactRef(
    outputs?.[output]
  );
}

export function outputsForRequest(
  outputs,
  requestedOutput
) {
  const output =
    normalizeBuildOutput(
      requestedOutput
    );

  if (output === "both") {
    return {
      apk: outputs?.apk,
      aab: outputs?.aab
    };
  }

  return {
    [output]: outputs?.[output]
  };
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

  if (output !== "both") {
    return outputExists(
      outputs?.[output]
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
      await projectContentSha256(
        projectFile
      ),
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
    cacheDebug(cacheKey, "DISABLED");
    return null;
  }

  cacheDebug(cacheKey, "LOOKUP");

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
      const hit =
        requestedOutput
          ? {
              ...hot,
              outputs:
                outputsForRequest(
                  hot.outputs,
                  requestedOutput
                )
            }
          : hot;

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
          hit
        );
      }

      return hit;
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

    const hit =
      requestedOutput
        ? {
            ...row,
            outputs:
              outputsForRequest(
                row.outputs,
                requestedOutput
              )
          }
        : row;

    await cacheRowInRedis(
      lookupKey,
      row
    );

    if (lookupKey !== cacheKey) {
      await cacheRowInRedis(
        cacheKey,
        hit
      );
    }

    return hit;
  }

  cacheDebug(cacheKey, "MISS");
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
