#!/bin/sh
set -eu
cd /workspace/source
[ -f package.json ] || { echo "package.json bulunamadı." >&2; exit 31; }

if [ "${APPFORGE_DEVICE_OFFLINE:-0}" = "1" ]; then
  set -- --offline
else
  set --
fi

if [ -f package-lock.json ]; then
  npm ci "$@" --include=dev --ignore-scripts --no-audit --no-fund
else
  npm install "$@" --include=dev --ignore-scripts --no-audit --no-fund
fi

if grep -Eq '"vite"[[:space:]]*:' package.json; then
  npm run build -- --base=./
else
  npm run build
fi

OUT=""
for dir in dist build out; do
  if [ -f "$dir/index.html" ]; then OUT="$dir"; break; fi
done

if [ -z "$OUT" ]; then
  INDEX="$(find . -maxdepth 5 -type f -name index.html ! -path './node_modules/*' ! -path './.git/*' | head -n 1)"
  [ -n "$INDEX" ] && OUT="$(dirname "$INDEX")"
fi

[ -n "$OUT" ] && [ -f "$OUT/index.html" ] || {
  echo "Statik index.html build çıktısı bulunamadı." >&2
  exit 32
}

printf '%s\n' "$OUT" > /workspace/.appforge-web-output
echo "APPFORGE_NODE_OUTPUT=$OUT"
