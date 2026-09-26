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

#
# Change Gradle properties while the project is still on the
# rootfs-native staging filesystem. Avoid sed -i / atomic rename
# operations after the project returns to the PRoot bind mount.
#
set_prop_native() {
  key="$1"
  value="$2"
  file="$PREBUILD/android/gradle.properties"
  tmp="$PREBUILD/android/gradle.properties.appforge.$$"

  awk \
    -v key="$key" \
    -v value="$value" \
    '
      BEGIN {
        found = 0
      }

      index($0, key "=") == 1 {
        print key "=" value
        found = 1
        next
      }

      {
        print
      }

      END {
        if (!found) {
          print key "=" value
        }
      }
    ' \
    "$file" \
    > "$tmp"

  cat "$tmp" > "$file"
  rm -f "$tmp"
}

set_prop_native \
  newArchEnabled \
  false

set_prop_native \
  hermesEnabled \
  false

set_prop_native \
  reactNativeArchitectures \
  arm64-v8a

grep -q '^newArchEnabled=false$' \
  "$PREBUILD/android/gradle.properties"

grep -q '^hermesEnabled=false$' \
  "$PREBUILD/android/gradle.properties"

grep -q '^reactNativeArchitectures=arm64-v8a$' \
  "$PREBUILD/android/gradle.properties"

#
# APPFORGE_EXPO_SEARCH_PATHS_V1
#
# Expo prebuild runs on a rootfs-native staging directory while the
# installed npm dependency tree remains under /workspace/source.
#
# Force Expo's supported Android autolinking search path to the final
# project node_modules directory before useExpoModules() resolves the
# native dependency graph.
#
SETTINGS_NATIVE="$PREBUILD/android/settings.gradle"
SETTINGS_TMP="$PREBUILD/android/settings.gradle.appforge.$$"

test -f "$SETTINGS_NATIVE"

grep -q 'expo-autolinking-settings' \
  "$SETTINGS_NATIVE"

grep -q 'expoAutolinking.useExpoModules()' \
  "$SETTINGS_NATIVE"

if ! grep -q 'APPFORGE_EXPO_SEARCH_PATHS_V1' \
  "$SETTINGS_NATIVE"
then
  awk '
    BEGIN {
      inserted = 0
    }

    /expoAutolinking\.useExpoModules\(\)/ && !inserted {
      print "// APPFORGE_EXPO_SEARCH_PATHS_V1"
      print "expoAutolinking.searchPaths = [\"../node_modules\"]"
      inserted = 1
    }

    {
      print
    }

    END {
      if (!inserted) {
        exit 42
      }
    }
  ' \
    "$SETTINGS_NATIVE" \
    > "$SETTINGS_TMP"

  cat "$SETTINGS_TMP" \
    > "$SETTINGS_NATIVE"

  rm -f "$SETTINGS_TMP"
fi

grep -q 'APPFORGE_EXPO_SEARCH_PATHS_V1' \
  "$SETTINGS_NATIVE"

grep -Fq 'expoAutolinking.searchPaths = ["../node_modules"]' \
  "$SETTINGS_NATIVE"

SEARCH_LINE="$(
  grep -n -m1 \
    'expoAutolinking.searchPaths' \
    "$SETTINGS_NATIVE" \
    | cut -d: -f1
)"

USE_LINE="$(
  grep -n -m1 \
    'expoAutolinking.useExpoModules()' \
    "$SETTINGS_NATIVE" \
    | cut -d: -f1
)"

test -n "$SEARCH_LINE"
test -n "$USE_LINE"
test "$SEARCH_LINE" -lt "$USE_LINE"

echo "APPFORGE_EXPO_NATIVE_AUTOLINK_SETTINGS=PASS"

echo "APPFORGE_EXPO_NATIVE_PROPERTIES=PASS"

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

grep -q '^newArchEnabled=false$' \
  android/gradle.properties

grep -q '^hermesEnabled=false$' \
  android/gradle.properties

