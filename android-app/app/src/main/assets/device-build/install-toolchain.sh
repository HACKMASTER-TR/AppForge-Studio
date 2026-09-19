#!/bin/sh
set -eu

ROOT="/opt/appforge-device"
SDK="$ROOT/android-sdk"
READY="$ROOT/.ready-v3"
JAVA_HOME="$ROOT/jdk-17"
ENGINE="${1:-webview-static}"

if [ -f "$READY" ] \
   && [ -x "$SDK/build-tools/36.0.0/aapt2" ] \
   && [ -f "$SDK/platforms/android-37/android.jar" ] \
   && [ -x "$ROOT/gradle-9.3.1/bin/gradle" ] \
   && [ -x "$JAVA_HOME/bin/java" ] \
   && [ -x "$JAVA_HOME/bin/javac" ]; then
  echo "APPFORGE_DEVICE_TOOLCHAIN_READY"
  exit 0
fi

export DEBIAN_FRONTEND=noninteractive
# A previous AppForge device-build attempt may have left Ubuntu's
# OpenJDK packages unpacked but unconfigured. Never purge a healthy
# installation; remove only broken/partial OpenJDK package states.
repair_broken_openjdk() {
  for pkg in \
    openjdk-17-jdk \
    openjdk-17-jdk-headless \
    openjdk-17-jre \
    openjdk-17-jre-headless
  do
    status="$(
      dpkg-query \
        -W \
        -f='${db:Status-Abbrev}' \
        "$pkg" \
        2>/dev/null \
        || true
    )"

    case "$status" in
      ""|ii*)
        ;;
      *)
        echo "APPFORGE_REPAIR_BROKEN_PACKAGE:$pkg:$status"
        dpkg \
          --purge \
          --force-all \
          "$pkg" \
          >/dev/null 2>&1 \
          || true
        ;;
    esac
  done
}

repair_broken_openjdk

# Older AppForge device-toolchain revisions installed Node/npm
# unconditionally. A failed installation can leave dozens of
# node-* packages in unpacked/half-configured state inside the
# persistent Ubuntu rootfs. Even an Android-only build can then
# trigger dpkg configuration of those stale packages during the
# next unrelated apt transaction.
#
# Preserve every healthy package. Remove only stale/broken Node/npm
# package states when the current source engine does not need Node.
repair_stale_node_packages() {
  [ "$ENGINE" = "node-web" ] && return 0

  dpkg-query \
    -W \
    -f='${binary:Package}\t${db:Status-Abbrev}\n' \
    'node-*' \
    'npm' \
    2>/dev/null \
  | while IFS="$(printf '\t')" read -r pkg status
    do
      [ -n "$pkg" ] || continue

      case "$status" in
        ii*)
          # Healthy package: leave it untouched.
          ;;

        *)
          echo "APPFORGE_REPAIR_STALE_NODE:$pkg:$status"

          dpkg \
            --purge \
            --force-all \
            "$pkg" \
            >/dev/null 2>&1 \
            || true
          ;;
      esac
    done

  # Clear stale package metadata/dependency state left by a
  # previously interrupted universal Node installation.
  dpkg --audit || true

  apt-get \
    -f install \
    -y \
    --no-install-recommends \
    >/dev/null 2>&1 \
    || true
}

repair_stale_node_packages

apt-get update
apt-get install -y --no-install-recommends \
  ca-certificates curl unzip zip xz-utils tar \
  git file \
  libstdc++6 zlib1g libpng16-16 \
  fontconfig libfreetype6

case "$ENGINE" in
  node-web)
    apt-get install -y --no-install-recommends nodejs npm
    ;;
  python-android)
    apt-get install -y --no-install-recommends python3 python3-pip python3-venv
    ;;
esac

mkdir -p "$ROOT" "$SDK/platforms" "$SDK/build-tools" "$ROOT/cache"

download_sha1() {
  url="$1"; sha="$2"; out="$3"
  if [ -s "$out" ] && echo "$sha  $out" | sha1sum -c - >/dev/null 2>&1; then return; fi
  rm -f "$out"
  curl -fL --retry 4 --connect-timeout 20 "$url" -o "$out"
  echo "$sha  $out" | sha1sum -c -
}

download_sha256() {
  url="$1"; sha="$2"; out="$3"
  if [ -s "$out" ] && echo "$sha  $out" | sha256sum -c - >/dev/null 2>&1; then return; fi
  rm -f "$out"
  curl -fL --retry 4 --connect-timeout 20 "$url" -o "$out"
  echo "$sha  $out" | sha256sum -c -
}

ensure_jdk() {
  [ -x "$JAVA_HOME/bin/java" ] \
    && [ -x "$JAVA_HOME/bin/javac" ] \
    && return

  arch="$(uname -m)"

  case "$arch" in
    aarch64|arm64)
      jdk_url="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.20.1_1.tar.gz"
      jdk_sha="457b57af8f9c93ec39080bb8c764f559dc8c89a6da1a39d718a400b7890d3e41"
      ;;

    x86_64|amd64)
      jdk_url="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz"
      jdk_sha="3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e"
      ;;

    *)
      echo "Unsupported device JDK architecture: $arch" >&2
      exit 41
      ;;
  esac

  archive="$ROOT/cache/temurin-jdk17-$arch.tar.gz"

  download_sha256 \
    "$jdk_url" \
    "$jdk_sha" \
    "$archive"

  rm -rf "$JAVA_HOME"
  mkdir -p "$JAVA_HOME"

  tar \
    -xzf "$archive" \
    -C "$JAVA_HOME" \
    --strip-components=1

  test -x "$JAVA_HOME/bin/java"
  test -x "$JAVA_HOME/bin/javac"
}

