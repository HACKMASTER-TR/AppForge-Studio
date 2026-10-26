#!/bin/sh
# Existing Node/Cargo/module controller owns consent, networking and staging.
# This adapter builds authored JS only. It never runs Expo prebuild or host AOT.
set -eu
ROOT=/opt/appforge-device
SOURCE=/workspace/source
NODE="$ROOT/node-22.23.3/bin/node"
NPM="$ROOT/node-22.23.3/lib/node_modules/npm/bin/npm-cli.js"
export PATH="$ROOT/node-22.23.3/bin:$PATH"
export NPM_CONFIG_CACHE="$ROOT/npm-cache-expo54-v1"
export NPM_CONFIG_UPDATE_NOTIFIER=false
export CI=1
cd "$SOURCE"
test -f package-lock.json
test -f index.js
test -f android/app/build.gradle
/usr/bin/python3.12 -I -S /workspace/runtime/rust-react-native.py "$SOURCE" --check-project
if [ "${APPFORGE_DEVICE_OFFLINE:-0}" = 1 ];then
 "$NODE" "$NPM" ci --offline --no-audit --no-fund
else
 "$NODE" "$NPM" ci --no-audit --no-fund
fi
assets="$SOURCE/android/app/src/main/assets"
mkdir -p "$assets"
if [ -e "$assets/index.android.bundle" ] || [ -L "$assets/index.android.bundle" ];then
 echo 'Authored/preexisting JS bundle preserved; use a clean reviewed source copy.' >&2
 exit 44
fi
stage=$(mktemp -d "$SOURCE/.rust-metro.XXXXXXXX")
trap 'rm -rf "$stage"' EXIT INT TERM
"$NODE" "$SOURCE/node_modules/react-native/cli.js" bundle \
 --platform android --dev false --entry-file index.js \
 --bundle-output "$stage/index.android.bundle" \
 --assets-dest "$SOURCE/android/app/src/main/res" --max-workers 1
test -s "$stage/index.android.bundle"
mv "$stage/index.android.bundle" "$assets/index.android.bundle"
echo 'APPFORGE_STANDALONE_RN_AUTHORED_JS_BUNDLE=PASS'
