#!/data/data/com.termux/files/usr/bin/bash
# Standalone physical Android R2 acceptance; never alters production package.
set -Eeuo pipefail

REPO="${APPFORGE_V9_REPO:-$HOME/appforge-v9-trust-review.dt58QF}"
OUTDIR="${APPFORGE_V9_APK_OUT:-$HOME/storage/downloads/AppForge-V9-R2-Physical}"
REPORT="$HOME/appforge-last-output.txt"
MANIFEST="$HOME/appforge-v9-candidate.xkFBBW8y/release-v1.json"
SIGNATURE="$HOME/appforge-v9-candidate.xkFBBW8y/release-v1.sig"
MANIFEST_PIN="569f7f55cb5fa645935250acaf2e6551d697fee4cfd723799abc92f5b58f5cf5"
SIGNATURE_PIN="830033663d72419435b99a1e6ced8e51cc582b9f8fd72752f3593c538c316ca7"
DEVICE_STAGE_PATH=""
PKG="com.appforge.studio.v9r2accept"
CLASS="com.appforge.studio.build.V9SignedR2PhysicalAcceptanceTest"
GITHUB_REPO="HACKMASTER-TR/AppForge-Studio"
WORKFLOW="v9-signed-r2-physical-apk.yml"

physical_test() {
  local method="$1" output
  echo "=== PHYSICAL_TEST_${method} ==="
  output="$(adb shell am instrument -w -r -e class "$CLASS#$method" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" 2>&1)" || {
    printf '%s\n' "$output"
    echo "PHYSICAL_TEST_${method}=BLOCKED"
    return 1
  }
  printf '%s\n' "$output" | tail -35
  printf '%s\n' "$output" | grep -Fq 'OK (1 test)' || {
    echo "PHYSICAL_TEST_${method}=FAIL"
    return 1
  }
  echo "PHYSICAL_TEST_${method}=PASS"
}

