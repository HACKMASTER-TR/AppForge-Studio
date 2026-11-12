#!/bin/sh
set -eu
if [ -f /workspace/runtime/module-download-telemetry.sh ]; then
  . /workspace/runtime/module-download-telemetry.sh
fi

MODE="${1:?Offline pack mode required}"

# Re-run the actual warmup build without allowing Gradle dependency downloads.
set --
if [ "${APPFORGE_DEVICE_OFFLINE:-0}" = 1 ]; then
  set -- --offline
fi

ROOT="/opt/appforge-device"
SDK="$ROOT/android-sdk"
GRADLE="$ROOT/gradle-9.3.1/bin/gradle"
BASE="/workspace/.appforge-offline-prewarm"

export JAVA_HOME="$ROOT/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="$SDK"
export ANDROID_HOME="$SDK"
export GRADLE_USER_HOME="/root/.gradle-appforge"
export NPM_CONFIG_CACHE="$ROOT/npm-cache-v1"
export NPM_CONFIG_UPDATE_NOTIFIER=false

test -x "$JAVA_HOME/bin/java"
test -x "$GRADLE"
test -f "$SDK/platforms/android-37.0/android.jar"
test -x "$SDK/build-tools/36.0.0/aapt2"

mkdir -p "$BASE" "$NPM_CONFIG_CACHE"

write_gradle_properties() {
  target="$1"

  cat > "$target/gradle.properties" <<'EOF'
android.aapt2FromMavenOverride=/opt/appforge-device/android-sdk/build-tools/36.0.0/aapt2
org.gradle.workers.max=2
# Keep warmup compiler state out of the protected runtime home.
kotlin.compiler.execution.strategy=in-process
android.builder.sdkDownload=false
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
EOF

  cat > "$target/local.properties" <<'EOF'
sdk.dir=/opt/appforge-device/android-sdk
EOF
}


# Retry only bounded, recognizable transient network failures.
# Never bypass TLS, integrity, authentication or offline policy.
# Inspect the exact public AGP marker from INSIDE the packaged Ubuntu environment.
# This is a read-only TLS-verified diagnostic, not an unpinned artifact installer.
agp_marker_reachable() {
  if [ "${APPFORGE_DEVICE_OFFLINE:-0}" = 1 ]; then
    echo 'APPFORGE_AGP_MAVEN_PROBE=OFFLINE'
    return 1
  fi
  if ! command -v curl >/dev/null 2>&1; then
    echo 'APPFORGE_AGP_MAVEN_PROBE=CURL_UNAVAILABLE'
    return 1
  fi
  agp_curl_status=0
  agp_http_status=$(curl --fail --location --silent --show-error \
      --connect-timeout 8 --max-time 25 --retry 0 \
      --output /dev/null --write-out '%{http_code}' \
      'https://dl.google.com/dl/android/maven2/com/android/application/com.android.application.gradle.plugin/9.1.1/com.android.application.gradle.plugin-9.1.1.pom' \
      2>/dev/null) || agp_curl_status=$?
  case "$agp_curl_status:$agp_http_status" in
    0:200) echo 'APPFORGE_AGP_MAVEN_PROBE=REACHABLE'; return 0 ;;
    5:*|6:*) agp_result=DNS ;;
    7:*) agp_result=CONNECT ;;
    28:*) agp_result=TIMEOUT ;;
    35:*|51:*|58:*|60:*|77:*|83:*|90:*|91:*) agp_result=TLS ;;
    22:401|22:403|22:404|22:429|22:502|22:503|22:504)
      agp_result="HTTP_$agp_http_status" ;;
    *) agp_result=UNREACHABLE ;;
  esac
  printf 'APPFORGE_AGP_MAVEN_PROBE=%s\n' "$agp_result"
  return 1
}

