#!/bin/sh
set -eu

ROOT="/opt/appforge-device"
NODE_HOME="$ROOT/node-22.23.3"
SOURCE="/workspace/source"
CACHE="$ROOT/npm-cache-expo54-v1"
NPM_CLI="$NODE_HOME/lib/node_modules/npm/bin/npm-cli.js"

export PATH="$NODE_HOME/bin:$PATH"
export NPM_CONFIG_CACHE="$CACHE"
export NPM_CONFIG_UPDATE_NOTIFIER=false
export EXPO_NO_TELEMETRY=1
export CI=1

test -x "$NODE_HOME/bin/node"
test -x "$NODE_HOME/bin/npm"
test -f "$NPM_CLI"
test -f "$SOURCE/package.json"

cd "$SOURCE"

"$NODE_HOME/bin/node" --version
"$NODE_HOME/bin/node" "$NPM_CLI" --version

"$NODE_HOME/bin/node" -e '
  const [major, minor] =
    process.versions.node
      .split(".")
      .map(Number);

  if (
    major !== 22 ||
    minor < 23
  ) {
    process.exit(1);
  }
'

echo "APPFORGE_EXPO_NODE22_EXECUTION=PASS"

if [ "${APPFORGE_DEVICE_OFFLINE:-0}" = "1" ]; then
  echo "APPFORGE_EXPO_NPM_MODE=OFFLINE"

  if [ -f package-lock.json ]; then
    "$NODE_HOME/bin/node" "$NPM_CLI" ci \
      --offline \
      --no-audit \
      --no-fund
  else
    "$NODE_HOME/bin/node" "$NPM_CLI" install \
      --offline \
      --no-audit \
      --no-fund
  fi
else
  echo "APPFORGE_EXPO_NPM_MODE=ONLINE"

  if [ -f package-lock.json ]; then
    "$NODE_HOME/bin/node" "$NPM_CLI" ci \
      --no-audit \
      --no-fund
  else
    "$NODE_HOME/bin/node" "$NPM_CLI" install \
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

"$NODE_HOME/bin/node" \
  "$PREBUILD/node_modules/expo/bin/cli" \
  prebuild \
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
# APPFORGE_EXPO_RUNTIME_CRASH_PROBE_V1
#
# Physical APK/AAB creation is now proven. The remaining acceptance
# blocker is launch/runtime. Instrument only the disposable Expo
# acceptance project so a startup crash produces real evidence instead
# of another source-level hypothesis.
#
MAIN_ACTIVITY="$(
  find \
    android/app/src/main/java \
    -type f \
    \( \
      -name 'MainActivity.kt' \
      -o \
      -name 'MainActivity.java' \
    \) \
    -print \
    | head -n 1
)"

test -n "$MAIN_ACTIVITY"
test -f "$MAIN_ACTIVITY"

case "$MAIN_APPLICATION" in
  *.kt)
    ;;
  *)
    echo "APPFORGE_EXPO_RUNTIME_PROBE_REQUIRES_KOTLIN_MAIN_APPLICATION" >&2
    exit 45
    ;;
esac

case "$MAIN_ACTIVITY" in
  *.kt)
    ;;
  *)
    echo "APPFORGE_EXPO_RUNTIME_PROBE_REQUIRES_KOTLIN_MAIN_ACTIVITY" >&2
    exit 46
    ;;
esac

APPFORGE_EXPO_PACKAGE="$(
  awk '
    /^[[:space:]]*package[[:space:]]+/ {
      print $2
      exit
    }
  ' "$MAIN_APPLICATION" |
  tr -d ';'
)"

test -n "$APPFORGE_EXPO_PACKAGE"

APPFORGE_EXPO_PROBE_FILE="$(
  dirname "$MAIN_APPLICATION"
)/AppForgeExpoRuntimeProbe.kt"

cat > "$APPFORGE_EXPO_PROBE_FILE" <<EOF
package $APPFORGE_EXPO_PACKAGE

object AppForgeExpoRuntimeProbe {
    private const val TAG = "AppForgeExpoProbe"

    @Volatile
    private var installed = false

    @Volatile
    private var reportUri: android.net.Uri? = null

