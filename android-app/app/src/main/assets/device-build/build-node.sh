#!/bin/sh
set -eu

cd /workspace/source

[ -f package.json ] || {
  echo "package.json bulunamadı." >&2
  exit 31
}

OFFLINE="${APPFORGE_DEVICE_OFFLINE:-0}"

CACHE="/opt/appforge-device/npm-cache-v1"

mkdir -p "$CACHE"

export NPM_CONFIG_CACHE="$CACHE"
export NPM_CONFIG_UPDATE_NOTIFIER=false

LOG="/workspace/.appforge-npm-install.log"
RETRY_LOG="/workspace/.appforge-npm-install-retry.log"

rm -f "$LOG" "$RETRY_LOG"

cleanup() {
  rm -f "$LOG" "$RETRY_LOG"
}

trap cleanup EXIT HUP INT TERM

npm_install() {

  if [ -f package-lock.json ]; then

    if [ "$OFFLINE" = "1" ]; then
      npm ci \
        --offline \
        --include=dev \
        --include=optional \
        --ignore-scripts \
        --no-audit \
        --no-fund
    else
      npm ci \
        --include=dev \
        --include=optional \
        --ignore-scripts \
        --no-audit \
        --no-fund
    fi

  else

    if [ "$OFFLINE" = "1" ]; then
      npm install \
        --offline \
        --include=dev \
        --include=optional \
        --ignore-scripts \
        --no-audit \
        --no-fund
    else
      npm install \
        --include=dev \
        --include=optional \
        --ignore-scripts \
        --no-audit \
        --no-fund
    fi

  fi
}

if npm_install >"$LOG" 2>&1
then

  cat "$LOG"

else

  FIRST_STATUS=$?

  cat "$LOG" >&2

  if \
    [ "$OFFLINE" != "1" ] &&
    grep -Fq "code ENOENT" "$LOG" &&
    grep -Fq "_cacache/tmp" "$LOG"
  then

    echo "APPFORGE_NPM_CACHE_TRANSIENT_RETRY"

    # Only disposable npm temp entries are removed.
    # Cached package content is preserved.
    rm -rf "$CACHE/_cacache/tmp"
    mkdir -p "$CACHE/_cacache/tmp"

    if npm_install >"$RETRY_LOG" 2>&1
    then

      cat "$RETRY_LOG"

      echo "APPFORGE_NPM_CACHE_RETRY=PASS"

    else

      RETRY_STATUS=$?

      cat "$RETRY_LOG" >&2

      echo "APPFORGE_NPM_CACHE_RETRY=FAIL" >&2

      exit "$RETRY_STATUS"

    fi

  else

    exit "$FIRST_STATUS"

  fi

fi

verify_native_optionals() {

  if [ -f node_modules/esbuild/package.json ]; then

    node -e '
      const esbuild = require("esbuild");
      esbuild.transformSync(
        "const appforgeOptionalCheck = 1"
      );
      console.log(
        "APPFORGE_ESBUILD_BINARY=PASS"
      );
    '

  fi

  if [ -f node_modules/rollup/package.json ]; then

    node \
      --input-type=module \
      -e '
        await import("rollup");
        console.log(
          "APPFORGE_ROLLUP_BINARY=PASS"
        );
      '

  fi
}

verify_native_optionals

echo "APPFORGE_NODE_NATIVE_OPTIONALS=PASS"

if grep -Eq '"vite"[[:space:]]*:' package.json
then
  npm run build -- --base=./
else
  npm run build
fi

OUT=""

for dir in dist build out
do
  if [ -f "$dir/index.html" ]; then
    OUT="$dir"
    break
  fi
done

if [ -z "$OUT" ]; then

  INDEX="$(
    find . \
      -maxdepth 5 \
      -type f \
      -name index.html \
      ! -path './node_modules/*' \
      ! -path './.git/*' \
      | head -n 1
  )"

  [ -n "$INDEX" ] &&
    OUT="$(dirname "$INDEX")"
fi

[ -n "$OUT" ] &&
[ -f "$OUT/index.html" ] || {
  echo "Statik index.html build çıktısı bulunamadı." >&2
  exit 32
}

printf '%s\n' "$OUT" \
  > /workspace/.appforge-web-output

echo "APPFORGE_NODE_OUTPUT=$OUT"