# Use the same JDK and inherited TLS settings as Gradle. Emit only fixed
# categories and HTTP status codes; never persist exceptions or headers.
agp_jvm_probe() (
  probe_source=$(mktemp "$BASE/AgpMavenProbe.XXXXXX.java") || {
    echo 'APPFORGE_AGP_JVM_PROBE=UNAVAILABLE'
    return
  }
  trap 'rm -f "$probe_source"' EXIT
  cat > "$probe_source" <<'JAVA'
import java.net.*;
import java.io.IOException;
import javax.net.ssl.*;

class AgpMavenProbe {
    public static void main(String[] args) {
        probe("MARKER", "com/android/application/com.android.application.gradle.plugin/9.1.1/com.android.application.gradle.plugin-9.1.1.pom");
        probe("POM", "com/android/tools/build/gradle/9.1.1/gradle-9.1.1.pom");
    }

    private static void probe(String label, String path) {
        String result;
        try {
            var url = URI.create("https://dl.google.com/dl/android/maven2/" + path).toURL();
            var connection = (HttpsURLConnection) url.openConnection();
            try {
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(false);
                result = "HTTP_" + connection.getResponseCode();
            } finally {
                connection.disconnect();
            }
        } catch (SSLException e) {
            result = "TLS";
        } catch (UnknownHostException e) {
            result = "DNS";
        } catch (SocketTimeoutException e) {
            result = "TIMEOUT";
        } catch (ConnectException e) {
            result = "CONNECT";
        } catch (IOException e) {
            result = "IO";
        }
        System.out.println("APPFORGE_AGP_JVM_" + label + "=" + result);
    }
}
JAVA
  if ! "$JAVA_HOME/bin/java" "$probe_source" 2>/dev/null; then
    echo 'APPFORGE_AGP_JVM_PROBE=UNAVAILABLE'
  fi
)

gradle_warmup() {
  gw_project="$1"
  gw_log="$2"
  gw_kind="$3"
  gw_exit="$4"
  gw_attempt=1
  gw_max=3
  gw_refresh=0

  case "$gw_kind" in
    ANDROID)
      gw_failed="APPFORGE_OFFLINE_PREWARM_FAILED:ANDROID"
      gw_pass="APPFORGE_OFFLINE_ANDROID_PREWARM=PASS"
      ;;
    PYTHON)
      gw_failed="APPFORGE_OFFLINE_PREWARM_FAILED:PYTHON"
      gw_pass="APPFORGE_OFFLINE_PYTHON_PREWARM=PASS"
      ;;
    *) return 61 ;;
  esac

  if [ "${APPFORGE_DEVICE_OFFLINE:-0}" = 1 ]; then
    gw_max=1
  fi

  while [ "$gw_attempt" -le "$gw_max" ]; do
    set --
    if [ "${APPFORGE_DEVICE_OFFLINE:-0}" = 1 ]; then
      set -- --offline
    fi
    if [ "$gw_refresh" -eq 1 ]; then
      set -- "$@" --refresh-dependencies
    fi

    if "$GRADLE" "$@" \
      -p "$gw_project" \
      --no-daemon --no-configuration-cache \
      --console=plain \
      :app:assembleDebug >"$gw_log" 2>&1
    then
      tail -80 "$gw_log"
      echo "$gw_pass"
      return 0
    fi

    gw_unsafe='PKIX|certification path|SSLHandshakeException|certificate verify failed|checksum mismatch|hash mismatch|signature invalid|401 Unauthorized|403 Forbidden|404 Not Found'

    gw_transient='Read timed out|Connection reset|Connection timed out|SocketTimeoutException|ConnectException|Temporary failure in name resolution|502 Bad Gateway|503 Service Unavailable|504 Gateway Timeout|429 Too Many Requests'

    # A missing AGP marker can be a stale negative Gradle cache, but only
    # retry if Google's official marker is reachable from the Ubuntu runtime.
    # Never mask TLS, integrity, authentication, or explicit HTTP errors.
    if grep -Eq "Plugin \[id: 'com\.android\.application'.*version: '9\.1\.1'.*was not found" "$gw_log"; then
      if [ -d "$GRADLE_USER_HOME/caches/modules-2/files-2.1/com.android.tools.build/gradle/9.1.1" ]; then
        echo 'APPFORGE_AGP_CACHE_ENTRY=PRESENT_NOT_YET_VERIFIED'
      else
        echo 'APPFORGE_AGP_CACHE_ENTRY=MISSING'
      fi
      gw_marker_reachable=0
      if [ "${APPFORGE_DEVICE_OFFLINE:-0}" != 1 ]; then
        gw_probe=$(agp_marker_reachable) && gw_marker_reachable=1
        printf '%s\n' "$gw_probe"
        printf '\n%s\n' "$gw_probe" >> "$gw_log"
      fi
      if [ "$gw_attempt" -eq 1 ] &&
         [ "$gw_marker_reachable" -eq 1 ] &&
         ! grep -Eiq "$gw_unsafe" "$gw_log"; then
        gw_refresh=1
        gw_attempt=$((gw_attempt + 1))
        echo 'APPFORGE_AGP_RESOLUTION_RETRY=REFRESH_DEPENDENCIES'
        continue
      fi
      if [ "$gw_marker_reachable" -eq 1 ] && [ "$gw_refresh" -eq 1 ]; then
        gw_jvm_probe=$(agp_jvm_probe)
        printf '%s\n' "$gw_jvm_probe"
        printf '\n%s\n' "$gw_jvm_probe" >> "$gw_log"
      fi
      echo 'APPFORGE_AGP_RESOLUTION=UNRESOLVED'
    fi

    if [ "$gw_attempt" -lt "$gw_max" ] &&
       ! grep -Eiq "$gw_unsafe" "$gw_log" &&
       grep -Eiq "$gw_transient" "$gw_log"; then
      echo "APPFORGE_GRADLE_TRANSIENT_RETRY:$gw_kind:$gw_attempt/$gw_max"
      sleep "$((gw_attempt * 2))"
      gw_attempt=$((gw_attempt+1))
      continue
    fi

    echo "$gw_failed"
    echo "APPFORGE_GRADLE_ATTEMPTS=$gw_attempt"
    echo "----- GRADLE FAILURE -----"
    sed -n '/FAILURE:/,$p' "$gw_log" |
      grep -v '^[[:space:]]*at org\.gradle\.' |
      tail -120 || true
    echo "----- END GRADLE FAILURE -----"
    return "$gw_exit"
  done
}