    @Synchronized
    fun install(app: android.app.Application) {
        if (installed) {
            return
        }

        installed = true

        mark(app, "APPLICATION_AFTER_SUPER")

        val bundleState =
            try {
                app.assets.open("index.android.bundle").use { input ->
                    if (input.read() >= 0) {
                        "PASS"
                    } else {
                        "EMPTY"
                    }
                }
            } catch (error: Throwable) {
                "FAIL:" +
                    error.javaClass.name +
                    ":" +
                    (error.message ?: "")
            }

        write(
            app,
            "BUNDLE_ASSET=index.android.bundle:" + bundleState
        )

        val nativeLibraries =
            try {
                java.io.File(
                    app.applicationInfo.nativeLibraryDir
                )
                    .listFiles()
                    ?.filter { it.isFile }
                    ?.map { it.name }
                    ?.sorted()
                    ?.joinToString(",")
                    ?: "NONE"
            } catch (error: Throwable) {
                "ERROR:" +
                    error.javaClass.name +
                    ":" +
                    (error.message ?: "")
            }

        write(
            app,
            "NATIVE_LIB_DIR=" +
                app.applicationInfo.nativeLibraryDir
        )

        write(
            app,
            "NATIVE_LIBS=" + nativeLibraries
        )

        write(
            app,
            "ANDROID_SDK=" +
                android.os.Build.VERSION.SDK_INT
        )

        write(
            app,
            "SUPPORTED_ABIS=" +
                android.os.Build.SUPPORTED_ABIS.joinToString(",")
        )

        val previous =
            Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler {
                thread,
                throwable ->

            try {
                write(
                    app,
                    "UNCAUGHT_THREAD=" + thread.name
                )

                write(
                    app,
                    "UNCAUGHT_CLASS=" +
                        throwable.javaClass.name
                )

                write(
                    app,
                    "UNCAUGHT_MESSAGE=" +
                        (throwable.message ?: "")
                )

                write(
                    app,
                    "STACKTRACE_BEGIN\n" +
                        android.util.Log.getStackTraceString(
                            throwable
                        ) +
                        "\nSTACKTRACE_END"
                )
            } catch (_: Throwable) {
            }

            if (previous != null) {
                previous.uncaughtException(
                    thread,
                    throwable
                )
            } else {
                android.os.Process.killProcess(
                    android.os.Process.myPid()
                )
            }
        }

        write(
            app,
            "UNCAUGHT_HANDLER=INSTALLED"
        )
    }

    fun mark(
        context: android.content.Context,
        stage: String
    ) {
        val appContext =
            context.applicationContext

        write(
            appContext,
            "STAGE=" + stage
        )

        if (stage == "APPLICATION_READY") {
            val hermesFactoryState =
                try {
                    Class.forName(
                        "com.facebook.hermes.reactexecutor.HermesExecutorFactory"
                    )

                    "PASS"
                } catch (error: Throwable) {
                    "FAIL:" +
                        error.javaClass.name +
                        ":" +
                        (error.message ?: "")
                }

            write(
                appContext,
                "HERMES_FACTORY_CLASS=" +
                    hermesFactoryState
            )

            val hermesNativeState =
                try {
                    val clazz =
                        Class.forName(
                            "com.facebook.hermes.reactexecutor.HermesExecutor"
                        )

                    val method =
                        clazz.getDeclaredMethod(
                            "loadLibrary"
                        )

                    method.isAccessible = true
                    method.invoke(null)

                    "PASS"
                } catch (error: Throwable) {
                    val root =
                        if (
                            error is
                                java.lang.reflect.InvocationTargetException &&
                            error.targetException != null
                        ) {
                            error.targetException
                        } else {
                            error
                        }

                    "FAIL:" +
                        root.javaClass.name +
                        ":" +
                        (root.message ?: "")
                }

            write(
                appContext,
                "HERMES_NATIVE_LOAD=" +
                    hermesNativeState
            )
        }
    }

    @Synchronized
    private fun ensureReport(
        context: android.content.Context
    ): android.net.Uri? {
        reportUri?.let {
            return it
        }

        if (
            android.os.Build.VERSION.SDK_INT <
            android.os.Build.VERSION_CODES.Q
        ) {
            android.util.Log.e(
                TAG,
                "PUBLIC_REPORT_REQUIRES_API_29"
            )

            return null
        }

        return try {
            val values =
                android.content.ContentValues().apply {
                    put(
                        android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                        "AppForgeExpoRuntime-" +
                            System.currentTimeMillis() +
                            ".txt"
                    )

                    put(
                        android.provider.MediaStore.MediaColumns.MIME_TYPE,
                        "text/plain"
                    )

                    put(
                        android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS +
                            "/AppForgeStudio/ExpoCrash"
                    )
                }

            context.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            )?.also {
                reportUri = it
            }
        } catch (error: Throwable) {
            android.util.Log.e(
                TAG,
                "REPORT_CREATE_FAILED",
                error
            )

            null
        }
    }

    @Synchronized
    private fun write(
        context: android.content.Context,
        message: String
    ) {
        android.util.Log.e(
            TAG,
            message
        )

        try {
            val uri =
                ensureReport(context)
                    ?: return

            context.contentResolver
                .openOutputStream(
                    uri,
                    "wa"
                )
                ?.bufferedWriter()
                ?.use { writer ->
                    writer.append(
                        System.currentTimeMillis()
                            .toString()
                    )

                    writer.append(" ")
                    writer.append(message)
                    writer.append("\n")
                }
        } catch (error: Throwable) {
            android.util.Log.e(
                TAG,
                "REPORT_WRITE_FAILED",
                error
            )
        }
    }
}
EOF

test -s "$APPFORGE_EXPO_PROBE_FILE"

MAIN_APPLICATION="$MAIN_APPLICATION" \
MAIN_ACTIVITY="$MAIN_ACTIVITY" \
"$NODE_HOME/bin/node" <<'NODE'
const fs = require("fs");

const applicationPath =
  process.env.MAIN_APPLICATION;

const activityPath =
  process.env.MAIN_ACTIVITY;

let application =
  fs.readFileSync(
    applicationPath,
    "utf8"
  );