grep -q '^reactNativeArchitectures=arm64-v8a$' \
  android/gradle.properties

grep -q 'APPFORGE_EXPO_SEARCH_PATHS_V1' \
  android/settings.gradle

grep -Fq 'expoAutolinking.searchPaths = ["../node_modules"]' \
  android/settings.gradle

echo "APPFORGE_EXPO_ANDROID_COPYBACK=PASS"

#
# APPFORGE_EXPO_FINAL_AUTOLINK_RESOLVE_V1
#
# Resolve from the exact final filesystem root that Gradle will use.
# Fail before Kotlin compilation if Expo's own autolinker cannot see
# the native Expo projects.
#
AUTOLINK_JSON="$ROOT/expo-autolinking-final-$$.json"

rm -f "$AUTOLINK_JSON"

(
  cd "$SOURCE/android"

  "$NODE_HOME/bin/node" \
    "$SOURCE/node_modules/expo/bin/autolinking" \
    resolve \
    --platform android \
    --json \
    ../node_modules
) > "$AUTOLINK_JSON"

test -s "$AUTOLINK_JSON"

AUTOLINK_JSON="$AUTOLINK_JSON" \
PREBUILD_ROOT="$PREBUILD_ROOT" \
"$NODE_HOME/bin/node" <<'NODE'
const fs =
  require("fs");

const file =
  process.env.AUTOLINK_JSON;

const prebuildRoot =
  process.env.PREBUILD_ROOT || "";

const payload =
  JSON.parse(
    fs.readFileSync(
      file,
      "utf8"
    )
  );

const modules =
  Array.isArray(
    payload.modules
  )
    ? payload.modules
    : [];

const requireModule =
  (
    packageName,
    marker
  ) => {
    const module =
      modules.find(
        item =>
          item &&
          item.packageName ===
            packageName
      );

    if (!module) {
      throw new Error(
        "Missing Expo autolink module: " +
          packageName
      );
    }

    const projects =
      Array.isArray(
        module.projects
      )
        ? module.projects
        : [];

    const sourceDirs =
      projects
        .map(
          project =>
            String(
              project.sourceDir ||
                ""
            )
        )
        .filter(Boolean);

    if (
      sourceDirs.length ===
        0
    ) {
      throw new Error(
        "Expo autolink module has no Android project: " +
          packageName
      );
    }

    if (
      sourceDirs.some(
        sourceDir =>
          prebuildRoot &&
          sourceDir.includes(
            prebuildRoot
          )
      )
    ) {
      throw new Error(
        "Expo autolink retained deleted prebuild path: " +
          packageName
      );
    }

    if (
      !sourceDirs.some(
        sourceDir =>
          sourceDir.includes(
            "/node_modules/" +
              packageName +
              "/android"
          )
      )
    ) {
      throw new Error(
        "Expo autolink resolved unexpected Android path for: " +
          packageName +
          " -> " +
          sourceDirs.join(",")
      );
    }

    console.log(
      marker + "=PASS"
    );
  };

requireModule(
  "expo",
  "APPFORGE_EXPO_AUTOLINK_EXPO"
);

requireModule(
  "expo-modules-core",
  "APPFORGE_EXPO_AUTOLINK_CORE"
);

console.log(
  "APPFORGE_EXPO_AUTOLINK_MODULE_COUNT=" +
    modules.length
);
NODE

rm -f "$AUTOLINK_JSON"

echo "APPFORGE_EXPO_FINAL_AUTOLINK=PASS"

#
# The copied project is disposable. Do not allow a project-local
# Gradle state directory from prebuild to influence the real build.
#
rm -rf "$SOURCE/android/.gradle"

echo "APPFORGE_EXPO54_PREBUILD=PASS"
echo "APPFORGE_EXPO_NEW_ARCH=DISABLED"
echo "APPFORGE_EXPO_HERMES=DISABLED"
echo "APPFORGE_EXPO_ABI=arm64-v8a"
