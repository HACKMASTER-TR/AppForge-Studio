#!/data/data/com.termux/files/usr/bin/bash
# Persistently re-sign ONLY V9 isolated debug/test APKs with a local, private test key.
# No credentials/secrets are sent to CI, GitHub, Cloudflare or Play.
set -Eeuo pipefail
umask 077
REPO="${APPFORGE_V9_REPO:-$HOME/appforge-v9-trust-review.dt58QF}"
HEAD="$(git -C "$REPO" rev-parse HEAD)"
KEYROOT="$HOME/.config/appforge-v9-r2-local-debug"
STORE="$KEYROOT/test-only-debug.p12"
PASSFILE="$KEYROOT/test-only-password"
ALIAS="appforge-v9-r2-debug"
IN="${APPFORGE_V9_APK_INPUT:-$HOME/storage/downloads/AppForge-V9-R2-Physical-$HEAD}"
OUT="${APPFORGE_V9_SIGNED_APK_OUTPUT:-$HOME/storage/downloads/AppForge-V9-R2-LocalSigned-$HEAD}"

say() { printf '%s\n' "$*"; }
die() { say "SIGNING_GATE=BLOCKED;$*"; exit 2; }

say '=== APPFORGE V9 PERSISTENT ISOLATED DEBUG SIGNING ==='
for c in git java keytool openssl sha256sum; do command -v "$c" >/dev/null || die "MISSING_TOOL=$c"; done
[ "$(git -C "$REPO" branch --show-current)" = 'feat/v9-global-package-distribution-20261027' ] || die SOURCE_BRANCH
[ -s "$IN/SHA256SUMS" ] || die CI_APKS_NOT_DOWNLOADED
[ -s "$IN/AppForge-V9-R2-Physical-debug.apk" ] || die MAIN_DEBUG_APK_MISSING
[ -s "$IN/AppForge-V9-R2-Physical-test.apk" ] || die TEST_APK_MISSING
(cd "$IN" && sha256sum -c SHA256SUMS) || die CI_ARTIFACT_SHA256
say 'CI_APKS_SHA256=PASS'

# Use SDK tool, never a substitute that disables APK signature verification.
SIGNER="$(command -v apksigner || true)"
if [ -z "$SIGNER" ]; then
  SIGNER="$(find "$HOME" -type f -path '*/build-tools/*/apksigner' -print 2>/dev/null | sort -V | tail -1)"
fi
[ -n "$SIGNER" ] && [ -f "$SIGNER" ] || die APK_SIGNER_NOT_FOUND

mkdir -p "$KEYROOT"
[ ! -L "$KEYROOT" ] && [ ! -L "$STORE" ] && [ ! -L "$PASSFILE" ] || die UNSAFE_KEY_PATH
chmod 700 "$KEYROOT"
if [ -e "$STORE" ] && [ ! -s "$STORE" ]; then die EMPTY_PREVIOUS_KEY; fi
if [ ! -e "$STORE" ]; then
  [ ! -e "$PASSFILE" ] || die ORPHAN_PASSWORD_FILE
  (umask 077; openssl rand -hex 32 > "$PASSFILE") || die PASSWORD_GENERATION
  chmod 600 "$PASSFILE"
  export APPFORGE_V9_TEST_PASS="$(cat "$PASSFILE")"
  keytool -genkeypair -noprompt -storetype PKCS12 \
    -keystore "$STORE" -alias "$ALIAS" -keyalg RSA -keysize 3072 \
    -validity 3650 -dname 'CN=AppForge V9 Isolated Debug,OU=Test,O=AppForge,C=TR' \
    -storepass:env APPFORGE_V9_TEST_PASS -keypass:env APPFORGE_V9_TEST_PASS \
    >/dev/null 2>&1 || die KEY_CREATION_FAILED
  chmod 600 "$STORE"
  say 'LOCAL_TEST_KEY=CREATED_ON_DEVICE'
else
  [ -s "$PASSFILE" ] || die LOCAL_DEBUG_PASSWORD_MISSING
  export APPFORGE_V9_TEST_PASS="$(cat "$PASSFILE")"
  keytool -list -storetype PKCS12 -keystore "$STORE" \
    -alias "$ALIAS" -storepass:env APPFORGE_V9_TEST_PASS >/dev/null 2>&1 || die STORED_KEY_UNREADABLE
  say 'LOCAL_TEST_KEY=REUSED'
fi

[ ! -L "$OUT" ] || die UNSAFE_OUTPUT_DIRECTORY
if [ -e "$OUT" ]; then die OUTPUT_ALREADY_EXISTS_CHOOSE_NEW_PATH; fi
mkdir -m 700 "$OUT" || die OUTPUT_CREATE_FAILED
for spec in 'AppForge-V9-R2-Physical-debug.apk:AppForge-V9-R2-Physical-debug.apk' \
            'AppForge-V9-R2-Physical-test.apk:AppForge-V9-R2-Physical-test.apk'; do
  source="${spec%%:*}"; target="${spec##*:}"
  "$SIGNER" sign --ks "$STORE" --ks-key-alias "$ALIAS" \
    --ks-pass env:APPFORGE_V9_TEST_PASS --key-pass env:APPFORGE_V9_TEST_PASS \
    --out "$OUT/$target" "$IN/$source" || die "APK_RESIGN_FAILED=$target"
  "$SIGNER" verify --verbose --print-certs "$OUT/$target" > "$OUT/$target.verify.txt" || die "APK_VERIFY_FAILED=$target"
  say "APK_SIGN_AND_VERIFY=PASS;$target"
done

APP_CERT="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$OUT/AppForge-V9-R2-Physical-debug.apk.verify.txt" | head -1)"
TEST_CERT="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$OUT/AppForge-V9-R2-Physical-test.apk.verify.txt" | head -1)"
[[ "$APP_CERT" =~ ^[0-9a-fA-F]{64}$ ]] && [ "$APP_CERT" = "$TEST_CERT" ] || die LOCAL_APK_SIGNER_CONTINUITY
(cd "$OUT" && sha256sum AppForge-V9-R2-Physical-debug.apk AppForge-V9-R2-Physical-test.apk > SHA256SUMS && sha256sum -c SHA256SUMS) || die LOCAL_SIGNED_HASH
unset APPFORGE_V9_TEST_PASS
say "DEBUG_SIGNER_SHA256=$APP_CERT"
say 'DEBUG_SIGNER_CONTINUITY=PASS'
say "SIGNED_APK_DIR=$OUT"
say 'MANIFEST_ED25519_KEY=UNTOUCHED'
say 'PRODUCTION_PACKAGE=UNTOUCHED'
say 'MAIN_PLAY_CLOUDFLARE_MUTATIONS=0'
say 'DEVICE_INSTALL=NOT_PERFORMED'