if (
  !application.includes(
    "AppForgeExpoRuntimeProbe.install(this)"
  )
) {
  const superPattern =
    /^(\s*)super\.onCreate\(\)\s*$/m;

  if (!superPattern.test(application)) {
    throw new Error(
      "MainApplication super.onCreate anchor missing"
    );
  }

  application =
    application.replace(
      superPattern,
      (line, indent) =>
        line +
        "\n" +
        indent +
        "AppForgeExpoRuntimeProbe.install(this)"
    );
}

if (
  !application.includes(
    "APPFORGE_EXPO_HERMES_RUNTIME_NO_HOST_AOT_V1"
  )
) {
  const hermesProperty =
    /^(\s*)override\s+val\s+isHermesEnabled\s*:\s*Boolean\s*=\s*BuildConfig\.IS_HERMES_ENABLED\s*$/m;

  const newArchProperty =
    /^(\s*)override\s+val\s+isNewArchEnabled\s*:\s*Boolean\s*=\s*BuildConfig\.IS_NEW_ARCHITECTURE_ENABLED\s*$/m;

  if (hermesProperty.test(application)) {
    application =
      application.replace(
        hermesProperty,
        (line, indent) =>
          indent +
          "// APPFORGE_EXPO_HERMES_RUNTIME_NO_HOST_AOT_V1\n" +
          indent +
          "override val isHermesEnabled: Boolean = true"
      );
  } else if (newArchProperty.test(application)) {
    application =
      application.replace(
        newArchProperty,
        (line, indent) =>
          line +
          "\n" +
          indent +
          "// APPFORGE_EXPO_HERMES_RUNTIME_NO_HOST_AOT_V1\n" +
          indent +
          "override val isHermesEnabled: Boolean = true"
      );
  } else {
    throw new Error(
      "MainApplication Hermes/new-arch anchor missing"
    );
  }
}

if (
  application.includes(
    "ApplicationLifecycleDispatcher.onApplicationCreate(this)"
  ) &&
  !application.includes(
    'AppForgeExpoRuntimeProbe.mark(this, "APPLICATION_READY")'
  )
) {
  const readyPattern =
    /^(\s*)ApplicationLifecycleDispatcher\.onApplicationCreate\(this\)\s*$/m;

  if (readyPattern.test(application)) {
    application =
      application.replace(
        readyPattern,
        (line, indent) =>
          line +
          "\n" +
          indent +
          'AppForgeExpoRuntimeProbe.mark(this, "APPLICATION_READY")'
      );
  }
}

fs.writeFileSync(
  applicationPath,
  application
);

let activity =
  fs.readFileSync(
    activityPath,
    "utf8"
  );

if (
  !activity.includes(
    'AppForgeExpoRuntimeProbe.mark(this, "MAIN_ACTIVITY_AFTER_SUPER")'
  )
) {
  const activitySuperPattern =
    /^(\s*)super\.onCreate\([^)]*\)\s*$/m;

  if (!activitySuperPattern.test(activity)) {
    throw new Error(
      "MainActivity super.onCreate anchor missing"
    );
  }

  activity =
    activity.replace(
      activitySuperPattern,
      (line, indent) =>
        line +
        "\n" +
        indent +
        'AppForgeExpoRuntimeProbe.mark(this, "MAIN_ACTIVITY_AFTER_SUPER")'
    );
}

fs.writeFileSync(
  activityPath,
  activity
);
NODE

grep -q \
  'AppForgeExpoRuntimeProbe.install(this)' \
  "$MAIN_APPLICATION"

grep -q \
  'MAIN_ACTIVITY_AFTER_SUPER' \
  "$MAIN_ACTIVITY"

grep -q \
  'MediaStore.Downloads.EXTERNAL_CONTENT_URI' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -q \
  'STACKTRACE_BEGIN' \
  "$APPFORGE_EXPO_PROBE_FILE"

echo "APPFORGE_EXPO_RUNTIME_PROBE_FILE=$APPFORGE_EXPO_PROBE_FILE"
echo "APPFORGE_EXPO_RUNTIME_PROBE_REPORT=Downloads/AppForgeStudio/ExpoCrash"
echo "APPFORGE_EXPO_RUNTIME_CRASH_PROBE=PASS"

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
# APPFORGE_EXPO_SEARCH_PATHS_V2
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

if ! grep -q 'APPFORGE_EXPO_SEARCH_PATHS_V2' \
  "$SETTINGS_NATIVE"
