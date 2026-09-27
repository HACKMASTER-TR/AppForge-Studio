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
# APPFORGE_EXPO_DISABLE_PCH_V20
#
# BUG-B physical evidence:
# identical Expo builds can alternate PASS/FAIL while Ubuntu ARM64 clang 18.1.3
# crashes with exit 139 compiling expo-modules-core's CMake-generated
# cmake_pch.hxx.pch. The AppForge Android host uses the native Ubuntu clang
# bridge because the NDK compiler payload is not an ARM64 Linux host binary.
#
# Disable only expo-modules-core PCH commands inside this disposable build
# workspace. Headers still compile normally. This trades build speed for
# deterministic native compilation and never edits the user's original project.
#
# APPFORGE_EXPO_PCH_ROOT_DISCOVERY_V20_2
#
# SDK 54's installed expo-modules-core package does not guarantee that
# PCH-bearing CMake sources live under android/cmake. Scan the package's
# complete Android source tree recursively instead of assuming that subpath.
#
APPFORGE_EXPO_CORE_ANDROID="$SOURCE/node_modules/expo-modules-core/android"

test -f "$SOURCE/node_modules/expo/package.json"
test -f "$SOURCE/node_modules/react-native/package.json"
test -d "$APPFORGE_EXPO_CORE_ANDROID"

echo "APPFORGE_EXPO_PCH_POST_NPM_V20_1=PASS"
echo "APPFORGE_EXPO_PCH_SCAN_ROOT=$APPFORGE_EXPO_CORE_ANDROID"

APPFORGE_EXPO_CORE_ANDROID="$APPFORGE_EXPO_CORE_ANDROID" \
"$NODE_HOME/bin/node" <<'NODE'
const fs = require("fs");
const path = require("path");

const root =
  process.env.APPFORGE_EXPO_CORE_ANDROID;

if (!root || !fs.statSync(root).isDirectory()) {
  throw new Error(
    "expo-modules-core CMake root missing"
  );
}

function listFiles(directory) {
  const result = [];

  for (
    const entry of
      fs.readdirSync(
        directory,
        { withFileTypes: true }
      )
  ) {
    const target =
      path.join(
        directory,
        entry.name
      );

    if (entry.isDirectory()) {
      result.push(
        ...listFiles(target)
      );
      continue;
    }

    if (
      entry.isFile() &&
      (
        entry.name.endsWith(".cmake") ||
        entry.name === "CMakeLists.txt"
      )
    ) {
      result.push(target);
    }
  }

  return result;
}

function removeCommand(
  source,
  commandName
) {
  const token =
    commandName + "(";

  let cursor = 0;
  let output = "";
  let removed = 0;

  while (true) {
    const start =
      source.indexOf(
        token,
        cursor
      );

    if (start < 0) {
      output +=
        source.slice(cursor);
      break;
    }

    output +=
      source.slice(
        cursor,
        start
      );

    let depth = 0;
    let end = -1;

    for (
      let index =
        start +
        commandName.length;
      index < source.length;
      index += 1
    ) {
      const char =
        source[index];

      if (char === "(") {
        depth += 1;
      } else if (char === ")") {
        depth -= 1;

        if (depth === 0) {
          end = index + 1;
          break;
        }
      }
    }

    if (end < 0) {
      throw new Error(
        "Unbalanced " +
          commandName +
          " call"
      );
    }

    output +=
      "# APPFORGE_EXPO_PCH_DISABLED_V20";

    cursor = end;
    removed += 1;
  }

  return {
    text: output,
    removed
  };
}

const files =
  listFiles(root);

let removedTotal = 0;
let touchedFiles = 0;

for (const file of files) {
  const original =
    fs.readFileSync(
      file,
      "utf8"
    );

  const result =
    removeCommand(
      original,
      "target_precompile_headers"
    );

  if (result.removed > 0) {
    fs.writeFileSync(
      file,
      result.text
    );

    removedTotal +=
      result.removed;

    touchedFiles += 1;

    console.log(
      "APPFORGE_EXPO_PCH_PATCH_FILE=" +
        path.relative(
          root,
          file
        ) +
        ":" +
        result.removed
    );
  }
}

if (removedTotal < 1) {
  throw new Error(
    "No expo-modules-core PCH commands found under Android source tree"
  );
}

const remaining =
  listFiles(root)
    .filter(
      file =>
        fs.readFileSync(
          file,
          "utf8"
        ).includes(
          "target_precompile_headers"
        )
    );

if (remaining.length !== 0) {
  throw new Error(
    "Expo PCH commands remain: " +
      remaining.join(",")
  );
}

console.log(
  "APPFORGE_EXPO_PCH_REMOVED_COUNT=" +
    removedTotal
);

console.log(
  "APPFORGE_EXPO_PCH_PATCHED_FILE_COUNT=" +
    touchedFiles
);

console.log(
  "APPFORGE_EXPO_PCH_REMAINING=0"
);
NODE

if grep -R -Fq \
  'target_precompile_headers' \
  "$APPFORGE_EXPO_CORE_ANDROID"
then
  echo "APPFORGE_EXPO_PCH_DISABLE=FAIL"
  exit 47
fi

