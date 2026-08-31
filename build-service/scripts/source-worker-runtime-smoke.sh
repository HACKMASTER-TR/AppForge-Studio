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
  code=0

  timeout 60 "$@" >"$output_file" 2>&1 ||
    code="$?"

  if [ "$code" -ne 0 ]; then
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

ensure_cache_dir() {
  label="$1"
  path="$2"

  test -n "$path" ||
    fail "${label} boş"

  case "$path" in
    "${APPFORGE_USER_CACHE_ROOT}"/*)
      ;;
    *)
      fail "${label} kullanıcı cache kökü dışında: ${path}"
      ;;
  esac

  mkdir -p "$path" ||
    fail "${label} oluşturulamadı: ${path}"

  test -d "$path" ||
    fail "${label} dizin değil: ${path}"

  test -w "$path" ||
    fail "${label} UID 10001 için yazılabilir değil: ${path}"

  probe="${path}/.appforge-cache-smoke-$$"

  : > "$probe" ||
    fail "${label} probe oluşturulamadı"

  rm -f "$probe" ||
    fail "${label} probe silinemedi"

  echo "CACHE_OK: ${label}=${path}"
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
echo "APPFORGE_USER_CACHE_ROOT=${APPFORGE_USER_CACHE_ROOT:-<empty>}"
echo "GRADLE_USER_HOME=${GRADLE_USER_HOME:-<empty>}"
echo "NPM_CONFIG_CACHE=${NPM_CONFIG_CACHE:-<empty>}"
echo "PIP_CACHE_DIR=${PIP_CACHE_DIR:-<empty>}"
echo "PUB_CACHE=${PUB_CACHE:-<empty>}"
echo "DOTNET_CLI_HOME=${DOTNET_CLI_HOME:-<empty>}"
echo "NUGET_PACKAGES=${NUGET_PACKAGES:-<empty>}"
echo "XDG_CACHE_HOME=${XDG_CACHE_HOME:-<empty>}"
echo "XDG_DATA_HOME=${XDG_DATA_HOME:-<empty>}"
echo "XDG_STATE_HOME=${XDG_STATE_HOME:-<empty>}"
echo "YARN_CACHE_FOLDER=${YARN_CACHE_FOLDER:-<empty>}"
echo "COREPACK_HOME=${COREPACK_HOME:-<empty>}"
echo "COREPACK_ENABLE_NETWORK=${COREPACK_ENABLE_NETWORK:-<empty>}"
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

test "${HOME:-}" = "/home/appforge" ||
  fail "HOME beklenmeyen değer: ${HOME:-<empty>}"

test -d "$HOME" ||
  fail "HOME dizini yok"

test -w "$HOME" ||
  fail "HOME UID 10001 için yazılabilir değil"

for dir in \
  /app/work \
  /app/outputs \
  /app/shared-inputs
do
  test -d "$dir" ||
    fail "${dir} dizini yok"

  echo "mount dir: $(ls -ldn "$dir" 2>/dev/null || echo '<stat failed>')"

  test -w "$dir" ||
    fail "${dir} UID 10001 için yazılabilir değil"

  probe="$dir/.appforge-source-worker-smoke-$$"

  : > "$probe" ||
    fail "${dir} içinde probe dosyası oluşturulamadı"

  rm -f "$probe" ||
    fail "${dir} içindeki probe dosyası silinemedi"
done

test "${APPFORGE_USER_CACHE_ROOT:-}" = "/app/user-cache/10001" ||
  fail "APPFORGE_USER_CACHE_ROOT beklenmeyen değer"

test -d "$APPFORGE_USER_CACHE_ROOT" ||
  fail "kullanıcı cache kökü yok"

test -w "$APPFORGE_USER_CACHE_ROOT" ||
  fail "kullanıcı cache kökü yazılabilir değil"

cache_owner="$(stat -c '%u:%g' "$APPFORGE_USER_CACHE_ROOT")"
test "$cache_owner" = "10001:10001" ||
  fail "cache sahibi beklenen=10001:10001 gerçek=${cache_owner}"

cache_mode="$(stat -c '%a' "$APPFORGE_USER_CACHE_ROOT")"
test "$cache_mode" = "700" ||
  fail "cache modu beklenen=700 gerçek=${cache_mode}"

ensure_cache_dir "GRADLE_USER_HOME" "${GRADLE_USER_HOME:-}"
ensure_cache_dir "NPM_CONFIG_CACHE" "${NPM_CONFIG_CACHE:-}"
ensure_cache_dir "PIP_CACHE_DIR" "${PIP_CACHE_DIR:-}"
ensure_cache_dir "PUB_CACHE" "${PUB_CACHE:-}"
ensure_cache_dir "DOTNET_CLI_HOME" "${DOTNET_CLI_HOME:-}"
ensure_cache_dir "NUGET_PACKAGES" "${NUGET_PACKAGES:-}"
ensure_cache_dir "XDG_CACHE_HOME" "${XDG_CACHE_HOME:-}"
ensure_cache_dir "XDG_DATA_HOME" "${XDG_DATA_HOME:-}"
ensure_cache_dir "XDG_STATE_HOME" "${XDG_STATE_HOME:-}"
ensure_cache_dir "YARN_CACHE_FOLDER" "${YARN_CACHE_FOLDER:-}"

test "${COREPACK_HOME:-}" = "/opt/appforge-corepack" ||
  fail "COREPACK_HOME immutable toolchain yolunda değil"

test -d "$COREPACK_HOME" ||
  fail "Corepack toolchain cache yok"

if test -w "$COREPACK_HOME"; then
  fail "Corepack toolchain payloadı runtime user tarafından yazılabilir olmamalı"
fi

test "${COREPACK_ENABLE_NETWORK:-}" = "0" ||
  fail "Corepack runtime network kapalı değil"

test "${DOTNET_SKIP_FIRST_TIME_EXPERIENCE:-}" = "1" ||
  fail "DOTNET_SKIP_FIRST_TIME_EXPERIENCE aktif değil"

test "${FLUTTER_ALREADY_LOCKED:-}" = "true" ||
  fail "Flutter read-only SDK lock koruması aktif değil"

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
run_tool flutter flutter --no-version-check --version
run_tool dart dart --version
run_tool cmake cmake --version
run_tool ninja ninja --version

flutter_smoke_dir="/app/work/flutter-readonly-smoke"
mkdir -p "$flutter_smoke_dir/lib"
printf '%s\n' \
  'name: appforge_flutter_readonly_smoke' \
  'environment:' \
  "  sdk: '>=3.0.0 <4.0.0'" \
  >"$flutter_smoke_dir/pubspec.yaml"
printf '%s\n' \
  "void main() {}" \
  >"$flutter_smoke_dir/lib/main.dart"

(
  cd "$flutter_smoke_dir"
  run_tool flutter_pub_get flutter --no-version-check pub get --offline
)

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
echo "SOURCE_WORKER_CACHE_HARDENING_OK"
echo "SOURCE_WORKER_TOOLCHAIN_SMOKE_OK"
echo "SOURCE_WORKER_RUNTIME_SMOKE_OK"
