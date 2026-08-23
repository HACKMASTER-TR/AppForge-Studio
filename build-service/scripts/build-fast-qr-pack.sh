#!/usr/bin/env bash

set -euo pipefail

ROOT="/app/fast-sdk-bundle/build/qr-bundle"
SOURCE="/app/fast-runtime/FastQrRuntime.java"

TMP="/tmp/appforge-fast-qr"
OUT="/opt/appforge-fast-features/qr"

ANDROID_JAR="$ANDROID_HOME/platforms/android-37.0/android.jar"
D8="$ANDROID_HOME/build-tools/36.0.0/d8"

rm -rf "$TMP" "$OUT"

mkdir -p \
  "$TMP/aar" \
  "$TMP/classes" \
  "$OUT"

if [ ! -d "$ROOT" ]; then
    echo "ERROR: QR bundle bulunamadı: $ROOT"
    exit 20
fi

if [ ! -f "$SOURCE" ]; then
    echo "ERROR: FastQrRuntime.java bulunamadı."
    exit 21
fi

if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar bulunamadı."
    exit 22
fi

if [ ! -x "$D8" ]; then
    echo "ERROR: D8 bulunamadı."
    exit 23
fi


CLASSPATH="$ANDROID_JAR"

D8_INPUTS=()


echo "=== QR AAR classes.jar hazırlanıyor ==="

for aar in "$ROOT"/*.aar; do
    [ -f "$aar" ] || continue

    name="$(
        basename "$aar" .aar
    )"

    dir="$TMP/aar/$name"

    mkdir -p "$dir"

    if unzip -l "$aar" 2>/dev/null |
       awk '{print $4}' |
       grep -qx 'classes.jar'
    then
        unzip -p \
          "$aar" \
          classes.jar \
          > "$dir/classes.jar"

        if [ -s "$dir/classes.jar" ]; then
            CLASSPATH="$CLASSPATH:$dir/classes.jar"

            D8_INPUTS+=(
                "$dir/classes.jar"
            )
        fi
    fi
done


echo "=== QR JAR bağımlılıkları hazırlanıyor ==="

for jar in "$ROOT"/*.jar; do
    [ -f "$jar" ] || continue

    CLASSPATH="$CLASSPATH:$jar"

    D8_INPUTS+=(
        "$jar"
    )
done


if [ "${#D8_INPUTS[@]}" -eq 0 ]; then
    echo "ERROR: QR için D8 input bulunamadı."
    exit 24
fi


echo "=== FastQrRuntime javac ==="

javac \
  -encoding UTF-8 \
  -source 17 \
  -target 17 \
  -classpath "$CLASSPATH" \
  -d "$TMP/classes" \
  "$SOURCE"


echo "=== QR feature pack D8 ==="

mapfile -d '' RUNTIME_CLASSES < <(
    find "$TMP/classes" \
      -type f \
      -name '*.class' \
      -print0
)

if [ "${#RUNTIME_CLASSES[@]}" -eq 0 ]; then
    echo "ERROR: FastQrRuntime class dosyası oluşmadı."
    exit 26
fi

"$D8" \
  --release \
  --min-api 26 \
  --lib "$ANDROID_JAR" \
  --output "$OUT" \
  "${RUNTIME_CLASSES[@]}" \
  "${D8_INPUTS[@]}"


DEX_COUNT="$(
    find "$OUT" \
      -maxdepth 1 \
      -type f \
      -name 'classes*.dex' |
    wc -l
)"


if [ "$DEX_COUNT" -lt 1 ]; then
    echo "ERROR: QR DEX oluşturulamadı."
    exit 25
fi


echo
echo "===== APPFORGE FAST QR PACK ====="

ls -lh "$OUT"/*.dex

echo
echo "DEX_COUNT=$DEX_COUNT"

echo "✅ FAST QR feature pack hazır."

rm -rf "$TMP"
