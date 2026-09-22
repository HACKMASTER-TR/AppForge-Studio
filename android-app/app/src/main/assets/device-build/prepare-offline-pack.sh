#!/bin/sh
set -eu

MODE="${1:?Offline pack mode required}"

ROOT="/opt/appforge-device"
SDK="$ROOT/android-sdk"
GRADLE="$ROOT/gradle-9.3.1/bin/gradle"
BASE="/workspace/.appforge-offline-prewarm"

export JAVA_HOME="$ROOT/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="$SDK"
export ANDROID_HOME="$SDK"
export GRADLE_USER_HOME="/root/.gradle-appforge"

test -x "$JAVA_HOME/bin/java"
test -x "$GRADLE"
test -f "$SDK/platforms/android-37.0/android.jar"
test -x "$SDK/build-tools/36.0.0/aapt2"

mkdir -p "$BASE"

write_gradle_properties() {
  target="$1"

  cat > "$target/gradle.properties" <<'EOF'
android.aapt2FromMavenOverride=/opt/appforge-device/android-sdk/build-tools/36.0.0/aapt2
org.gradle.workers.max=2
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
EOF

  cat > "$target/local.properties" <<'EOF'
sdk.dir=/opt/appforge-device/android-sdk
EOF
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

    "$GRADLE" \
      -p "$PROJECT" \
      --no-daemon \
      --stacktrace \
      :app:assembleDebug

    echo "APPFORGE_OFFLINE_ANDROID_PREWARM=PASS"
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

    "$GRADLE" \
      -p "$PROJECT" \
      --no-daemon \
      --stacktrace \
      :app:assembleDebug

    echo "APPFORGE_OFFLINE_PYTHON_PREWARM=PASS"
    ;;


  *)

    echo "Unknown offline pack mode: $MODE" >&2
    exit 61
    ;;

esac