then
  awk '
    BEGIN {
      inserted = 0
    }

    /expoAutolinking\.useExpoModules\(\)/ && !inserted {
      print "// APPFORGE_EXPO_SEARCH_PATHS_V2"
      print "expoAutolinking.searchPaths = [\"../node_modules/expo\", \"../node_modules/expo-modules-core\"]"
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

grep -q 'APPFORGE_EXPO_SEARCH_PATHS_V2' \
  "$SETTINGS_NATIVE"

grep -Fq 'expoAutolinking.searchPaths = ["../node_modules/expo", "../node_modules/expo-modules-core"]' \
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
echo "APPFORGE_EXPO_AUTOLINK_SEARCH_MODE=EXACT_MODULES"

#
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

grep -q 'APPFORGE_EXPO_SEARCH_PATHS_V2' \
  android/settings.gradle

grep -Fq 'expoAutolinking.searchPaths = ["../node_modules/expo", "../node_modules/expo-modules-core"]' \
  android/settings.gradle

echo "APPFORGE_EXPO_ANDROID_COPYBACK=PASS"

#
# APPFORGE_EXPO_FINAL_AUTOLINK_RESOLVE_V2
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
    ../node_modules/expo \
    ../node_modules/expo-modules-core
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
      packageName === "expo" &&
      !projects.some(
        project =>
          String(
            project.name || ""
          ) === "expo"
      )
    ) {
      throw new Error(
        "Expo autolink did not expose Gradle project :expo."
      );
    }

    if (
      packageName === "expo"
    ) {
      console.log(
        "APPFORGE_EXPO_GRADLE_PROJECT_EXPO=PASS"
      );
    }

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
# APPFORGE_EXPO_APP_CLASSPATH_AUTHORITY_V2
#
# Expo SDK 54 has already resolved the native Expo Gradle projects
# through useExpoModules(). That resolver is authoritative for Expo
# modules. Do not require the React Native config command to expose
# Expo a second time.
#
echo "APPFORGE_EXPO_APP_CLASSPATH_AUTHORITY=EXPO_MODULE_RESOLVER"

#
# APPFORGE_EXPO_APP_CLASSPATH_BRIDGE_V1
#
# Expo SDK 54 normally reaches the app through React Native
# autolinkLibrariesWithApp(). On AppForge's PRoot workspace the Expo
# project is discovered correctly but did not reach debugCompileClasspath
# on the physical device. The generated Android tree is disposable, so
# add the exact :expo project dependency as an acceptance-only fallback.
#
APP_GRADLE="$SOURCE/android/app/build.gradle"
APP_GRADLE_TMP="$SOURCE/android/app/build.gradle.appforge.$$"

test -f "$APP_GRADLE"

grep -q 'autolinkLibrariesWithApp()' \
  "$APP_GRADLE"

if ! grep -q 'APPFORGE_EXPO_APP_CLASSPATH_BRIDGE_V1' \
  "$APP_GRADLE"
