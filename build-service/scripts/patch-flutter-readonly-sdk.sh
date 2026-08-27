#!/bin/sh
set -eu

target="${FLUTTER_HOME:-/opt/flutter}/bin/internal/update_engine_version.sh"

if [ ! -f "$target" ]; then
  echo "APPFORGE_FLUTTER_READONLY_PATCH_FAIL: $target bulunamadı" >&2
  exit 1
fi

python3 - "$target" <<'PYFLUTTER'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = r'''# Write the engine version out so downstream tools know what to look for.
# Use a temporary file and atomic mv to prevent race conditions during parallel flutter executions.
pid=$$
es_tmp="$FLUTTER_ROOT/bin/cache/engine.stamp.tmp.$pid"
trap 'rm -f "$es_tmp"' EXIT
echo "$ENGINE_VERSION" >"$es_tmp" && mv "$es_tmp" "$FLUTTER_ROOT/bin/cache/engine.stamp"
trap - EXIT

# The realm on CI is passed in.
if [ -n "${FLUTTER_REALM}" ]; then
  echo "$FLUTTER_REALM" >"$FLUTTER_ROOT/bin/cache/engine.realm"
else
  echo "" >"$FLUTTER_ROOT/bin/cache/engine.realm"
fi
'''

new = r'''# AppForge hardened read-only SDK guard.
#
# The pinned Flutter SDK is fully prepared during image build. Flutter normally
# rewrites engine.stamp and engine.realm on every invocation, even when their
# contents are unchanged. Under AppForge's --read-only source Worker that
# unnecessary write must be skipped.
desired_realm="${FLUTTER_REALM:-}"
current_engine=""
current_realm=""

if [ -f "$FLUTTER_ROOT/bin/cache/engine.stamp" ]; then
  current_engine="$(cat "$FLUTTER_ROOT/bin/cache/engine.stamp")"
fi

if [ -f "$FLUTTER_ROOT/bin/cache/engine.realm" ]; then
  current_realm="$(cat "$FLUTTER_ROOT/bin/cache/engine.realm")"
fi

if [ "$current_engine" != "$ENGINE_VERSION" ] || [ "$current_realm" != "$desired_realm" ]; then
  # Write only when the pinned SDK metadata really changed.
  pid=$$
  es_tmp="$FLUTTER_ROOT/bin/cache/engine.stamp.tmp.$pid"
  trap 'rm -f "$es_tmp"' EXIT
  echo "$ENGINE_VERSION" >"$es_tmp" && mv "$es_tmp" "$FLUTTER_ROOT/bin/cache/engine.stamp"
  trap - EXIT

  echo "$desired_realm" >"$FLUTTER_ROOT/bin/cache/engine.realm"
fi
'''

if "AppForge hardened read-only SDK guard" in text:
    print("Flutter read-only guard zaten uygulanmış.")
    raise SystemExit(0)

count = text.count(old)

if count != 1:
    raise SystemExit(
        f"Beklenen Flutter 3.44.9 engine metadata bloğu bulunamadı (count={count})."
    )

path.write_text(
    text.replace(old, new, 1),
    encoding="utf-8"
)

print("APPFORGE_FLUTTER_READONLY_PATCH_OK")
PYFLUTTER

grep -F \
  'AppForge hardened read-only SDK guard' \
  "$target" >/dev/null

echo "APPFORGE_FLUTTER_READONLY_GUARD_OK"
