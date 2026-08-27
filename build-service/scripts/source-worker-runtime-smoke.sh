#!/bin/sh
set -eu

fail() {
  echo "SOURCE_WORKER_RUNTIME_SMOKE_FAIL: $*" >&2
  exit 1
}

run_tool() {
  label="$1"
  shift

  echo
  echo "=== tool: ${label} ==="
  echo "command: $*"

  first="$1"

  command -v "$first" >/dev/null 2>&1 ||
    fail "${label}: ${first} PATH içinde bulunamadı"

  output_file="/tmp/appforge-tool-${label}-$$.log"

  if ! timeout 60 "$@" >"$output_file" 2>&1; then
    code="$?"
    echo "--- ${label} output ---" >&2
    cat "$output_file" >&2 || true
    rm -f "$output_file" || true
    fail "${label} çalıştırılamadı (exit=${code})"
  fi

  sed -n '1,12p' "$output_file"
  rm -f "$output_file" ||
    fail "${label} geçici logu silinemedi"

  echo "TOOL_OK: ${label}"
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

echo "HOME=${HOME:-<empty>}"
echo "GRADLE_USER_HOME=${GRADLE_USER_HOME:-<empty>}"
echo "NPM_CONFIG_CACHE=${NPM_CONFIG_CACHE:-<empty>}"
echo "PIP_CACHE_DIR=${PIP_CACHE_DIR:-<empty>}"
echo "PUB_CACHE=${PUB_CACHE:-<empty>}"
echo "DOTNET_CLI_HOME=${DOTNET_CLI_HOME:-<empty>}"
echo "NUGET_PACKAGES=${NUGET_PACKAGES:-<empty>}"
echo "XDG_CACHE_HOME=${XDG_CACHE_HOME:-<empty>}"
echo "COREPACK_HOME=${COREPACK_HOME:-<empty>}"
echo "FLUTTER_ALREADY_LOCKED=${FLUTTER_ALREADY_LOCKED:-<empty>}"

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

for dir in \
  /app/work \
  /app/outputs \
  /app/shared-inputs \
  /app/gradle-cache
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

# ---------------------------------------------------------
# Real toolchain smoke under:
#   --network none
#   --read-only
#   --cap-drop ALL
#   no-new-privileges
#   UID/GID 10001
# ---------------------------------------------------------
run_tool node node --version
run_tool npm npm --version
run_tool yarn yarn --version
run_tool pnpm pnpm --version
run_tool python python3 --version
run_tool pip python3 -m pip --version
run_tool java java -version
run_tool javac javac -version
run_tool gradle gradle --version
run_tool dotnet dotnet --info
run_tool flutter flutter --version
run_tool dart dart --version
run_tool cmake cmake --version
run_tool ninja ninja --version

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

echo
echo "SOURCE_WORKER_TOOLCHAIN_SMOKE_OK"
echo "SOURCE_WORKER_RUNTIME_SMOKE_OK"
