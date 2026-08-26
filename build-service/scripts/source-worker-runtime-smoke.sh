#!/bin/sh
set -eu

echo "Source Worker runtime smoke..."

uid="$(id -u)"
gid="$(id -g)"

test "$uid" = "10001"
test "$gid" = "10001"

test "${SOURCE_BUILD_ISOLATION_MODE:-}" = "dedicated"
test "${SOURCE_BUILD_REQUIRE_ISOLATION:-}" = "true"
test "${SOURCE_BUILD_ISOLATION_CAPABILITY:-}" = "source-isolation-dedicated"

case ",${WORKER_CAPABILITIES:-}," in
  *,source-isolation-dedicated,*)
    ;;
  *)
    echo "source-isolation-dedicated capability eksik." >&2
    exit 1
    ;;
esac

for dir in \
  /app/work \
  /app/outputs \
  /app/shared-inputs \
  /app/gradle-cache
do
  test -d "$dir"
  test -w "$dir"

  probe="$dir/.appforge-source-worker-smoke-$$"
  : > "$probe"
  rm -f "$probe"
done

# Application source should not be writable by the runtime user.
test -f /app/package.json

if test -w /app/package.json; then
  echo "/app/package.json runtime user tarafından yazılabilir olmamalı." >&2
  exit 1
fi

node --input-type=module <<'NODE'
import {
  assertSourceBuildIsolation
} from "/app/src/sourceBuildIsolation.js";

const capabilities =
  String(
    process.env.WORKER_CAPABILITIES ||
    ""
  )
    .split(",")
    .map(
      value =>
        value.trim()
    )
    .filter(Boolean);

const status =
  assertSourceBuildIsolation({
    engine:
      "android-gradle",
    mode:
      process.env.SOURCE_BUILD_ISOLATION_MODE,
    requireIsolation:
      process.env.SOURCE_BUILD_REQUIRE_ISOLATION ===
        "true",
    workerCapabilities:
      capabilities,
    requiredCapability:
      process.env.SOURCE_BUILD_ISOLATION_CAPABILITY
  });

if (
  !status.attestedIsolation ||
  status.blocked
) {
  throw new Error(
    "Source Worker isolation attestation smoke başarısız."
  );
}

console.log(
  JSON.stringify(
    {
      uid:
        process.getuid?.(),
      gid:
        process.getgid?.(),
      mode:
        status.mode,
      capability:
        status.requiredCapability,
      attested:
        status.attestedIsolation
    }
  )
);
NODE

echo "SOURCE_WORKER_RUNTIME_SMOKE_OK"