then
  awk '
    BEGIN {
      inserted = 0
    }

    /^[[:space:]]*dependencies[[:space:]]*\{/ && !inserted {
      print
      print "    // APPFORGE_EXPO_APP_CLASSPATH_BRIDGE_V1"
      print "    implementation(project(\":expo\"))"
      inserted = 1
      next
    }

    {
      print
    }

    END {
      if (!inserted) {
        exit 43
      }
    }
  ' \
    "$APP_GRADLE" \
    > "$APP_GRADLE_TMP"

  cat "$APP_GRADLE_TMP" \
    > "$APP_GRADLE"

  rm -f "$APP_GRADLE_TMP"
fi

grep -q 'APPFORGE_EXPO_APP_CLASSPATH_BRIDGE_V1' \
  "$APP_GRADLE"

grep -Fq 'implementation(project(":expo"))' \
  "$APP_GRADLE"

echo "APPFORGE_EXPO_APP_CLASSPATH_BRIDGE=PASS"

#
# APPFORGE_EXPO_HERMES_RUNTIME_DEPENDENCY_V1
#
# React Native 0.81 no longer provides the previous first-party JSC
# runtime path used by older legacy-architecture builds. AppForge keeps
# Gradle hermesEnabled=false to avoid executing the host hermesc binary
# on ARM64 Android, but explicitly packages the Hermes Android runtime.
#
if ! grep -q \
  'APPFORGE_EXPO_HERMES_RUNTIME_DEPENDENCY_V1' \
  "$APP_GRADLE"
then
  cat >> "$APP_GRADLE" <<'EOF'

// APPFORGE_EXPO_HERMES_RUNTIME_DEPENDENCY_V1
dependencies {
    implementation("com.facebook.react:hermes-android")
}
EOF
fi

grep -q \
  'APPFORGE_EXPO_HERMES_RUNTIME_DEPENDENCY_V1' \
  "$APP_GRADLE"

grep -Fq \
  'implementation("com.facebook.react:hermes-android")' \
  "$APP_GRADLE"

grep -q '^hermesEnabled=false$' \
  "$SOURCE/android/gradle.properties"

grep -q \
  'APPFORGE_EXPO_HERMES_RUNTIME_NO_HOST_AOT_V1' \
  "$MAIN_APPLICATION"

grep -Fq \
  'override val isHermesEnabled: Boolean = true' \
  "$MAIN_APPLICATION"

echo "APPFORGE_EXPO_HERMES_BUILD_AOT=DISABLED"
echo "APPFORGE_EXPO_HERMES_RUNTIME=ENABLED"
echo "APPFORGE_EXPO_HERMES_RUNTIME_DEPENDENCY=PASS"

#
# APPFORGE_EXPO_STANDALONE_ACCEPTANCE_V1
#
# React Native treats normal Debug as a Metro/developer variant and
# does not package the JS bundle into it. AppForge physical acceptance
# must run after installation with no Metro server.
#
# Create a disposable non-debuggable acceptance build type which:
# - inherits the already-proven Debug native configuration
# - uses the generated debug signing key
# - keeps minification disabled
# - is NOT in React Native debuggableVariants
# - therefore receives an embedded JS/assets bundle.
#
if ! grep -q \
  'APPFORGE_EXPO_STANDALONE_ACCEPTANCE_V1' \
  "$APP_GRADLE"
then
  cat >> "$APP_GRADLE" <<'EOF'

// APPFORGE_EXPO_STANDALONE_ACCEPTANCE_V1
android {
    buildTypes {
        appforgeAcceptance {
            initWith debug
            debuggable false
            minifyEnabled false
            signingConfig signingConfigs.debug
            matchingFallbacks = ["debug", "release"]
        }
    }
}

react {
    debuggableVariants = ["debug"]
    nodeExecutableAndArgs = ["/opt/appforge-device/node-22.23.3/bin/node"]
}
EOF
fi

grep -q \
  'APPFORGE_EXPO_STANDALONE_ACCEPTANCE_V1' \
  "$APP_GRADLE"

grep -Fq \
  'appforgeAcceptance {' \
  "$APP_GRADLE"

grep -Fq \
  'debuggable false' \
  "$APP_GRADLE"

grep -Fq \
  'signingConfig signingConfigs.debug' \
  "$APP_GRADLE"

grep -Fq \
  'debuggableVariants = ["debug"]' \
  "$APP_GRADLE"

if grep -E \
  'debuggableVariants.*appforgeAcceptance' \
  "$APP_GRADLE"
then
  echo "APPFORGE_EXPO_ACCEPTANCE_WRONGLY_DEBUGGABLE" >&2
  exit 44
fi

echo "APPFORGE_EXPO_STANDALONE_BUILD_TYPE=appforgeAcceptance"
echo "APPFORGE_EXPO_STANDALONE_BUNDLE=EMBEDDED"
echo "APPFORGE_EXPO_STANDALONE_ACCEPTANCE=PASS"

#
# APPFORGE_EXPO_BUNDLE_PREFLIGHT_V1
#
# Run the same Expo production bundler explicitly before Gradle.
# This proves entry resolution, Expo CLI resolution, Metro traversal,
# JS transformation and asset export while preserving the real error
# output if Node exits non-zero.
#
APPFORGE_EXPO_BUNDLE_PROBE_ROOT="$ROOT/expo-bundle-probe"
APPFORGE_EXPO_BUNDLE_PROBE="$APPFORGE_EXPO_BUNDLE_PROBE_ROOT/$$"
APPFORGE_EXPO_BUNDLE_LOG="$APPFORGE_EXPO_BUNDLE_PROBE/export-embed.log"

rm -rf "$APPFORGE_EXPO_BUNDLE_PROBE"
mkdir -p "$APPFORGE_EXPO_BUNDLE_PROBE/assets"

APPFORGE_EXPO_BUNDLE_ENTRY="$(
  cd "$SOURCE"

  "$NODE_HOME/bin/node" \
    -e "require('expo/scripts/resolveAppEntry')" \
    "$SOURCE" \
    android \
    absolute |
  tail -n 1
)"

APPFORGE_EXPO_BUNDLE_CLI="$(
  cd "$SOURCE"

  "$NODE_HOME/bin/node" \
    --print \
    "require.resolve('@expo/cli', { paths: [require.resolve('expo/package.json')] })" |
  tail -n 1
)"

test -n "$APPFORGE_EXPO_BUNDLE_ENTRY"
test -f "$APPFORGE_EXPO_BUNDLE_ENTRY"

test -n "$APPFORGE_EXPO_BUNDLE_CLI"
test -f "$APPFORGE_EXPO_BUNDLE_CLI"

echo "APPFORGE_EXPO_BUNDLE_ENTRY=$APPFORGE_EXPO_BUNDLE_ENTRY"
echo "APPFORGE_EXPO_BUNDLE_CLI=$APPFORGE_EXPO_BUNDLE_CLI"
echo "APPFORGE_EXPO_BUNDLE_NODE=$NODE_HOME/bin/node"
echo "APPFORGE_EXPO_BUNDLE_NODE_ENV=production"

set +e

(
  cd "$SOURCE"

  NODE_ENV=production \
  CI=1 \
  EXPO_NO_TELEMETRY=1 \
  "$NODE_HOME/bin/node" \
    "$APPFORGE_EXPO_BUNDLE_CLI" \
    export:embed \
    --platform android \
    --dev false \
    --reset-cache \
    --entry-file "$APPFORGE_EXPO_BUNDLE_ENTRY" \
    --bundle-output "$APPFORGE_EXPO_BUNDLE_PROBE/index.android.bundle" \
    --assets-dest "$APPFORGE_EXPO_BUNDLE_PROBE/assets" \
    --minify false
) > "$APPFORGE_EXPO_BUNDLE_LOG" 2>&1

APPFORGE_EXPO_BUNDLE_RC=$?

set -e

echo "APPFORGE_EXPO_BUNDLE_PROBE_RC=$APPFORGE_EXPO_BUNDLE_RC"