ensure_gradle() {
  version="$1"
  dest="$ROOT/gradle-$version"
  [ -x "$dest/bin/gradle" ] && return
  zip="$ROOT/cache/gradle-$version-bin.zip"
  sha_file="$ROOT/cache/gradle-$version-bin.zip.sha256"
  curl -fL --retry 4 "https://services.gradle.org/distributions/gradle-$version-bin.zip" -o "$zip"
  curl -fL --retry 4 "https://services.gradle.org/distributions/gradle-$version-bin.zip.sha256" -o "$sha_file"
  expected="$(tr -d '[:space:]' < "$sha_file")"
  echo "$expected  $zip" | sha256sum -c -
  rm -rf "$dest"
  unzip -q "$zip" -d "$ROOT"
  test -x "$dest/bin/gradle"
}

PLATFORM_ZIP="$ROOT/cache/platform-37.0_r02.zip"
download_sha1 \
  "https://dl.google.com/android/repository/platform-37.0_r02.zip" \
  "ed8ebf7f8822a4de5686d427f237d2fa30ff7410" \
  "$PLATFORM_ZIP"

rm -rf "$ROOT/platform-unpack" "$SDK/platforms/android-37"
mkdir -p "$ROOT/platform-unpack" "$SDK/platforms/android-37"
unzip -q "$PLATFORM_ZIP" -d "$ROOT/platform-unpack"
PLATFORM_JAR="$(find "$ROOT/platform-unpack" -type f -name android.jar | head -n 1)"
test -n "$PLATFORM_JAR"
PLATFORM_DIR="$(dirname "$PLATFORM_JAR")"
cp -a "$PLATFORM_DIR"/. "$SDK/platforms/android-37/"
test -f "$SDK/platforms/android-37/android.jar"

if [ ! -f "$SDK/platforms/android-37/source.properties" ]; then
  cat > "$SDK/platforms/android-37/source.properties" <<'EOF'
Pkg.Desc=Android SDK Platform 37
Pkg.Revision=2
AndroidVersion.ApiLevel=37
EOF
fi

OFFICIAL_BT="$ROOT/cache/build-tools_r36_linux.zip"
download_sha1 \
  "https://dl.google.com/android/repository/build-tools_r36_linux.zip" \
  "b0b6376977657e8ad9b969bacf4093601da2c6fb" \
  "$OFFICIAL_BT"

rm -rf "$ROOT/buildtools-official" "$SDK/build-tools/36.0.0"
mkdir -p "$ROOT/buildtools-official" "$SDK/build-tools/36.0.0"
unzip -q "$OFFICIAL_BT" -d "$ROOT/buildtools-official"
D8_PATH="$(find "$ROOT/buildtools-official" -type f -name d8 | head -n 1)"
test -n "$D8_PATH"
OFFICIAL_DIR="$(dirname "$D8_PATH")"
cp -a "$OFFICIAL_DIR"/. "$SDK/build-tools/36.0.0/"

ARM_BT="$ROOT/cache/build-tools-36-aarch64.zip"
download_sha256 \
  "https://github.com/Qjj7679/build-tools-36-aarch64/releases/download/36.0.0/build-tools.zip" \
  "ccbb8ad3b3dcd2c1e52b0c032d1101840d5539fdfbecf95a4fb6ec1d889b6703" \
  "$ARM_BT"

rm -rf "$ROOT/buildtools-arm64"
mkdir -p "$ROOT/buildtools-arm64"
unzip -q "$ARM_BT" -d "$ROOT/buildtools-arm64"
for tool in aapt aapt2 aidl split-select zipalign; do
  src="$(find "$ROOT/buildtools-arm64" -type f -name "$tool" | head -n 1)"
  test -n "$src"
  cp "$src" "$SDK/build-tools/36.0.0/$tool"
  chmod 0755 "$SDK/build-tools/36.0.0/$tool"
done

cat > "$SDK/build-tools/36.0.0/source.properties" <<'EOF'
Pkg.Desc=Android SDK Build-Tools 36
Pkg.Revision=36.0.0
EOF

ensure_jdk

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

ensure_gradle "9.3.1"
ensure_gradle "8.14.3"

cat > "$ROOT/ensure-gradle" <<'EOF'
#!/bin/sh
set -eu
version="${1:?Gradle version required}"
root="/opt/appforge-device"
dest="$root/gradle-$version"
if [ -x "$dest/bin/gradle" ]; then printf '%s\n' "$dest/bin/gradle"; exit 0; fi
zip="$root/cache/gradle-$version-bin.zip"
sha="$root/cache/gradle-$version-bin.zip.sha256"
curl -fL --retry 4 "https://services.gradle.org/distributions/gradle-$version-bin.zip" -o "$zip"
curl -fL --retry 4 "https://services.gradle.org/distributions/gradle-$version-bin.zip.sha256" -o "$sha"
expected="$(tr -d '[:space:]' < "$sha")"
echo "$expected  $zip" | sha256sum -c -
unzip -q -o "$zip" -d "$root"
test -x "$dest/bin/gradle"
printf '%s\n' "$dest/bin/gradle"
EOF
chmod 0755 "$ROOT/ensure-gradle"

"$SDK/build-tools/36.0.0/aapt2" version
"$ROOT/gradle-9.3.1/bin/gradle" --version >/dev/null
java -version

case "$ENGINE" in
  node-web)
    node --version
    npm --version
    ;;
  python-android)
    python3 --version
    ;;
esac

touch "$READY"
rm -rf "$ROOT/platform-unpack" "$ROOT/buildtools-official" "$ROOT/buildtools-arm64"
echo "APPFORGE_DEVICE_TOOLCHAIN_READY"