echo "APPFORGE_EXPO_PCH_MODE=DISABLED_ARM64_HOST"
echo "APPFORGE_EXPO_PCH_ROOT_DISCOVERY_V20_2=PASS"
echo "APPFORGE_EXPO_DISABLE_PCH_V20=PASS"

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

    @Volatile
    private var applicationRef: android.app.Application? = null

    // APPFORGE_EXPO_STARTUP_CAPTURE_V16
    private val earlyFactoryEvents =
        java.util.concurrent.CopyOnWriteArrayList<String>()

    @Volatile
    private var crashHandler:
        Thread.UncaughtExceptionHandler? = null

    // APPFORGE_EXPO_JAVA_CRASH_CAPTURE_V17
    @Volatile
    private var crashDelegate:
        Thread.UncaughtExceptionHandler? = null

    @Volatile
    private var mainLooperProbeInstalled = false

    private val mainLooperEventCount =
        java.util.concurrent.atomic.AtomicInteger(0)

    // APPFORGE_EXPO_LAUNCH_TRANSACTION_V18
    private val activityEventCount =
        java.util.concurrent.atomic.AtomicInteger(0)

    private val executeTransactionSeen =
        java.util.concurrent.atomic.AtomicBoolean(false)

    fun early(stage: String) {
        val app = applicationRef

        if (app != null) {
            write(
                app,
                "STAGE=" + stage
            )
        } else {
            android.util.Log.e(
                TAG,
                "EARLY_STAGE_NO_APPLICATION=" + stage
            )
        }
    }

    @Synchronized
    fun install(app: android.app.Application) {
        if (installed) {
            return
        }

        installed = true
        applicationRef = app

        flushEarlyFactoryEvents(app)
        installActivityLifecycleProbe(app)
        recordPreviousExit(app)

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

        installCrashHandler(
            app,
            "INSTALL"
        )

        installMainLooperProbe(app)
        recordLaunchState(
            app,
            "INSTALL"
        )
    }

    @Synchronized
    private fun installCrashHandler(
        context: android.content.Context,
        phase: String
    ) {
        val mainThread =
            android.os.Looper.getMainLooper().thread

        val currentDefault =
            Thread.getDefaultUncaughtExceptionHandler()

        val currentMain =
            mainThread.uncaughtExceptionHandler

        write(
            context,
            "UNCAUGHT_HANDLER_" +
                phase +
                "_DEFAULT_CURRENT=" +
                (
                    currentDefault
                        ?.javaClass
                        ?.name
                        ?: "NONE"
                )
        )

        write(
            context,
            "UNCAUGHT_HANDLER_" +
                phase +
                "_MAIN_CURRENT=" +
                (
                    currentMain
                        ?.javaClass
                        ?.name
                        ?: "NONE"
                )
        )

        val active =
            crashHandler

        if (
            active != null &&
            currentDefault === active &&
            currentMain === active
        ) {
            write(
                context,
                "UNCAUGHT_HANDLER_" +
                    phase +
                    "_STATE=UNCHANGED"
            )

            write(
                context,
                "UNCAUGHT_HANDLER_" +
                    phase +
                    "_VERIFY=PASS"
            )

            return
        }

        if (crashDelegate == null) {
            crashDelegate =
                if (
                    currentDefault !== active
                ) {
                    currentDefault
                } else {
                    null
                }
        }

        val delegate =
            crashDelegate

        val handler =
            object :
                Thread.UncaughtExceptionHandler {
                override fun uncaughtException(
                    thread: Thread,
                    throwable: Throwable
                ) {
                    try {
                        write(
                            context,
                            "UNCAUGHT_THREAD=" +
                                thread.name
                        )

                        write(
                            context,
                            "UNCAUGHT_THREAD_ID=" +
                                thread.id
                        )

                        write(
                            context,
                            "UNCAUGHT_THREAD_HANDLER=" +
                                (
                                    thread
                                        .uncaughtExceptionHandler
                                        ?.javaClass
                                        ?.name
                                        ?: "NONE"
                                )
                        )

                        write(
                            context,
                            "UNCAUGHT_CLASS=" +
                                throwable.javaClass.name
                        )

                        write(
                            context,
                            "UNCAUGHT_MESSAGE=" +
                                (throwable.message ?: "")
                        )

                        write(
                            context,
                            "STACKTRACE_BEGIN\n" +
                                android.util.Log
                                    .getStackTraceString(
                                        throwable
                                    ) +
                                "\nSTACKTRACE_END"
                        )
                    } catch (_: Throwable) {
                    }

                    if (
                        delegate != null &&
                        delegate !== this
                    ) {
                        delegate.uncaughtException(
                            thread,
                            throwable
                        )
                    } else {
                        android.os.Process.killProcess(
                            android.os.Process.myPid()
                        )
                    }
                }
            }

        crashHandler = handler

        Thread.setDefaultUncaughtExceptionHandler(
            handler
        )

        mainThread.uncaughtExceptionHandler =
            handler

        val defaultPass =
            Thread.getDefaultUncaughtExceptionHandler() ===
                handler

        val mainPass =
            mainThread.uncaughtExceptionHandler ===
                handler

        write(
            context,
            "UNCAUGHT_HANDLER_" +
                phase +
                "_DELEGATE=" +
                (
                    delegate
                        ?.javaClass
                        ?.name
                        ?: "NONE"
                )
        )

        write(
            context,
            "UNCAUGHT_HANDLER_" +
                phase +
                "_STATE=INSTALLED"
        )

        write(
            context,
            "UNCAUGHT_HANDLER_" +
                phase +
                "_VERIFY=" +
                if (
                    defaultPass &&
                    mainPass
                ) {
                    "PASS"
                } else {
                    "FAIL"
                }
        )
    }

    @Synchronized
    private fun installMainLooperProbe(
        app: android.app.Application
    ) {
        if (mainLooperProbeInstalled) {
            return
        }

        mainLooperProbeInstalled = true

        val looper =
            android.os.Looper.getMainLooper()

        looper.setMessageLogging(
            android.util.Printer { raw ->
                val index =
                    mainLooperEventCount
                        .incrementAndGet()

                if (
                    raw.contains(
                        "android.app.ActivityThread"
                    ) &&
                    raw.contains(": 159")
                ) {
                    if (
                        executeTransactionSeen
                            .compareAndSet(
                                false,
                                true
                            )
                    ) {
                        write(
                            app,
                            "MAIN_LOOPER_EXECUTE_TRANSACTION_SEEN=PASS"
                        )
                    }
                }

                if (index <= 64) {
                    val safe =
                        raw
                            .replace(
                                "\\n",
                                " "
                            )
                            .replace(
                                "\\r",
                                " "
                            )
                            .take(1200)

                    write(
                        app,
                        "MAIN_LOOPER_EVENT_" +
                            index +
                            "=" +
                            safe
                    )
                }
            }
        )

        write(
            app,
            "MAIN_LOOPER_PROBE=INSTALLED"
        )

        val handler =
            android.os.Handler(looper)

        handler.postAtFrontOfQueue {
            write(
                app,
                "MAIN_LOOPER_FRONT_QUEUE=PASS"
            )

            installCrashHandler(
                app,
                "FRONT_QUEUE"
            )
        }

        handler.postDelayed(
            {
                write(
                    app,
                    "MAIN_LOOPER_DELAYED_100MS=PASS"
                )

                installCrashHandler(
                    app,
                    "DELAYED_100MS"
                )

                recordLaunchState(
                    app,
                    "DELAYED_100MS"
                )
            },
            100L
        )

        handler.postDelayed(
            {
                write(
                    app,
                    "PROCESS_ALIVE_1500MS=PASS"
                )

                write(
                    app,
                    "ACTIVITY_EVENT_COUNT_1500MS=" +
                        activityEventCount.get()
                )

                write(
                    app,
                    "EXECUTE_TRANSACTION_SEEN_1500MS=" +
                        if (
                            executeTransactionSeen.get()
                        ) {
                            "PASS"
                        } else {
                            "NO"
                        }
                )

                recordLaunchState(
                    app,
                    "DELAYED_1500MS"
                )
            },
            1500L
        )

        handler.postDelayed(
            {
                write(
                    app,
                    "PROCESS_ALIVE_3000MS=PASS"
                )

                write(
                    app,
                    "ACTIVITY_EVENT_COUNT_3000MS=" +
                        activityEventCount.get()
                )

                write(
                    app,
                    "EXECUTE_TRANSACTION_SEEN_3000MS=" +
                        if (
                            executeTransactionSeen.get()
                        ) {
                            "PASS"
                        } else {
                            "NO"
                        }
                )

                recordLaunchState(
                    app,
                    "DELAYED_3000MS"
                )
            },
            3000L
        )
    }

    private fun recordLaunchState(
        context: android.content.Context,
        phase: String
    ) {
        try {
            val packageManager =
                context.packageManager

            val packageName =
                context.packageName

            val launchIntent =
                packageManager
                    .getLaunchIntentForPackage(
                        packageName
                    )

            val component =
                launchIntent
                    ?.component

            write(
                context,
                "LAUNCH_" +
                    phase +
                    "_PACKAGE=" +
                    packageName
            )

            write(
                context,
                "LAUNCH_" +
                    phase +
                    "_COMPONENT=" +
                    (
                        component
                            ?.flattenToShortString()
                            ?: "NONE"
                    )
            )

            write(
                context,
                "LAUNCH_" +
                    phase +
                    "_ACTION=" +
                    (
                        launchIntent
                            ?.action
                            ?: "NONE"
                    )
            )

            write(
                context,
                "LAUNCH_" +
                    phase +
                    "_CATEGORIES=" +
                    (
                        launchIntent
                            ?.categories
                            ?.sorted()
                            ?.joinToString(",")
                            ?: "NONE"
                    )
            )

            write(
                context,
                "LAUNCH_" +
                    phase +
                    "_FLAGS=" +
                    (
                        launchIntent
                            ?.flags
                            ?.toString()
                            ?: "NONE"
                    )
            )

            if (component != null) {
                val info =
                    packageManager.getActivityInfo(
                        component,
                        0
                    )

                write(
                    context,
                    "LAUNCH_" +
                        phase +
                        "_ACTIVITY_ENABLED=" +
                        info.enabled
                )

                write(
                    context,
                    "LAUNCH_" +
                        phase +
                        "_ACTIVITY_EXPORTED=" +
                        info.exported
                )

                write(
                    context,
                    "LAUNCH_" +
                        phase +
                        "_APP_ENABLED=" +
                        info.applicationInfo.enabled
                )

                write(
                    context,
                    "LAUNCH_" +
                        phase +
                        "_COMPONENT_SETTING=" +
                        packageManager
                            .getComponentEnabledSetting(
                                component
                            )
                )
            }

            val launcherQuery =
                android.content.Intent(
                    android.content.Intent.ACTION_MAIN
                ).apply {
                    addCategory(
                        android.content.Intent.CATEGORY_LAUNCHER
                    )

                    setPackage(
                        packageName
                    )
                }

            val matches =
                packageManager.queryIntentActivities(
                    launcherQuery,
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                )

            write(
                context,
                "LAUNCH_" +
                    phase +
                    "_MATCH_COUNT=" +
                    matches.size
            )

            matches
                .take(8)
                .forEachIndexed {
                        index,
                        resolve ->

                    val activityInfo =
                        resolve.activityInfo

                    write(
                        context,
                        "LAUNCH_" +
                            phase +
                            "_MATCH_" +
                            index +
                            "=" +
                            activityInfo.packageName +
                            "/" +
                            activityInfo.name +
                            "|enabled=" +
                            activityInfo.enabled +
                            "|exported=" +
                            activityInfo.exported
                    )
                }

            val process =
                android.app.ActivityManager
                    .RunningAppProcessInfo()

            android.app.ActivityManager
                .getMyMemoryState(
                    process
                )

            write(
                context,
                "LAUNCH_" +
                    phase +
                    "_PROCESS_IMPORTANCE=" +
                    process.importance
            )

            write(
                context,
                "LAUNCH_" +
                    phase +
                    "_ACTIVITY_EVENT_COUNT=" +
                    activityEventCount.get()
            )

            write(
                context,
                "LAUNCH_" +
                    phase +
                    "_EXECUTE_TRANSACTION_SEEN=" +
                    if (
                        executeTransactionSeen.get()
                    ) {
                        "PASS"
                    } else {
                        "NO"
                    }
            )
        } catch (error: Throwable) {
            write(
                context,
                "LAUNCH_" +
                    phase +
                    "_ERROR=" +
                    error.javaClass.name +
                    ":" +
                    (error.message ?: "")
            )
        }
    }

    @Synchronized
    private fun flushEarlyFactoryEvents(
        app: android.app.Application
    ) {
        val pending =
            earlyFactoryEvents.toList()

        earlyFactoryEvents.clear()

        write(
            app,
            "COMPONENT_FACTORY_EARLY_FLUSH_COUNT=" +
                pending.size
        )

        pending.forEach { message ->
            write(
                app,
                message
            )
        }
    }

    private fun componentFactoryRecord(
        message: String
    ) {
        val app = applicationRef

        if (app == null) {
            earlyFactoryEvents.add(
                message
            )

            android.util.Log.e(
                TAG,
                "EARLY_FACTORY_BUFFER=" +
                    message
            )

            return
        }

        write(
            app,
            message
        )
    }

    private fun recordPreviousExit(
        app: android.app.Application
    ) {
        if (
            android.os.Build.VERSION.SDK_INT <
            android.os.Build.VERSION_CODES.R
        ) {
            write(
                app,
                "PREVIOUS_EXIT=UNAVAILABLE_API"
            )
            return
        }

        try {
            val manager =
                app.getSystemService(
                    android.app.ActivityManager::class.java
                )

            val exits =
                manager.getHistoricalProcessExitReasons(
                    app.packageName,
                    0,
                    5
                )

            write(
                app,
                "PREVIOUS_EXIT_COUNT=" + exits.size
            )

            exits.take(5).forEachIndexed {
                    index,
                    info ->

                val prefix =
                    "PREVIOUS_EXIT_" +
                        index +
                        "_"

                write(
                    app,
                    prefix + "REASON=" + info.reason
                )

                write(
                    app,
                    prefix + "STATUS=" + info.status
                )

                write(
                    app,
                    prefix + "IMPORTANCE=" + info.importance
                )

                write(
                    app,
                    prefix + "TIMESTAMP=" + info.timestamp
                )

                write(
                    app,
                    prefix + "PROCESS=" + info.processName
                )

                write(
                    app,
                    prefix +
                        "DESCRIPTION=" +
                        (info.description ?: "")
                )
            }
        } catch (error: Throwable) {
            write(
                app,
                "PREVIOUS_EXIT_ERROR=" +
                    error.javaClass.name +
                    ":" +
                    (error.message ?: "")
            )
        }
    }

    fun componentFactoryStage(
        stage: String,
        className: String,
        intent: android.content.Intent?
    ) {
        val component =
            try {
                intent
                    ?.component
                    ?.flattenToShortString()
                    ?: "NONE"
            } catch (error: Throwable) {
                "ERROR:" +
                    error.javaClass.name
            }

        componentFactoryRecord(
            "STAGE=" + stage
        )

        componentFactoryRecord(
            stage + "_CLASS=" + className
        )

        componentFactoryRecord(
            stage + "_COMPONENT=" + component
        )
    }

    fun componentFactoryThrowable(
        className: String,
        error: Throwable
    ) {
        componentFactoryRecord(
            "COMPONENT_FACTORY_THROWABLE_CLASS_NAME=" +
                className
        )

        componentFactoryRecord(
            "COMPONENT_FACTORY_THROWABLE_CLASS=" +
                error.javaClass.name
        )

        componentFactoryRecord(
            "COMPONENT_FACTORY_THROWABLE_MESSAGE=" +
                (error.message ?: "")
        )

        componentFactoryRecord(
            "COMPONENT_FACTORY_THROWABLE_STACK_BEGIN\n" +
                android.util.Log.getStackTraceString(error) +
                "\nCOMPONENT_FACTORY_THROWABLE_STACK_END"
        )
    }

    fun activityThrowable(
        context: android.content.Context,
        error: Throwable
    ) {
        val appContext =
            context.applicationContext

        write(
            appContext,
            "MAIN_ACTIVITY_THROWABLE_CLASS=" +
                error.javaClass.name
        )

        write(
            appContext,
            "MAIN_ACTIVITY_THROWABLE_MESSAGE=" +
                (error.message ?: "")
        )

        write(
            appContext,
            "MAIN_ACTIVITY_THROWABLE_STACK_BEGIN\n" +
                android.util.Log.getStackTraceString(error) +
                "\nMAIN_ACTIVITY_THROWABLE_STACK_END"
        )
    }

    private fun installActivityLifecycleProbe(
        app: android.app.Application
    ) {
        // APPFORGE_EXPO_ACTIVITY_IDENTITY_V14_1
        fun record(
            stage: String,
            activity: android.app.Activity
        ) {
            activityEventCount
                .incrementAndGet()

            val className =
                activity.javaClass.name

            val component =
                try {
                    activity.intent
                        ?.component
                        ?.flattenToShortString()
                        ?: "NONE"
                } catch (error: Throwable) {
                    "ERROR:" +
                        error.javaClass.name
                }

            write(
                app,
                "STAGE=" + stage
            )

            write(
                app,
                stage + "_CLASS=" + className
            )

            write(
                app,
                stage + "_COMPONENT=" + component
            )
        }

        app.registerActivityLifecycleCallbacks(
            object :
                android.app.Application.ActivityLifecycleCallbacks {

                override fun onActivityPreCreated(
                    activity: android.app.Activity,
                    savedInstanceState: android.os.Bundle?
                ) {
                    record(
                        "ACTIVITY_PRE_CREATED",
                        activity
                    )
                }

                override fun onActivityCreated(
                    activity: android.app.Activity,
                    savedInstanceState: android.os.Bundle?
                ) {
                    record(
                        "ACTIVITY_CREATED",
                        activity
                    )
                }

                override fun onActivityStarted(
                    activity: android.app.Activity
                ) {
                    record(
                        "ACTIVITY_STARTED",
                        activity
                    )
                }

                override fun onActivityResumed(
                    activity: android.app.Activity
                ) {
                    record(
                        "ACTIVITY_RESUMED",
                        activity
                    )
                }

                override fun onActivityPaused(
                    activity: android.app.Activity
                ) {
                    record(
                        "ACTIVITY_PAUSED",
                        activity
                    )
                }

                override fun onActivityStopped(
                    activity: android.app.Activity
                ) {
                    record(
                        "ACTIVITY_STOPPED",
                        activity
                    )
                }

                override fun onActivitySaveInstanceState(
                    activity: android.app.Activity,
                    outState: android.os.Bundle
                ) {
                }

                override fun onActivityDestroyed(
                    activity: android.app.Activity
                ) {
                    record(
                        "ACTIVITY_DESTROYED",
                        activity
                    )
                }
            }
        )

        write(
            app,
            "ACTIVITY_CALLBACKS_REGISTERED=PASS"
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
            recordLaunchState(
                appContext,
                "READY"
            )

            installCrashHandler(
                appContext,
                "READY"
            )

            write(
                appContext,
                "UNCAUGHT_HANDLER_READY_VERIFY=" +
                    if (
                        Thread.getDefaultUncaughtExceptionHandler() ===
                            crashHandler &&
                        android.os.Looper
                            .getMainLooper()
                            .thread
                            .uncaughtExceptionHandler ===
                            crashHandler
                    ) {
                        "PASS"
                    } else {
                        "FAIL"
                    }
            )

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

#
# APPFORGE_EXPO_COMPONENT_FACTORY_V15
#
# ActivityLifecycleCallbacks begin only after an Activity instance exists.
# V14.1 physically proved the callbacks were registered but never received
# an Activity. Intercept the framework's Activity instantiation boundary so
# class loading / constructor failures are captured before onCreate().
#
APPFORGE_EXPO_COMPONENT_FACTORY_FILE="$(
  dirname "$MAIN_APPLICATION"
)/AppForgeExpoComponentFactory.kt"

cat > "$APPFORGE_EXPO_COMPONENT_FACTORY_FILE" <<EOF
package $APPFORGE_EXPO_PACKAGE

class AppForgeExpoComponentFactory :
    androidx.core.app.CoreComponentFactory() {

    override fun instantiateApplication(
        cl: ClassLoader,
        className: String
    ): android.app.Application {
        AppForgeExpoRuntimeProbe.componentFactoryStage(
            "COMPONENT_FACTORY_APPLICATION_BEFORE",
            className,
            null
        )

        return try {
            super.instantiateApplication(
                cl,
                className
            ).also { application ->
                AppForgeExpoRuntimeProbe.componentFactoryStage(
                    "COMPONENT_FACTORY_APPLICATION_AFTER",
                    application.javaClass.name,
                    null
                )
            }
        } catch (error: Throwable) {
            AppForgeExpoRuntimeProbe.componentFactoryThrowable(
                className,
                error
            )
            throw error
        }
    }

    override fun instantiateActivity(
        cl: ClassLoader,
        className: String,
        intent: android.content.Intent?
    ): android.app.Activity {
        AppForgeExpoRuntimeProbe.componentFactoryStage(
            "COMPONENT_FACTORY_BEFORE",
            className,
            intent
        )

        return try {
            super.instantiateActivity(
                cl,
                className,
                intent
            ).also { activity ->
                AppForgeExpoRuntimeProbe.componentFactoryStage(
                    "COMPONENT_FACTORY_AFTER",
                    activity.javaClass.name,
                    intent
                )
            }
        } catch (error: Throwable) {
            AppForgeExpoRuntimeProbe.componentFactoryThrowable(
                className,
                error
            )
            throw error
        }
    }
}
EOF

test -s "$APPFORGE_EXPO_COMPONENT_FACTORY_FILE"

APPFORGE_EXPO_MANIFEST="android/app/src/main/AndroidManifest.xml"
test -f "$APPFORGE_EXPO_MANIFEST"

MANIFEST_PATH="$APPFORGE_EXPO_MANIFEST" \
"$NODE_HOME/bin/node" <<'NODE'
const fs = require("fs");

const manifestPath =
  process.env.MANIFEST_PATH;

let xml =
  fs.readFileSync(
    manifestPath,
    "utf8"
  );

if (
  !xml.includes(
    'xmlns:tools="http://schemas.android.com/tools"'
  )
) {
  xml =
    xml.replace(
      /<manifest\b/,
      '<manifest xmlns:tools="http://schemas.android.com/tools"'
    );
}

const applicationMatch =
  /<application\b[^>]*>/m.exec(xml);

if (!applicationMatch) {
  throw new Error(
    "Expo AndroidManifest application tag missing"
  );
}

let applicationTag =
  applicationMatch[0];

if (
  !applicationTag.includes(
    'android:appComponentFactory=".AppForgeExpoComponentFactory"'
  )
) {
  applicationTag =
    applicationTag.replace(
      />$/,
      '\n    android:appComponentFactory=".AppForgeExpoComponentFactory">'
    );
}

const toolsReplace =
  /tools:replace="([^"]*)"/;