if [ "$APPFORGE_EXPO_BUNDLE_RC" -ne 0 ]; then
  echo "APPFORGE_EXPO_BUNDLE_PREFLIGHT=FAIL"
  echo "APPFORGE_EXPO_BUNDLE_LOG_BEGIN"

  sed \
    's#/opt/appforge-device#[DEVICE]#g; s#/workspace/source#[SOURCE]#g' \
    "$APPFORGE_EXPO_BUNDLE_LOG" |
  tail -n 160

  echo "APPFORGE_EXPO_BUNDLE_LOG_END"
  exit 45
fi

test -s \
  "$APPFORGE_EXPO_BUNDLE_PROBE/index.android.bundle"

echo "APPFORGE_EXPO_BUNDLE_SIZE=$(
  wc -c < "$APPFORGE_EXPO_BUNDLE_PROBE/index.android.bundle" |
  tr -d ' '
)"

echo "APPFORGE_EXPO_BUNDLE_PREFLIGHT=PASS"

rm -rf "$APPFORGE_EXPO_BUNDLE_PROBE"

#
# APPFORGE_EXPO_NATIVE_MODULE_BUILD_DIR_V1
#
# Keep :app on /workspace so the existing AppForge artifact collector
# can still find APK/AAB output. Native Expo module intermediates are
# redirected to the rootfs-native filesystem because AGP/Prefab creates
# executable helper files there during CMake configuration.
#
EXPO_NATIVE_BUILD_PARENT="$ROOT/expo-native-gradle-work"
EXPO_NATIVE_BUILD_ROOT="$EXPO_NATIVE_BUILD_PARENT/$$"

mkdir -p "$EXPO_NATIVE_BUILD_PARENT"

# Acceptance runs use unique process-owned directories. Clean only
# abandoned older runs; never delete another fresh concurrent run.
find "$EXPO_NATIVE_BUILD_PARENT" \
  -mindepth 1 \
  -maxdepth 1 \
  -type d \
  -mtime +1 \
  -exec rm -rf {} + \
  2>/dev/null \
  || true

rm -rf "$EXPO_NATIVE_BUILD_ROOT"
mkdir -p "$EXPO_NATIVE_BUILD_ROOT"
chmod 0755 "$EXPO_NATIVE_BUILD_ROOT"

#
# APPFORGE_EXPO_PREFAB_EXECUTION_PROOF_V1
#
# Record the AGP version actually loaded by Gradle and create a tiny
# Java ProcessBuilder probe. After a failed native configure task the
# exact generated prefab_command is tested with Java direct execution
# and explicit /bin/sh interpretation.
#
AGP_RUNTIME_PROBE="$EXPO_NATIVE_BUILD_ROOT/appforge-agp-runtime.init.gradle"
AGP_RUNTIME_VERSION_FILE="$EXPO_NATIVE_BUILD_ROOT/appforge-agp-runtime-version.txt"
JAVA_PROCESS_PROBE="$EXPO_NATIVE_BUILD_ROOT/AppForgeProcessProbe.java"

cat > "$AGP_RUNTIME_PROBE" <<EOF
gradle.projectsEvaluated {
    def result =
        "UNKNOWN"

    gradle.rootProject.allprojects.each { project ->
        if (result == "UNKNOWN") {
            def plugin =
                project.plugins.findPlugin(
                    "com.android.application"
                )

            if (plugin == null) {
                plugin =
                    project.plugins.findPlugin(
                        "com.android.library"
                    )
            }

            if (plugin != null) {
                try {
                    def versionClass =
                        plugin.class.classLoader.loadClass(
                            "com.android.Version"
                        )

                    def value =
                        versionClass
                            .getField(
                                "ANDROID_GRADLE_PLUGIN_VERSION"
                            )
                            .get(null)

                    if (value != null) {
                        result =
                            value.toString()
                    }
                } catch (Throwable ignored) {
                    def fallback =
                        plugin.class
                            .package
                            ?.implementationVersion

                    if (fallback != null) {
                        result =
                            fallback.toString()
                    }
                }
            }
        }
    }

    println(
        "APPFORGE_EXPO_AGP_RUNTIME_VERSION=" +
        result
    )

    new File(
        "${AGP_RUNTIME_VERSION_FILE}"
    ).text =
        result +
        System.lineSeparator()
}
EOF

cat > "$JAVA_PROCESS_PROBE" <<'JAVA'
import java.io.File;

public final class AppForgeProcessProbe {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println(
                "APPFORGE_EXPO_JAVA_PREFAB_START=INVALID_ARGS"
            );
            System.exit(64);
        }

        try {
            ProcessBuilder builder =
                new ProcessBuilder(
                    args[0]
                );

            builder.directory(
                new File(
                    args[1]
                )
            );

            builder.redirectOutput(
                ProcessBuilder.Redirect.DISCARD
            );

            builder.redirectError(
                ProcessBuilder.Redirect.DISCARD
            );

            Process process =
                builder.start();

            int rc =
                process.waitFor();

            System.out.println(
                "APPFORGE_EXPO_JAVA_PREFAB_START=PASS"
            );

            System.out.println(
                "APPFORGE_EXPO_JAVA_PREFAB_RC=" +
                rc
            );

            System.exit(0);
        } catch (Throwable error) {
            String message =
                String.valueOf(
                    error.getMessage()
                )
                .replace('\n', ' ')
                .replace('\r', ' ');

            if (message.length() > 240) {
                message =
                    message.substring(
                        0,
                        240
                    );
            }

            System.out.println(
                "APPFORGE_EXPO_JAVA_PREFAB_START=FAIL"
            );

            System.out.println(
                "APPFORGE_EXPO_JAVA_PREFAB_EXCEPTION=" +
                error.getClass().getName()
            );

            System.out.println(
                "APPFORGE_EXPO_JAVA_PREFAB_MESSAGE=" +
                message
            );

            System.exit(66);
        }
    }
}
JAVA

