#!/bin/sh
set -eu

ROOT="/opt/appforge-device"
SDK="$ROOT/android-sdk"
READY="$ROOT/.ready-v1"

if [ -f "$READY" ] \
   && [ -x "$SDK/build-tools/36.0.0/aapt2" ] \
   && [ -f "$SDK/platforms/android-37/android.jar" ] \
   && [ -x "$ROOT/gradle-9.3.1/bin/gradle" ]; then
  echo "APPFORGE_DEVICE_TOOLCHAIN_READY"
  exit 0
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends \
  ca-certificates curl unzip zip xz-utils \
  openjdk-17-jdk-headless \
  python3 python3-pip python3-venv \
  nodejs npm git file \
  libstdc++6 zlib1g libpng16-16

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
node --version
npm --version
python3 --version

touch "$READY"
rm -rf "$ROOT/platform-unpack" "$ROOT/buildtools-official" "$ROOT/buildtools-arm64"
echo "APPFORGE_DEVICE_TOOLCHAIN_READY"
