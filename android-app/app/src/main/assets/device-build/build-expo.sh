#!/bin/sh
set -eu

ROOT="/opt/appforge-device"
NODE_HOME="$ROOT/node-22.23.3"
SOURCE="/workspace/source"
CACHE="$ROOT/npm-cache-expo54-v1"

export PATH="$NODE_HOME/bin:$PATH"
export NPM_CONFIG_CACHE="$CACHE"
export NPM_CONFIG_UPDATE_NOTIFIER=false
export EXPO_NO_TELEMETRY=1
export CI=1

test -x "$NODE_HOME/bin/node"
test -x "$NODE_HOME/bin/npm"
test -f "$SOURCE/package.json"

cd "$SOURCE"

node --version
npm --version

if [ "${APPFORGE_DEVICE_OFFLINE:-0}" = "1" ]; then
  echo "APPFORGE_EXPO_NPM_MODE=OFFLINE"

  if [ -f package-lock.json ]; then
    npm ci \
      --offline \
      --no-audit \
      --no-fund
  else
    npm install \
      --offline \
      --no-audit \
      --no-fund
  fi
else
  echo "APPFORGE_EXPO_NPM_MODE=ONLINE"

  if [ -f package-lock.json ]; then
    npm ci \
      --no-audit \
      --no-fund
  else
    npm install \
      --no-audit \
      --no-fund
  fi
fi

node <<'NODE'
const fs = require("fs");

const expo =
  require("./node_modules/expo/package.json").version;

const rn =
  require("./node_modules/react-native/package.json").version;

console.log("APPFORGE_EXPO_VERSION=" + expo);
console.log("APPFORGE_REACT_NATIVE_VERSION=" + rn);

if (!expo.startsWith("54.")) {
  throw new Error(
    "Experimental device engine accepts Expo SDK 54 only."
  );
}

if (!rn.startsWith("0.81.")) {
  throw new Error(
    "Experimental device engine accepts React Native 0.81 only."
  );
}

const path = "app.json";

const app =
  JSON.parse(
    fs.readFileSync(path, "utf8")
  );

app.expo = app.expo || {};
app.expo.newArchEnabled = false;

fs.writeFileSync(
  path,
  JSON.stringify(app, null, 2) + "\n"
);
NODE

#
# Expo config-plugins locate MainApplication/MainActivity with glob.
# /workspace is a PRoot bind mount. Keep npm/project ownership there,
# but run CNG/prebuild on the rootfs-native filesystem so Expo's native
# file discovery does not depend on bind-mount glob semantics.
#
PREBUILD_ROOT="$ROOT/expo-prebuild-work"
PREBUILD="$PREBUILD_ROOT/$$"

rm -rf "$PREBUILD"
mkdir -p "$PREBUILD"

cleanup_prebuild() {
  rm -rf "$PREBUILD"
}

trap cleanup_prebuild EXIT INT TERM

echo "APPFORGE_EXPO_PREBUILD_SOURCE=$SOURCE"
echo "APPFORGE_EXPO_PREBUILD_STAGE=$PREBUILD"

(
  cd "$SOURCE"

  tar \
    --exclude='./node_modules' \
    --exclude='./android' \
    --exclude='./ios' \
    --exclude='./.git' \
    -cf - \
    .
) | (
  cd "$PREBUILD"
  tar -xf -
)

ln -s \
  "$SOURCE/node_modules" \
  "$PREBUILD/node_modules"

cd "$PREBUILD"

test -L node_modules
test -f package.json
test -f app.json

echo "APPFORGE_EXPO_PREBUILD_FS=NATIVE_ROOTFS"

"$NODE_HOME/bin/npx" \
  expo prebuild \
  --platform android \
  --no-install \
  --clean

test -f android/gradle.properties
test -f android/app/build.gradle
test -f android/settings.gradle

MAIN_APPLICATION="$(
  find \
    android/app/src/main/java \
    -type f \
    \( \
      -name 'MainApplication.kt' \
      -o \
      -name 'MainApplication.java' \
    \) \
    -print \
    | head -n 1
)"

test -n "$MAIN_APPLICATION"
test -f "$MAIN_APPLICATION"

echo "APPFORGE_EXPO_MAIN_APPLICATION=$MAIN_APPLICATION"
echo "APPFORGE_EXPO_MAIN_APPLICATION=PASS"

rm -rf "$SOURCE/android"

cp -a \
  "$PREBUILD/android" \
  "$SOURCE/android"

cp \
  "$PREBUILD/package.json" \
  "$SOURCE/package.json"

if [ -f "$PREBUILD/app.json" ]; then
  cp \
    "$PREBUILD/app.json" \
    "$SOURCE/app.json"
fi

cd "$SOURCE"

test -f android/gradle.properties
test -f android/app/build.gradle
test -f android/settings.gradle

echo "APPFORGE_EXPO_ANDROID_COPYBACK=PASS"

set_prop() {
  key="$1"
  value="$2"
  file="android/gradle.properties"

  if grep -q "^${key}=" "$file"; then
    sed -i \
      "s|^${key}=.*|${key}=${value}|" \
      "$file"
  else
    printf '%s=%s\n' \
      "$key" \
      "$value" \
      >> "$file"
  fi
}

set_prop \
  newArchEnabled \
  false

set_prop \
  hermesEnabled \
  false

set_prop \
  reactNativeArchitectures \
  arm64-v8a

grep -q '^newArchEnabled=false$' \
  android/gradle.properties

grep -q '^hermesEnabled=false$' \
  android/gradle.properties

grep -q '^reactNativeArchitectures=arm64-v8a$' \
  android/gradle.properties

echo "APPFORGE_EXPO54_PREBUILD=PASS"
echo "APPFORGE_EXPO_NEW_ARCH=DISABLED"
echo "APPFORGE_EXPO_HERMES=DISABLED"
echo "APPFORGE_EXPO_ABI=arm64-v8a"