test -s "$AGP_RUNTIME_PROBE"
test -s "$JAVA_PROCESS_PROBE"

echo "APPFORGE_EXPO_AGP_RUNTIME_PROBE=READY"
echo "APPFORGE_EXPO_JAVA_PROCESS_PROBE=READY"

#
# APPFORGE_EXPO_PREFAB_EXEC_SECURITY_SHIM_V1
#
# Physical proof established:
# - actual AGP is 8.11.0
# - Java direct execution of prefab_command fails
# - /bin/sh prefab_command succeeds with rc=0
#
# Install a narrowly scoped Java 17 SecurityManager hook inside the
# Gradle JVM. ProcessBuilder calls checkExec immediately before native
# process launch. For the exact AGP-generated prefab_command under the
# AppForge-owned native build root, prepend a POSIX shell shebang.
#
PREFAB_EXEC_SHIM="$EXPO_NATIVE_BUILD_ROOT/appforge-prefab-exec-shim.init.gradle"

cat > "$PREFAB_EXEC_SHIM" <<'GROOVY'
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.Permission

class AppForgePrefabExecSecurityManager extends SecurityManager {
    private final SecurityManager delegateManager
    private final String allowedRoot

    AppForgePrefabExecSecurityManager(
        SecurityManager delegateManager,
        String allowedRoot
    ) {
        this.delegateManager =
            delegateManager

        this.allowedRoot =
            new File(
                allowedRoot
            ).canonicalPath
    }

    @Override
    void checkPermission(
        Permission permission
    ) {
        if (
            delegateManager !=
                null
        ) {
            delegateManager
                .checkPermission(
                    permission
                )
        }
    }

    @Override
    void checkPermission(
        Permission permission,
        Object context
    ) {
        if (
            delegateManager !=
                null
        ) {
            delegateManager
                .checkPermission(
                    permission,
                    context
                )
        }
    }

    @Override
    void checkExec(
        String command
    ) {
        patchPrefabCommand(
            command
        )

        if (
            delegateManager !=
                null
        ) {
            delegateManager
                .checkExec(
                    command
                )
        }
    }

    private void patchPrefabCommand(
        String command
    ) {
        if (
            command ==
                null
        ) {
            return
        }

        File target =
            new File(
                command
            )

        if (
            target.name !=
                "prefab_command"
        ) {
            return
        }

        String canonical =
            target.canonicalPath

        String rootPrefix =
            allowedRoot +
            File.separator

        if (
            !canonical.startsWith(
                rootPrefix
            )
        ) {
            return
        }

        if (
            !target.isFile()
        ) {
            throw new SecurityException(
                "AppForge Prefab command disappeared before execution."
            )
        }

        byte[] original =
            Files.readAllBytes(
                target.toPath()
            )

        if (
            original.length >= 2 &&
            original[0] == (byte) '#' &&
            original[1] == (byte) '!'
        ) {
            println(
                "APPFORGE_EXPO_PREFAB_EXEC_SHIM=ALREADY_PATCHED"
            )

            return
        }

        String prefix =
            new String(
                original,
                0,
                Math.min(
                    original.length,
                    256
                ),
                StandardCharsets.UTF_8
            )

        if (
            !prefix.startsWith(
                "/opt/appforge-device/jdk-17/bin/java"
            )
        ) {
            throw new SecurityException(
                "AppForge refused unexpected prefab_command content."
            )
        }

        byte[] shebang =
            "#!/bin/sh\n"
                .getBytes(
                    StandardCharsets.UTF_8
                )

        byte[] patched =
            new byte[
                shebang.length +
                original.length
            ]

        System.arraycopy(
            shebang,
            0,
            patched,
            0,
            shebang.length
        )

        System.arraycopy(
            original,
            0,
            patched,
            shebang.length,
            original.length
        )

        Files.write(
            target.toPath(),
            patched
        )

        println(
            "APPFORGE_EXPO_PREFAB_EXEC_SHIM=PATCHED"
        )

        println(
            "APPFORGE_EXPO_PREFAB_EXEC_SHIM_PATH=" +
            canonical
        )
    }
}

def appforgePrefabRoot =
    System.getenv(
        "APPFORGE_EXPO_NATIVE_ROOT"
    )

if (
    appforgePrefabRoot ==
        null ||
    appforgePrefabRoot
        .trim()
        .isEmpty()
) {
    throw new GradleException(
        "APPFORGE_EXPO_NATIVE_ROOT is missing."
    )
}

def previousSecurityManager =
    System.getSecurityManager()