case "$MODE" in

  android)

    PROJECT="$BASE/android"

    rm -rf "$PROJECT"

    mkdir -p \
      "$PROJECT/app/src/main"

    cat > "$PROJECT/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(
        RepositoriesMode.FAIL_ON_PROJECT_REPOS
    )

    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AppForgeOfflineAndroidWarmup"

include(":app")
EOF

    cat > "$PROJECT/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application") version "9.1.1" apply false
}
EOF

    cat > "$PROJECT/app/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application")
}

android {
    namespace = "com.appforge.offlinewarmup"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.appforge.offlinewarmup"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
}
EOF

    cat > "$PROJECT/app/src/main/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="false"
        android:label="AppForge Offline Warmup" />
</manifest>
EOF

    write_gradle_properties \
      "$PROJECT"

    LOG="$BASE/android-gradle.log"
    gradle_warmup "$PROJECT" "$LOG" "ANDROID" 62
    ;;


  node)

    command -v node >/dev/null
    command -v npm >/dev/null

    node --version
    npm --version

    npm cache verify

    echo "APPFORGE_OFFLINE_NODE_TOOLCHAIN=PASS"
    echo "APPFORGE_NODE_PROJECT_DEPENDENCIES=PROJECT_VERSION_DEPENDENT"
    ;;


  python)

    PROJECT="$BASE/python"

    rm -rf "$PROJECT"

    mkdir -p "$PROJECT"

    cp -a \
      /workspace/runtime/python-template/. \
      "$PROJECT/"

    mkdir -p \
      "$PROJECT/app/src/main/python"

    if [ ! -f "$PROJECT/app/src/main/python/main.py" ]; then
      cat > "$PROJECT/app/src/main/python/main.py" <<'EOF'
def main():
    return "AppForge offline Python warmup"
EOF
    fi

    : > \
      "$PROJECT/app/requirements.txt"

    write_gradle_properties \
      "$PROJECT"

    LOG="$BASE/python-gradle.log"
    gradle_warmup "$PROJECT" "$LOG" "PYTHON" 63
    ;;


  *)

    echo "Unknown offline pack mode: $MODE" >&2
    exit 61
    ;;

esac