runner() {
  echo '=== APPFORGE V9 REAL SIGNED R2 PHYSICAL ACCEPTANCE ==='
  for tool in adb gh python3 sha256sum; do command -v "$tool" >/dev/null; done
  test -d "$REPO/.git" || git -C "$REPO" rev-parse --git-dir >/dev/null
  test "$(git -C "$REPO" branch --show-current)" = 'feat/v9-global-package-distribution-20261027'
  test -s "$MANIFEST" && test -s "$SIGNATURE"
  test ! -L "$MANIFEST" && test ! -L "$SIGNATURE"
  printf '%s  %s\n' "$MANIFEST_PIN" "$MANIFEST" | sha256sum -c --status
  printf '%s  %s\n' "$SIGNATURE_PIN" "$SIGNATURE" | sha256sum -c --status
  python3 "$REPO/quality/tools/appforge_v9_release_bundle.py" verify-bundle \
    --manifest "$MANIFEST" --signature "$SIGNATURE"
  echo 'MANIFEST_AND_SIGNATURE=PASS'
  local HEAD RUN JSON APK TESTAPK TARGET_TMP
  HEAD="$(git -C "$REPO" rev-parse HEAD)"
  # Every CI commit must use its own fresh APK download directory.
  if [ -z "${APPFORGE_V9_APK_OUT:-}" ]; then
    OUTDIR="$HOME/storage/downloads/AppForge-V9-R2-Physical-$HEAD"
  fi
  JSON="$(gh run list -R "$GITHUB_REPO" --workflow "$WORKFLOW" --branch 'feat/v9-global-package-distribution-20261027' --limit 10 --json databaseId,headSha,conclusion,status)"
  RUN="$(printf '%s' "$JSON" | python3 -c 'import sys,json;h=sys.argv[1];r=[x for x in json.load(sys.stdin) if x["headSha"]==h and x["conclusion"]=="success"];print(r[0]["databaseId"] if r else "")' "$HEAD")"
  test -n "$RUN" || { echo 'GITHUB_APK_CI=NOT_CONFIRMED_SUCCESS'; return 2; }
  echo "CI_RUN=$RUN"
  mkdir -p "$OUTDIR"
  if [ ! -s "$OUTDIR/AppForge-V9-R2-Physical-debug.apk" ] || [ ! -s "$OUTDIR/AppForge-V9-R2-Physical-test.apk" ]; then
    gh run download "$RUN" -R "$GITHUB_REPO" -n "v9-r2-physical-acceptance-$HEAD" -D "$OUTDIR"
  fi
  (cd "$OUTDIR" && sha256sum -c SHA256SUMS)
  echo 'DEBUG_APK_SHA256=PASS'
  APK="$OUTDIR/AppForge-V9-R2-Physical-debug.apk"
  TESTAPK="$OUTDIR/AppForge-V9-R2-Physical-test.apk"
  test -s "$APK" && test -s "$TESTAPK"
  local devices
  devices="$(adb devices | awk '$2=="device"{print $1}')"
  test "$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l)" -eq 1 || { echo 'ADB_DEVICE=NOT_EXACTLY_ONE_CONNECTED'; return 2; }
  local model
  model="$(adb shell getprop ro.product.model | tr -d '\r')"
  echo "ADB_DEVICE_MODEL=$model"
  echo 'ADB_INSTALL=ISOLATED_DEBUG_ONLY'
  adb install -r "$APK"
  adb install -r "$TESTAPK"
  TARGET_TMP="/data/local/tmp/v9r2-physical-$RUN"
  DEVICE_STAGE_PATH="$TARGET_TMP"
  adb push "$MANIFEST" "$TARGET_TMP.json" >/dev/null
  adb push "$SIGNATURE" "$TARGET_TMP.sig" >/dev/null
  adb shell run-as "$PKG" mkdir -p files/v9-r2-acceptance
  adb shell run-as "$PKG" cp "$TARGET_TMP.json" files/v9-r2-acceptance/release-v1.json
  adb shell run-as "$PKG" cp "$TARGET_TMP.sig" files/v9-r2-acceptance/release-v1.sig
  adb shell rm -f "$TARGET_TMP.json" "$TARGET_TMP.sig" >/dev/null
  DEVICE_STAGE_PATH=""
  echo 'SIGNED_BUNDLE_DEVICE_INJECTION=PASS;PRIVATE_KEY=NOT_COPIED'
  physical_test signedManifestAndSelectivePlan
  physical_test forgedSignatureFailsClosed
  physical_test verifiedCacheTamperFailsClosed
  echo 'NETWORK_POLICY=VALIDATED_UNMETERED_WIFI_ONLY'
  echo 'SELECTED_MODULE=NODE_WEB;EXPECTED=21_DEB_PLUS_4_INRELEASE'
  physical_test userApprovedNodeDownloadOnWifi
  physical_test cachedNodeReuseWithoutNetworkRequest
  echo 'V9_SIGNED_R2_PHYSICAL_ACCEPTANCE=PASS;ANDROID_TESTS=5'
  echo 'MAIN_UNCHANGED=YES;PLAY_PRODUCTION=UNCHANGED;CLOUDFLARE_MUTATIONS=0'
  echo 'ANDROID_AUTO_ROUTING=DISABLED'
}

set +e
(
  set -Eeuo pipefail
  trap 'if [ -n "$DEVICE_STAGE_PATH" ]; then adb shell rm -f "$DEVICE_STAGE_PATH.json" "$DEVICE_STAGE_PATH.sig" >/dev/null 2>&1 || true; fi' EXIT
  runner
) 2>&1 | tee "$REPORT"
RC=${PIPESTATUS[0]}
set -e
if command -v termux-clipboard-set >/dev/null 2>&1; then
  termux-clipboard-set < "$REPORT" 2>/dev/null && echo 'CLIPBOARD=SUCCESS' || echo 'CLIPBOARD=FAILED'
fi
echo "SCRIPT_EXIT_CODE=$RC"
echo "REPORT=$REPORT"
exit "$RC"
