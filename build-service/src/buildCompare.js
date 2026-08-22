const SECRET_KEY =
  /(password|token|secret|api.?key|keystore|credential)/i;

function redact(
  key,
  value
) {
  if (
    SECRET_KEY.test(
      String(key)
    )
  ) {
    return "[REDACTED]";
  }

  return value;
}

function primitive(
  value
) {
  return (
    value === null ||
    [
      "string",
      "number",
      "boolean",
      "undefined"
    ].includes(
      typeof value
    )
  );
}

function walkDiff(
  before,
  after,
  prefix,
  out,
  limit = 150
) {
  if (
    out.length >=
    limit
  ) {
    return;
  }

  if (
    primitive(before) &&
    primitive(after)
  ) {
    if (
      before !==
      after
    ) {
      const key =
        prefix
          .split(".")
          .at(-1) ||
        prefix;

      out.push({
        path:
          prefix,
        before:
          redact(
            key,
            before
          ),
        after:
          redact(
            key,
            after
          )
      });
    }

    return;
  }

  if (
    Array.isArray(before) ||
    Array.isArray(after)
  ) {
    const left =
      JSON.stringify(
        before ?? null
      );

    const right =
      JSON.stringify(
        after ?? null
      );

    if (
      left !==
      right
    ) {
      out.push({
        path:
          prefix,
        before:
          left.slice(
            0,
            500
          ),
        after:
          right.slice(
            0,
            500
          )
      });
    }

    return;
  }

  const a =
    before &&
    typeof before ===
      "object"
      ? before
      : {};

  const b =
    after &&
    typeof after ===
      "object"
      ? after
      : {};

  const keys =
    new Set([
      ...Object.keys(a),
      ...Object.keys(b)
    ]);

  for (
    const key of keys
  ) {
    const path =
      prefix
        ? `${prefix}.${key}`
        : key;

    walkDiff(
      a[key],
      b[key],
      path,
      out,
      limit
    );

    if (
      out.length >=
      limit
    ) {
      break;
    }
  }
}

function artifactSize(
  build,
  kind
) {
  return Number(
    build.outputs
      ?.[kind]
      ?.sizeBytes ||
    build.artifact_manifest
      ?.outputs
      ?.[kind]
      ?.sizeBytes ||
    0
  );
}

function releaseNotes(
  left,
  right,
  changes
) {
  const notes =
    [];

  if (
    left.config
      ?.versionName !==
    right.config
      ?.versionName
  ) {
    notes.push(
      `Sürüm ${left.config?.versionName || "-"} → ${right.config?.versionName || "-"}.`
    );
  }

  if (
    left.config
      ?.versionCode !==
    right.config
      ?.versionCode
  ) {
    notes.push(
      `versionCode ${left.config?.versionCode || "-"} → ${right.config?.versionCode || "-"}.`
    );
  }

  const featurePaths =
    changes
      .filter(
        item =>
          item.path.startsWith(
            "features."
          ) ||
          item.path.startsWith(
            "nativeBridge."
          ) ||
          item.path.startsWith(
            "billing."
          ) ||
          item.path.startsWith(
            "firebase."
          ) ||
          item.path.startsWith(
            "admob."
          )
      )
      .slice(
        0,
        12
      );

  for (
    const change of
    featurePaths
  ) {
    notes.push(
      `${change.path}: ${String(change.before)} → ${String(change.after)}.`
    );
  }

  if (
    !notes.length
  ) {
    notes.push(
      "Yapılandırma düzeyinde belirgin kullanıcı özelliği değişikliği bulunamadı."
    );
  }

  return notes;
}

export function compareBuilds(
  left,
  right
) {
  const changes =
    [];

  walkDiff(
    left.config || {},
    right.config || {},
    "",
    changes
  );

  const apkBefore =
    artifactSize(
      left,
      "apk"
    );

  const apkAfter =
    artifactSize(
      right,
      "apk"
    );

  const aabBefore =
    artifactSize(
      left,
      "aab"
    );

  const aabAfter =
    artifactSize(
      right,
      "aab"
    );

  return {
    left: {
      buildId:
        left.id,
      versionName:
        left.config
          ?.versionName ||
        null,
      versionCode:
        left.config
          ?.versionCode ||
        null,
      apkSizeBytes:
        apkBefore,
      aabSizeBytes:
        aabBefore
    },
    right: {
      buildId:
        right.id,
      versionName:
        right.config
          ?.versionName ||
        null,
      versionCode:
        right.config
          ?.versionCode ||
        null,
      apkSizeBytes:
        apkAfter,
      aabSizeBytes:
        aabAfter
    },
    deltas: {
      apkBytes:
        apkAfter -
        apkBefore,
      aabBytes:
        aabAfter -
        aabBefore
    },
    changeCount:
      changes.length,
    changes,
    releaseNotes:
      releaseNotes(
        left,
        right,
        changes
      )
  };
}
