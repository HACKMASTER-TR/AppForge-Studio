#!/bin/sh
set -eu
if [ -f /workspace/runtime/module-download-telemetry.sh ]; then
  . /workspace/runtime/module-download-telemetry.sh
fi
ROOT=/opt/appforge-device
VERSION=22.23.3
DEST="$ROOT/node-$VERSION"
CACHE="$ROOT/cache"
ensure_links() {
 mkdir -p /usr/local/bin
 for NAME in node npm npx; do
  LINK="/usr/local/bin/$NAME"
  if [ -e "$LINK" ] || [ -L "$LINK" ]; then
   [ "$(readlink -f "$LINK")" = "$(readlink -f "$DEST/bin/$NAME")" ] || { echo 'Conflicting Node executable preserved'; exit 59; }
  else ln -s "$DEST/bin/$NAME" "$LINK"; fi
 done
}
[ "${APPFORGE_DEVICE_OFFLINE:-0}" != 1 ] || { echo 'APPFORGE_NODE_OFFLINE_MISSING'; exit 42; }
case "$(uname -m)" in
 aarch64|arm64) ARCH=arm64; SHA=a44aeb94849a299b22df10b9e622ec2f605c2183501bc40590705131de7c740f ;;
 x86_64|amd64) ARCH=x64; SHA=df450af89261115ef9f9e3830c3eeb2cc9213b63c720b1af623cb5dcbe2e02de ;;
 *) echo 'Unsupported Node module architecture'; exit 54 ;;
esac
# No SDK, JDK, Gradle, Python, Expo or Windows installation here.
if ! command -v curl >/dev/null || ! command -v xz >/dev/null; then
 apt-get update
 apt-get install -y --no-install-recommends --no-upgrade ca-certificates curl tar xz-utils
fi
command -v curl >/dev/null
command -v tar >/dev/null
command -v xz >/dev/null
mkdir -p "$CACHE"
ARCHIVE="$CACHE/node-v$VERSION-linux-$ARCH.tar.xz"
STAGE="$ROOT/node-$VERSION-stage-$$"
trap 'rm -rf "$STAGE"; rm -f "$ARCHIVE.part"' 0
if ! printf '%s  %s\n' "$SHA" "$ARCHIVE" | sha256sum -c - >/dev/null 2>&1; then
 curl -fL --retry 4 --connect-timeout 20 "https://nodejs.org/dist/v$VERSION/node-v$VERSION-linux-$ARCH.tar.xz" -o "$ARCHIVE.part"
 printf '%s  %s\n' "$SHA" "$ARCHIVE.part" | sha256sum -c -
 mv "$ARCHIVE.part" "$ARCHIVE"
fi
mkdir -p "$STAGE"
# Digest is pinned; reject absolute or parent path components before extraction.
if tar -tJf "$ARCHIVE" | grep -E '(^/|(^|/)\.\.(/|$))' >/dev/null; then
 echo 'Unsafe Node archive path'; exit 57
fi
tar -xJf "$ARCHIVE" --no-same-owner --no-same-permissions -C "$STAGE" --strip-components=1
[ "$("$STAGE/bin/node" --version)" = "v$VERSION" ]
PATH="$STAGE/bin:$PATH" "$STAGE/bin/node" "$STAGE/lib/node_modules/npm/bin/npm-cli.js" --version
# Never overwrite a working installation. An invalid old directory remains for diagnosis.
if [ -e "$DEST" ]; then
 if cmp -s "$STAGE/bin/node" "$DEST/bin/node" && cmp -s "$STAGE/lib/node_modules/npm/bin/npm-cli.js" "$DEST/lib/node_modules/npm/bin/npm-cli.js"; then
  ensure_links; echo 'APPFORGE_NODE_MODULE_READY'; exit 0
 fi
 echo 'Existing invalid Node module preserved; repair requires review'; exit 58
fi
mv "$STAGE" "$DEST"
ensure_links
echo 'APPFORGE_NODE_MODULE_READY'
