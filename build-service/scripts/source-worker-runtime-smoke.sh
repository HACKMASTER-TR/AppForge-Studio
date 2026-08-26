#!/bin/sh
set -eu

fail() {
  echo "SOURCE_WORKER_RUNTIME_SMOKE_FAIL: $*" >&2
  exit 1
}

echo "Source Worker runtime smoke..."

uid="$(id -u)"
gid="$(id -g)"

echo "runtime uid=${uid} gid=${gid}"

test "$uid" = "10001" ||
  fail "UID beklenen=10001 gerçek=${uid}"

test "$gid" = "10001" ||
  fail "GID beklenen=10001 gerçek=${gid}"

echo "isolation mode=${SOURCE_BUILD_ISOLATION_MODE:-<empty>}"
echo "isolation required=${SOURCE_BUILD_REQUIRE_ISOLATION:-<empty>}"
echo "isolation capability=${SOURCE_BUILD_ISOLATION_CAPABILITY:-<empty>}"
echo "worker capabilities=${WORKER_CAPABILITIES:-<empty>}"

test "${SOURCE_BUILD_ISOLATION_MODE:-}" = "dedicated" ||
  fail "SOURCE_BUILD_ISOLATION_MODE dedicated değil"

test "${SOURCE_BUILD_REQUIRE_ISOLATION:-}" = "true" ||
  fail "SOURCE_BUILD_REQUIRE_ISOLATION true değil"

test "${SOURCE_BUILD_ISOLATION_CAPABILITY:-}" = "source-isolation-dedicated" ||
  fail "SOURCE_BUILD_ISOLATION_CAPABILITY hatalı"

case ",${WORKER_CAPABILITIES:-}," in
  *,source-isolation-dedicated,*)
    ;;
  *)
    fail "source-isolation-dedicated Worker capability eksik"
    ;;
esac

for dir in   /app/work   /app/outputs   /app/shared-inputs   /app/gradle-cache
do
  test -d "$dir" ||
    fail "${dir} dizini yok"

  echo "mount dir: $(ls -ldn "$dir" 2>/dev/null || echo '<stat failed>')"

  test -w "$dir" ||
    fail "${dir} UID 10001 için yazılabilir değil"

  probe="$dir/.appforge-source-worker-smoke-$$"

  if ! : > "$probe"; then
    fail "${dir} içinde probe dosyası oluşturulamadı"
  fi

  rm -f "$probe" ||
    fail "${dir} içindeki probe dosyası silinemedi"
done

test -f /app/package.json ||
  fail "/app/package.json bulunamadı"

if test -w /app/package.json; then
  fail "/app/package.json runtime user tarafından yazılabilir olmamalı"
fi

if ! node --input-type=module <<'NODE'
import {
  assertSourceBuildIsolation
} from "/app/src/sourceBuildIsolation.js";

const capabilities =
  String(
    process.env.WORKER_CAPABILITIES ||
    ""
  )
    .split(",")
    .map(value => value.trim())
    .filter(Boolean);

const status =
  assertSourceBuildIsolation({
    engine: "android-gradle",
    mode: process.env.SOURCE_BUILD_ISOLATION_MODE,
    requireIsolation:
      process.env.SOURCE_BUILD_REQUIRE_ISOLATION === "true",
    workerCapabilities: capabilities,
    requiredCapability:
      process.env.SOURCE_BUILD_ISOLATION_CAPABILITY
  });

if (!status.attestedIsolation || status.blocked) {
  throw new Error(
    "Source Worker isolation attestation smoke başarısız."
  );
}

console.log(
  JSON.stringify({
    uid: process.getuid?.(),
    gid: process.getgid?.(),
    mode: status.mode,
    capability: status.requiredCapability,
    attested: status.attestedIsolation
  })
);
NODE
then
  fail "Node source isolation attestation kontrolü başarısız"
fi

echo "SOURCE_WORKER_RUNTIME_SMOKE_OK"