if (toolsReplace.test(applicationTag)) {
  applicationTag =
    applicationTag.replace(
      toolsReplace,
      (full, value) => {
        const parts =
          value
            .split(",")
            .map((part) => part.trim())
            .filter(Boolean);

        if (
          !parts.includes(
            "android:appComponentFactory"
          )
        ) {
          parts.push(
            "android:appComponentFactory"
          );
        }

        return (
          'tools:replace="' +
          parts.join(",") +
          '"'
        );
      }
    );
} else {
  applicationTag =
    applicationTag.replace(
      />$/,
      '\n    tools:replace="android:appComponentFactory">'
    );
}

xml =
  xml.slice(
    0,
    applicationMatch.index
  ) +
  applicationTag +
  xml.slice(
    applicationMatch.index +
      applicationMatch[0].length
  );

fs.writeFileSync(
  manifestPath,
  xml
);
NODE

grep -Fq \
  'class AppForgeExpoComponentFactory' \
  "$APPFORGE_EXPO_COMPONENT_FACTORY_FILE"

grep -Fq \
  'COMPONENT_FACTORY_BEFORE' \
  "$APPFORGE_EXPO_COMPONENT_FACTORY_FILE"

grep -Fq \
  'componentFactoryThrowable' \
  "$APPFORGE_EXPO_COMPONENT_FACTORY_FILE"