if (
    !(
        previousSecurityManager
            instanceof
        AppForgePrefabExecSecurityManager
    )
) {
    System.setSecurityManager(
        new AppForgePrefabExecSecurityManager(
            previousSecurityManager,
            appforgePrefabRoot
        )
    )
}

println(
    "APPFORGE_EXPO_PREFAB_EXEC_SHIM=ARMED"
)
GROOVY

test -s "$PREFAB_EXEC_SHIM"

echo "APPFORGE_EXPO_PREFAB_EXEC_SHIM_FILE=READY"

ROOT_GRADLE="$SOURCE/android/build.gradle"

test -f "$ROOT_GRADLE"

if ! grep -q \
  'APPFORGE_EXPO_NATIVE_MODULE_BUILD_DIR_V1' \
  "$ROOT_GRADLE"
then
  cat >> "$ROOT_GRADLE" <<EOF

// APPFORGE_EXPO_NATIVE_MODULE_BUILD_DIR_V1
subprojects { subproject ->
    if (subproject.path != ":app") {
        def appforgeSafeProjectPath =
            subproject.path.replace(":", "_")

        subproject.buildDir =
            new File(
                "${EXPO_NATIVE_BUILD_ROOT}/" +
                appforgeSafeProjectPath
            )
    }
}
EOF
fi

grep -q \
  'APPFORGE_EXPO_NATIVE_MODULE_BUILD_DIR_V1' \
  "$ROOT_GRADLE"

grep -Fq \
  'subproject.path != ":app"' \
  "$ROOT_GRADLE"

grep -Fq \
  'subproject.buildDir' \
  "$ROOT_GRADLE"

echo "APPFORGE_EXPO_NATIVE_BUILD_ROOT=$EXPO_NATIVE_BUILD_ROOT"
echo "APPFORGE_EXPO_NATIVE_MODULE_BUILD_DIR=PASS"

#
# APPFORGE_EXPO_NATIVE_CXX_STAGING_V2
#
# Gradle project buildDir and AGP externalNativeBuild staging are
# separate locations. AGP otherwise keeps CMake state under
# <module>/.cxx, which is still /workspace-backed in AppForge.
#
# Force CMake/Ninja configure state, try_compile scratch projects,
# Prefab staging and native metadata onto the rootfs-native filesystem.
#
if ! grep -q \
  'APPFORGE_EXPO_NATIVE_CXX_STAGING_V2' \
  "$ROOT_GRADLE"
then
  cat >> "$ROOT_GRADLE" <<EOF

// APPFORGE_EXPO_NATIVE_CXX_STAGING_V2
subprojects { subproject ->
    if (subproject.path != ":app") {
        def appforgeCxxSafeProjectPath =
            subproject.path.replace(":", "_")

        def appforgeCxxRoot =
            new File(
                "${EXPO_NATIVE_BUILD_ROOT}/cxx/" +
                appforgeCxxSafeProjectPath
            )

        def appforgeConfigureCxxStaging = {
            appforgeCxxRoot.mkdirs()

            def appforgeAndroid =
                subproject.extensions.findByName(
                    "android"
                )

            if (appforgeAndroid != null) {
                def appforgeExternalNativeBuild =
                    appforgeAndroid.externalNativeBuild

                if (
                    appforgeExternalNativeBuild != null &&
                    appforgeExternalNativeBuild.cmake != null
                ) {
                    appforgeExternalNativeBuild
                        .cmake
                        .buildStagingDirectory =
                            appforgeCxxRoot

                    println(
                        "APPFORGE_EXPO_CXX_STAGING=" +
                        subproject.path +
                        ":" +
                        appforgeCxxRoot.absolutePath
                    )
                }
            }
        }

        subproject.plugins.withId(
            "com.android.library"
        ) {
            appforgeConfigureCxxStaging()
        }

        subproject.plugins.withId(
            "com.android.application"
        ) {
            appforgeConfigureCxxStaging()
        }
    }
}
EOF
fi

grep -q \
  'APPFORGE_EXPO_NATIVE_CXX_STAGING_V2' \
  "$ROOT_GRADLE"

grep -Fq \
  'buildStagingDirectory' \
  "$ROOT_GRADLE"

grep -Fq \
  "${EXPO_NATIVE_BUILD_ROOT}/cxx/" \
  "$ROOT_GRADLE"

mkdir -p \
  "$EXPO_NATIVE_BUILD_ROOT/cxx"

chmod 0755 \
  "$EXPO_NATIVE_BUILD_ROOT/cxx"

echo "APPFORGE_EXPO_NATIVE_CXX_ROOT=$EXPO_NATIVE_BUILD_ROOT/cxx"
echo "APPFORGE_EXPO_NATIVE_CXX_STAGING=PASS"

#
# The copied project is disposable. Do not allow a project-local
# Gradle state directory from prebuild to influence the real build.
#
rm -rf "$SOURCE/android/.gradle"

echo "APPFORGE_EXPO54_PREBUILD=PASS"
echo "APPFORGE_EXPO_NEW_ARCH=DISABLED"
echo "APPFORGE_EXPO_HERMES=DISABLED"
echo "APPFORGE_EXPO_ABI=arm64-v8a"