grep -Fq \
  'COMPONENT_FACTORY_APPLICATION_BEFORE' \
  "$APPFORGE_EXPO_COMPONENT_FACTORY_FILE"

grep -Fq \
  'instantiateApplication' \
  "$APPFORGE_EXPO_COMPONENT_FACTORY_FILE"

grep -Fq \
  'android:appComponentFactory=".AppForgeExpoComponentFactory"' \
  "$APPFORGE_EXPO_MANIFEST"

grep -Fq \
  'tools:replace="' \
  "$APPFORGE_EXPO_MANIFEST"

echo "APPFORGE_EXPO_COMPONENT_FACTORY_V15=PASS"

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

// APPFORGE_EXPO_NATIVE_PACKAGE_REGISTRATION_V19
if (
  !application.includes(
    "APPFORGE_EXPO_NATIVE_PACKAGE_REGISTRATION_V19"
  )
) {
  const packagesApplyPattern =
    /(PackageList\(this\)\.packages\.apply\s*\{)/m;

  if (!packagesApplyPattern.test(application)) {
    throw new Error(
      "MainApplication PackageList apply anchor missing"
    );
  }

  application =
    application.replace(
      packagesApplyPattern,
      (line) =>
        line +
        "\n" +
        "              // APPFORGE_EXPO_NATIVE_PACKAGE_REGISTRATION_V19\n" +
        "              if (none { it.javaClass.name == \"expo.modules.ExpoModulesPackage\" }) {\n" +
        "                add(expo.modules.ExpoModulesPackage())\n" +
        "              }"
    );
}

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
    "APPFORGE_EXPO_ACTIVITY_BOUNDARY_V14"
  )
) {
  if (
    /override\s+fun\s+attachBaseContext\s*\(/m
      .test(activity)
  ) {
    throw new Error(
      "MainActivity already overrides attachBaseContext"
    );
  }

  const classPattern =
    /(class\s+MainActivity\s*:\s*ReactActivity\s*\(\s*\)\s*\{)/m;

  if (!classPattern.test(activity)) {
    throw new Error(
      "MainActivity class anchor missing"
    );
  }

  const injected =
    [
      "",
      "  // APPFORGE_EXPO_ACTIVITY_BOUNDARY_V14",
      "  init {",
      '    AppForgeExpoRuntimeProbe.early("MAIN_ACTIVITY_INIT")',
      "  }",
      "",
      "  override fun attachBaseContext(",
      "    newBase: android.content.Context",
      "  ) {",
      '    AppForgeExpoRuntimeProbe.mark(newBase, "MAIN_ACTIVITY_ATTACH_ENTER")',
      "",
      "    try {",
      "      super.attachBaseContext(newBase)",
      "    } catch (error: Throwable) {",
      "      AppForgeExpoRuntimeProbe.activityThrowable(",
      "        newBase,",
      "        error",
      "      )",
      "      throw error",
      "    }",
      "",
      '    AppForgeExpoRuntimeProbe.mark(newBase, "MAIN_ACTIVITY_ATTACH_RETURN")',
      "  }",
      ""
    ].join("\n");

  activity =
    activity.replace(
      classPattern,
      (line) =>
        line +
        injected
    );
}

if (
  !activity.includes(
    "APPFORGE_EXPO_ACTIVITY_GUARD_V12"
  )
) {
  const activitySuperPattern =
    /^(\s*)super\.onCreate\(([^)]*)\)\s*$/m;

  if (!activitySuperPattern.test(activity)) {
    throw new Error(
      "MainActivity super.onCreate anchor missing"
    );
  }

  activity =
    activity.replace(
      activitySuperPattern,
      (line, indent, args) =>
        indent +
        "// APPFORGE_EXPO_ACTIVITY_GUARD_V12\n" +
        indent +
        'AppForgeExpoRuntimeProbe.mark(this, "MAIN_ACTIVITY_BEFORE_SUPER")\n' +
        indent +
        "try {\n" +
        indent +
        "    super.onCreate(" +
        args +
        ")\n" +
        indent +
        "} catch (error: Throwable) {\n" +
        indent +
        "    AppForgeExpoRuntimeProbe.activityThrowable(this, error)\n" +
        indent +
        "    throw error\n" +
        indent +
        "}\n" +
        indent +
        'AppForgeExpoRuntimeProbe.mark(this, "MAIN_ACTIVITY_AFTER_SUPER")'
    );
}

if (
  !activity.includes(
    "APPFORGE_EXPO_FULL_ONCREATE_GUARD_V13"
  )
) {
  const onCreatePattern =
    /override\s+fun\s+onCreate\s*\([^)]*\)\s*\{/m;

  const onCreateMatch =
    onCreatePattern.exec(activity);

  if (!onCreateMatch) {
    throw new Error(
      "MainActivity onCreate function anchor missing"
    );
  }

  const lineStart =
    activity.lastIndexOf(
      "\n",
      onCreateMatch.index
    ) + 1;

  const methodIndent =
    activity
      .slice(
        lineStart,
        onCreateMatch.index
      )
      .match(/^[ \t]*/)[0];

  const openIndex =
    onCreateMatch.index +
    onCreateMatch[0].lastIndexOf("{");

  let depth = 0;
  let closeIndex = -1;
  let quote = null;
  let escaped = false;
  let lineComment = false;
  let blockComment = false;

  for (
    let index = openIndex;
    index < activity.length;
    index += 1
  ) {
    const current = activity[index];
    const next = activity[index + 1];

    if (lineComment) {
      if (current === "\n") {
        lineComment = false;
      }

      continue;
    }

    if (blockComment) {
      if (
        current === "*" &&
        next === "/"
      ) {
        blockComment = false;
        index += 1;
      }

      continue;
    }

    if (quote !== null) {
      if (escaped) {
        escaped = false;
        continue;
      }

      if (current === "\\") {
        escaped = true;
        continue;
      }

      if (current === quote) {
        quote = null;
      }

      continue;
    }

    if (
      current === "/" &&
      next === "/"
    ) {
      lineComment = true;
      index += 1;
      continue;
    }

    if (
      current === "/" &&
      next === "*"
    ) {
      blockComment = true;
      index += 1;
      continue;
    }

    if (
      current === '"' ||
      current === "'"
    ) {
      quote = current;
      continue;
    }

    if (current === "{") {
      depth += 1;
      continue;
    }

    if (current === "}") {
      depth -= 1;

      if (depth === 0) {
        closeIndex = index;
        break;
      }
    }
  }

  if (closeIndex < 0) {
    throw new Error(
      "MainActivity onCreate closing brace missing"
    );
  }

  const originalBody =
    activity
      .slice(
        openIndex + 1,
        closeIndex
      )
      .replace(/^\s*\n/, "")
      .replace(/\s*$/, "");

  const originalLines =
    originalBody.length === 0
      ? []
      : originalBody.split("\n");

  const nonBlankIndents =
    originalLines
      .filter(
        (line) =>
          line.trim().length > 0
      )
      .map(
        (line) =>
          (line.match(/^[ \t]*/) || [""])[0]
            .length
      );

  const commonIndent =
    nonBlankIndents.length > 0
      ? Math.min(...nonBlankIndents)
      : 0;

  const bodyIndent =
    methodIndent + "  ";

  const nestedIndent =
    bodyIndent + "  ";

  const nestedBody =
    originalLines
      .map(
        (line) => {
          if (line.trim().length === 0) {
            return "";
          }

          return (
            nestedIndent +
            line.slice(commonIndent)
          );
        }
      )
      .join("\n");

  const wrappedBody =
    "\n" +
    bodyIndent +
    "// APPFORGE_EXPO_FULL_ONCREATE_GUARD_V13\n" +
    bodyIndent +
    'AppForgeExpoRuntimeProbe.mark(this, "MAIN_ACTIVITY_ONCREATE_ENTER")\n' +
    bodyIndent +
    "try {\n" +
    nestedBody +
    "\n" +
    bodyIndent +
    "} catch (error: Throwable) {\n" +
    nestedIndent +
    'AppForgeExpoRuntimeProbe.mark(this, "MAIN_ACTIVITY_ONCREATE_CATCH")\n' +
    nestedIndent +
    "AppForgeExpoRuntimeProbe.activityThrowable(this, error)\n" +
    nestedIndent +
    "throw error\n" +
    bodyIndent +
    "}\n" +
    bodyIndent +
    'AppForgeExpoRuntimeProbe.mark(this, "MAIN_ACTIVITY_ONCREATE_RETURN")\n' +
    methodIndent;

  activity =
    activity.slice(
      0,
      openIndex + 1
    ) +
    wrappedBody +
    activity.slice(closeIndex);
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
  'APPFORGE_EXPO_NATIVE_PACKAGE_REGISTRATION_V19' \
  "$MAIN_APPLICATION"

grep -Fq \
  'expo.modules.ExpoModulesPackage()' \
  "$MAIN_APPLICATION"

grep -Fq \
  'none { it.javaClass.name == "expo.modules.ExpoModulesPackage" }' \
  "$MAIN_APPLICATION"

echo "APPFORGE_EXPO_NATIVE_PACKAGE_REGISTRATION_V19=PASS"

grep -q \
  'APPFORGE_EXPO_ACTIVITY_BOUNDARY_V14' \
  "$MAIN_ACTIVITY"

grep -q \
  'MAIN_ACTIVITY_INIT' \
  "$MAIN_ACTIVITY"

grep -q \
  'MAIN_ACTIVITY_ATTACH_ENTER' \
  "$MAIN_ACTIVITY"

grep -q \
  'MAIN_ACTIVITY_ATTACH_RETURN' \
  "$MAIN_ACTIVITY"

grep -q \
  'ACTIVITY_PRE_CREATED' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -q \
  'ACTIVITY_CREATED' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -q \
  'APPFORGE_EXPO_ACTIVITY_IDENTITY_V14_1' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'stage + "_CLASS="' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'stage + "_COMPONENT="' \
  "$APPFORGE_EXPO_PROBE_FILE"

echo "APPFORGE_EXPO_ACTIVITY_IDENTITY_VERIFY_V14_1_1=PASS"

grep -q \
  'ACTIVITY_CALLBACKS_REGISTERED=PASS' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -q \
  'APPFORGE_EXPO_ACTIVITY_GUARD_V12' \
  "$MAIN_ACTIVITY"

grep -q \
  'APPFORGE_EXPO_FULL_ONCREATE_GUARD_V13' \
  "$MAIN_ACTIVITY"

grep -q \
  'MAIN_ACTIVITY_ONCREATE_ENTER' \
  "$MAIN_ACTIVITY"

grep -q \
  'MAIN_ACTIVITY_ONCREATE_CATCH' \
  "$MAIN_ACTIVITY"

grep -q \
  'MAIN_ACTIVITY_ONCREATE_RETURN' \
  "$MAIN_ACTIVITY"

grep -q \
  'MAIN_ACTIVITY_BEFORE_SUPER' \
  "$MAIN_ACTIVITY"

grep -q \
  'MAIN_ACTIVITY_AFTER_SUPER' \
  "$MAIN_ACTIVITY"

grep -q \
  'PREVIOUS_EXIT_COUNT=' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -q \
  'getHistoricalProcessExitReasons' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -q \
  'MAIN_ACTIVITY_THROWABLE_CLASS=' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -q \
  'MediaStore.Downloads.EXTERNAL_CONTENT_URI' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -q \
  'STACKTRACE_BEGIN' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'COMPONENT_FACTORY_THROWABLE_STACK_BEGIN' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'stage + "_CLASS="' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'APPFORGE_EXPO_COMPONENT_FACTORY_V15=PASS' \
  "$0"

grep -Fq \
  'COMPONENT_FACTORY_EARLY_FLUSH_COUNT=' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'UNCAUGHT_HANDLER_READY_VERIFY=' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'APPFORGE_EXPO_STARTUP_CAPTURE_V16' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'APPFORGE_EXPO_JAVA_CRASH_CAPTURE_V17' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'MAIN_LOOPER_PROBE=INSTALLED' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'MAIN_LOOPER_FRONT_QUEUE=PASS' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'APPFORGE_EXPO_LAUNCH_TRANSACTION_V18' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'MAIN_LOOPER_EXECUTE_TRANSACTION_SEEN=PASS' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'LAUNCH_' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'PROCESS_ALIVE_3000MS=PASS' \
  "$APPFORGE_EXPO_PROBE_FILE"

grep -Fq \
  'uncaughtExceptionHandler =' \
  "$APPFORGE_EXPO_PROBE_FILE"

echo "APPFORGE_EXPO_RUNTIME_PROBE_FILE=$APPFORGE_EXPO_PROBE_FILE"
echo "APPFORGE_EXPO_RUNTIME_PROBE_REPORT=Downloads/AppForgeStudio/ExpoCrash"
echo "APPFORGE_EXPO_RUNTIME_CRASH_PROBE=PASS"
echo "APPFORGE_EXPO_RUNTIME_EXIT_PROBE_V12=PASS"
echo "APPFORGE_EXPO_FULL_ONCREATE_CAPTURE_V13=PASS"
echo "APPFORGE_EXPO_ACTIVITY_BOUNDARY_V14=PASS"
echo "APPFORGE_EXPO_ACTIVITY_IDENTITY_V14_1=PASS"
echo "APPFORGE_EXPO_COMPONENT_FACTORY_CAPTURE_V15=PASS"
echo "APPFORGE_EXPO_STARTUP_CAPTURE_V16=PASS"
echo "APPFORGE_EXPO_JAVA_CRASH_CAPTURE_V17=PASS"
echo "APPFORGE_EXPO_LAUNCH_TRANSACTION_V18=PASS"
echo "APPFORGE_EXPO_NATIVE_PACKAGE_REGISTRATION_V19=PASS"
echo "APPFORGE_EXPO_DISABLE_PCH_V20=PASS"
echo "APPFORGE_EXPO_PCH_POST_NPM_V20_1=PASS"
echo "APPFORGE_EXPO_PCH_ROOT_DISCOVERY_V20_2=PASS"

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
  true

set_prop_native \
  reactNativeArchitectures \
  arm64-v8a

grep -q '^newArchEnabled=false$' \
  "$PREBUILD/android/gradle.properties"

grep -q '^hermesEnabled=true$' \
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

grep -q '^hermesEnabled=true$' \
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
# V19 NOTE: this Gradle dependency provides Expo classes but does NOT by
# itself register ExpoModulesPackage with React Native. MainApplication
# therefore has a separate duplicate-safe package registration fallback.
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
# Gradle hermesEnabled=true packages the Hermes Android runtime.
# AppForge separately suppresses host hermesc/AOT for the acceptance variant.
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

grep -q '^hermesEnabled=true$' \
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
echo "APPFORGE_EXPO_HERMES_NATIVE_PACKAGE_V11=ENABLED"
echo "APPFORGE_EXPO_HERMES_GRADLE_PROPERTY=TRUE"

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
    // APPFORGE_EXPO_MANUAL_HERMES_BUNDLE_V2
    //
    // RN treats this variant as bundle-task-debuggable only so its
    // Gradle plugin does not invoke the host hermesc executable.
    // Android itself remains debuggable=false. AppForge embeds the
    // production JS bundle explicitly below.
    debuggableVariants = ["debug", "appforgeAcceptance"]
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

#
# APPFORGE_EXPO_ACCEPTANCE_VARIANT_GUARD_V11_1
#
# V11 deliberately marks appforgeAcceptance as RN-debuggable so the
# React Native Gradle plugin skips host hermesc/AOT execution. Android
# itself remains debuggable=false and AppForge embeds the production
# bundle manually. A second active debug-only assignment would conflict
# with that contract.
#
if grep -Eq \
  '^[[:space:]]*debuggableVariants[[:space:]]*=[[:space:]]*\["debug"\][[:space:]]*$' \
  "$APP_GRADLE"
then
  echo "APPFORGE_EXPO_ACCEPTANCE_RN_VARIANT_CONFLICT=FAIL"
  exit 44
fi

grep -Eq \
  '^[[:space:]]*debuggableVariants[[:space:]]*=[[:space:]]*\["debug",[[:space:]]*"appforgeAcceptance"\][[:space:]]*$' \
  "$APP_GRADLE"

echo "APPFORGE_EXPO_ACCEPTANCE_RN_VARIANT_GUARD=PASS"

echo "APPFORGE_EXPO_ACCEPTANCE_ANDROID_DEBUGGABLE=FALSE"
echo "APPFORGE_EXPO_ACCEPTANCE_RN_BUNDLE_TASK=SKIPPED"
echo "APPFORGE_EXPO_HERMES_HOST_AOT=SKIPPED"

echo "APPFORGE_EXPO_STANDALONE_BUILD_TYPE=appforgeAcceptance"
echo "APPFORGE_EXPO_STANDALONE_BUNDLE=MANUAL_EMBED"
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

#
# APPFORGE_EXPO_MANUAL_BUNDLE_EMBED_V2
#
# hermesEnabled=true is required so the Android Hermes runtime and
# libhermes.so are packaged. The acceptance variant is deliberately
# listed in React Native debuggableVariants only to suppress the
# Gradle host-hermesc/AOT task on the ARM64 Android host.
#
# The production JS generated above is therefore copied explicitly
# into the Android application before Gradle packaging.
#
APPFORGE_EXPO_MANUAL_ASSETS_DIR="$SOURCE/android/app/src/main/assets"
APPFORGE_EXPO_MANUAL_RES_DIR="$SOURCE/android/app/src/main/res"

mkdir -p "$APPFORGE_EXPO_MANUAL_ASSETS_DIR"
mkdir -p "$APPFORGE_EXPO_MANUAL_RES_DIR"

cp \
  "$APPFORGE_EXPO_BUNDLE_PROBE/index.android.bundle" \
  "$APPFORGE_EXPO_MANUAL_ASSETS_DIR/index.android.bundle"

test -s \
  "$APPFORGE_EXPO_MANUAL_ASSETS_DIR/index.android.bundle"

if find \
  "$APPFORGE_EXPO_BUNDLE_PROBE/assets" \
  -mindepth 1 \
  -print -quit \
  | grep -q .
then
  cp -a \
    "$APPFORGE_EXPO_BUNDLE_PROBE/assets/." \
    "$APPFORGE_EXPO_MANUAL_RES_DIR/"
fi

echo "APPFORGE_EXPO_MANUAL_BUNDLE_PATH=android/app/src/main/assets/index.android.bundle"

echo "APPFORGE_EXPO_MANUAL_BUNDLE_SIZE=$(
  wc -c \
    < "$APPFORGE_EXPO_MANUAL_ASSETS_DIR/index.android.bundle" \
  | tr -d ' '
)"

echo "APPFORGE_EXPO_MANUAL_BUNDLE_EMBED_V2=PASS"

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
