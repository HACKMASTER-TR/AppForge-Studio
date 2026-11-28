#!/data/data/com.termux/files/usr/bin/bash
# Local ARM64 candidate only. Never installs, publishes, pushes or clears app data.
set -uo pipefail
umask 077
if [ "$#" -ne 6 ]; then
  echo "Usage: appforge-build preflight|build COMMIT PROFILE VERSION_CODE VERSION_NAME TEST_COUNT" >&2
  exit 2
fi
MODE=$1; COMMIT=$2; PROFILE=$3; VERSION=$4; NAME=$5; COUNT=$6
[[ "$MODE" == preflight || "$MODE" == build ]] || exit 2
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ && "$PROFILE" =~ ^appforgeR[0-9]+[A-Za-z0-9]+Candidate$ && "$VERSION" =~ ^[0-9]+$ && "$COUNT" =~ ^[1-9][0-9]*$ && "$NAME" =~ ^[A-Za-z0-9._-]+$ ]] || exit 2
WORK="$HOME/appforge-r70-build.yNDFXe7o"
REPO=https://github.com/HACKMASTER-TR/AppForge-Studio.git
NATIVE_BASE=f54c83905859eee941d3df628dd2c5b6f09eb206
SNAP=android-app/app/src/main/assets/second_brain_snapshot.json
LOG="$HOME/AppForge-v$VERSION-$MODE-$(date +%Y%m%d-%H%M%S).log"
OUT=$(mktemp -d "$HOME/appforge-local-v$VERSION.XXXXXXXX") || exit 1
(
set -euo pipefail
# mkdir lease prevents concurrent local builds/checkouts; never automatically remove another run's lock.
LOCK="$HOME/.appforge-local-build.lock"
mkdir "$LOCK" || { echo "LOCAL_BUILD_LOCKED=$LOCK"; exit 1; }
SECRET=""
trap 'if [ -n "$SECRET" ]; then rm -f -- "$SECRET"; fi; rmdir "$LOCK"' EXIT
echo "LOCAL_BUILD_MODE=$MODE"
cd "$WORK"
git diff --quiet HEAD -- . ":(exclude)$SNAP"
git -c credential.helper= -c 'credential.helper=!gh auth git-credential' fetch --depth=1 "$REPO" "$COMMIT"
if ! git diff --quiet HEAD -- "$SNAP"; then
  # Only accept a generated snapshot already equal to the requested commit.
  git show "$COMMIT:$SNAP" | cmp -s "$SNAP" -
fi
git checkout --detach "$COMMIT"
test "$(git rev-parse HEAD)" = "$COMMIT"
git diff --quiet HEAD --
echo "SOURCE_COMMIT=PASS"
if ! git cat-file -e "$NATIVE_BASE^{commit}" 2>/dev/null; then
  git -c credential.helper= -c 'credential.helper=!gh auth git-credential' fetch --depth=1 "$REPO" "$NATIVE_BASE"
fi
for DIR in android-app/app/src/main/cpp android-app/termux-terminal-emulator/src/main/jni; do
  test "$(git rev-parse "$COMMIT:$DIR")" = "$(git rev-parse "$NATIVE_BASE:$DIR")" || { echo "NATIVE_CHANGED=$DIR; rebuild native libraries before continuing"; exit 1; }
done
for P in android-app/app android-app/termux-terminal-emulator; do
  git show "$NATIVE_BASE:$P/build.gradle.kts" > "$OUT/$(basename "$P").native-base.gradle"
done
python - "$OUT" "$PROFILE" "$VERSION" "$NAME" <<'PY'
from pathlib import Path
import re,sys
out=Path(sys.argv[1])
def blocks(s,name):
    pattern=r"(?m)^[ \t]*"+re.escape(name)+r"\s*\{"
    result=[]
    for m in re.finditer(pattern,s):
        pos=m.end();depth=1
        while depth and pos<len(s):
            if s[pos]=="{":depth+=1
            elif s[pos]=="}":depth-=1
            pos+=1
        assert depth==0
        result.append(s[m.start():pos].strip())
    return result
for module in ("app","termux-terminal-emulator"):
    a=(Path("android-app")/module/"build.gradle.kts").read_text()
    b=(out/(module+".native-base.gradle")).read_text()
    for k in ("externalNativeBuild","ndk"):
        assert blocks(a,k)==blocks(b,k), "NATIVE_CONFIG_CHANGED:"+module+":"+k
    assert re.findall(r'(?m)^\s*ndkVersion\s*=.*$',a)==re.findall(r'(?m)^\s*ndkVersion\s*=.*$',b)
app=Path("android-app/app/build.gradle.kts").read_text()
assert re.search(r'if\s*\('+re.escape(sys.argv[2])+r'\)\s*\{\s*output.versionCode.set\('+sys.argv[3]+r'\)\s*output.versionName.set\("'+re.escape(sys.argv[4])+r'"\)',app), "PROFILE_VERSION_MISMATCH"
print("NATIVE_SOURCE_AND_CONFIG=PASS")
print("PROFILE_VERSION=PASS")
PY
python tests/test_termux_build_contract.py
python tests/test_ci_quota_policy.py
python tests/test_r58_r62_integrated_candidate.py
python scripts/generate-second-brain-snapshot.py
python scripts/generate-second-brain-snapshot.py --check
echo "SNAPSHOT_JSON_BEGIN"
cat "$SNAP"
echo "SNAPSHOT_JSON_END"
if [ "$MODE" = build ]; then
  SECRET=$(mktemp "$HOME/.appforge-sign.XXXXXXXX")
  printf 'Orijinal imza anahtari sifresi: ' > /dev/tty
  IFS= read -rs PASSWORD < /dev/tty
  printf '\n' > /dev/tty
  printf '%s' "$PASSWORD" > "$SECRET"
  unset PASSWORD
fi
proot-distro login ubuntu --bind "$HOME:/mnt/afhome" -- /bin/bash -s -- "$MODE" "$(basename "$OUT")" "$(basename "$SECRET")" "$PROFILE" "$VERSION" "$NAME" "$COUNT" <<'UBUNTU'
set -euo pipefail
umask 077
unset LD_PRELOAD LD_LIBRARY_PATH
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export PATH="$JAVA_HOME/bin:/usr/bin:/bin"
export LC_ALL=C
export ANDROID_HOME=/mnt/afhome/appforge-v9-android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
WORK=/mnt/afhome/appforge-r70-build.yNDFXe7o
OUT="/mnt/afhome/$2"
GRADLE=/mnt/afhome/.local/gradle/gradle-9.3.1/bin/gradle
AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
SIGNER="$ANDROID_HOME/build-tools/36.0.0/lib/apksigner.jar"
CERT=f04693e62080489f41554ff357bab0789bf87b864aa8c5a9927279667de9d91b
export BASEAPK=/mnt/afhome/appforge-r70-verified.8r4VBvlL/AppForgeStudio-v586.apk
cd "$WORK"
/usr/bin/curl --version >/dev/null
java -version
test -x "$GRADLE"
"$AAPT2" version
test -s "$SIGNER"
echo "ff2801f9b11eb09d50058656a4e3801ff42ad6ed14ed35431fdacae599416ff4  $BASEAPK" | sha256sum -c -
java -jar "$SIGNER" verify --print-certs "$BASEAPK" | grep -Fi "certificate SHA-256 digest: $CERT"
test -s /mnt/afhome/AppForge-Studio/appforge-debug.p12
echo "LOCAL_ENVIRONMENT=PASS"
if [ "$1" = preflight ]; then
  touch "$OUT/PREFLIGHT_PASS"
  exit 0
fi
export APPFORGE_R31_KEYSTORE_PATH=/mnt/afhome/AppForge-Studio/appforge-debug.p12
export APPFORGE_R31_STORE_PASSWORD="$(cat "/mnt/afhome/$3")"
rm -f "/mnt/afhome/$3"
CERTFILE="$OUT/certificate.der"
keytool -exportcert -storetype PKCS12 -alias appforge-debug -keystore "$APPFORGE_R31_KEYSTORE_PATH" -storepass:env APPFORGE_R31_STORE_PASSWORD -file "$CERTFILE"
test "$(sha256sum "$CERTFILE" | cut -d ' ' -f1)" = "$CERT"
echo "ORIGINAL_KEYSTORE=PASS"
APP="$WORK/android-app/app/build.gradle.kts"
TERMINAL="$WORK/android-app/termux-terminal-emulator/build.gradle.kts"
cp "$APP" "$OUT/app.gradle.original"
cp "$TERMINAL" "$OUT/terminal.gradle.original"
# Absolute paths: never depend on final working directory. Restore before success sentinel.
restore() {
  cp "$OUT/app.gradle.original" "$APP" &&
  cp "$OUT/terminal.gradle.original" "$TERMINAL"
}
trap 'restore' EXIT
python3 <<'PY'
from pathlib import Path
import re,os,zipfile,hashlib
def remove_blocks(s,name):
    pattern=r"(?m)^[ \t]*"+re.escape(name)+r"\s*\{"
    while True:
        m=re.search(pattern,s)
        if not m:return s
        pos=m.end();depth=1
        while depth and pos<len(s):
            if s[pos]=="{":depth+=1
            elif s[pos]=="}":depth-=1
            pos+=1
        assert depth==0
        s=s[:m.start()]+s[pos:]
with zipfile.ZipFile(os.environ["BASEAPK"]) as z:
    for module,lib,expected in (
        ("app","libappforge_pty.so","380b63ce73483dff3777d2e73699da8eed903a382d0ff214cfe62b7a205977bb"),
        ("termux-terminal-emulator","libtermux.so","946799ddae931604ec94937f5f920591e6d3d559d895049a88a453a2f5c387a0")):
        root=Path("android-app")/module
        p=root/"build.gradle.kts"
        s=remove_blocks(remove_blocks(p.read_text(),"externalNativeBuild"),"ndk")
        s=re.sub(r'(?m)^\s*ndkVersion\s*=\s*"[^"]*"\s*$',"",s)
        if module=="app":
            marker='keepDebugSymbols += "**/libproroot*.so"'
            assert s.count(marker)==1
            s=s.replace(marker,'keepDebugSymbols += "**/*.so"')
        else:
            s=s.replace("android {",'android {\n    packaging {\n        jniLibs {\n            keepDebugSymbols += "**/*.so"\n        }\n    }\n',1)
        data=z.read("lib/arm64-v8a/"+lib)
        assert hashlib.sha256(data).hexdigest()==expected
        dest=root/"src/main/jniLibs/arm64-v8a"/lib
        dest.parent.mkdir(parents=True,exist_ok=True)
        assert not dest.is_symlink()
        if dest.exists():assert dest.read_bytes()==data
        else:dest.write_bytes(data)
        p.write_text(s)
        print(lib+"=PASS")
PY
"$GRADLE" -p android-app :app:testDebugUnitTest :app:assembleDebug "-P$4=true" "-Pandroid.aapt2FromMavenOverride=$AAPT2" --no-daemon --no-watch-fs --max-workers=2 --no-configuration-cache --console=plain
APK="$WORK/android-app/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
python3 - "$APK" "$7" <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
import sys,os,zipfile
reports=list(Path("android-app/app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
assert reports
roots=[ET.parse(f).getroot() for f in reports]
totals={k:sum(int(r.get(k,0)) for r in roots) for k in ("tests","failures","errors","skipped")}
print("UNIT_TEST_TOTALS="+str(totals))
assert totals==dict(tests=int(sys.argv[2]),failures=0,errors=0,skipped=0)
with zipfile.ZipFile(sys.argv[1]) as new:
    assert new.testzip() is None
    with zipfile.ZipFile(os.environ["BASEAPK"]) as old:
        for lib in ("libappforge_pty.so","libtermux.so"):
            p="lib/arm64-v8a/"+lib
            assert new.read(p)==old.read(p)
print("APK_AND_TESTS=PASS")
PY
"$AAPT2" dump badging "$APK" > "$OUT/badging.txt"
grep -F "package: name='com.appforge.studio' versionCode='$5' versionName='$6'" "$OUT/badging.txt"
java -jar "$SIGNER" verify --verbose --print-certs "$APK" > "$OUT/signature.txt"
grep -Fi "certificate SHA-256 digest: $CERT" "$OUT/signature.txt"
grep -F "Verified using v2 scheme (APK Signature Scheme v2): true" "$OUT/signature.txt"
cp "$APK" "$OUT/AppForgeStudio-v$5.apk"
(cd "$OUT"; sha256sum "AppForgeStudio-v$5.apk" > SHA256SUMS.txt; sha256sum -c SHA256SUMS.txt)
restore
trap - EXIT
touch "$OUT/VERIFIED_PASS"
UBUNTU
if [ "$MODE" = preflight ]; then
  test -f "$OUT/PREFLIGHT_PASS"
  echo "PREFLIGHT=PASS"
  echo "APK_BUILD=NO"
else
  test -f "$OUT/VERIFIED_PASS"
  git diff --quiet HEAD -- . ":(exclude)$SNAP"
  (cd "$OUT"; sha256sum -c SHA256SUMS.txt)
  DEST="/sdcard/Download/AppForgeStudio-v$VERSION.apk"
  if [ -e "$DEST" ]; then
    cmp "$OUT/AppForgeStudio-v$VERSION.apk" "$DEST" || { echo "DESTINATION_EXISTS_DIFFERENT=$DEST"; exit 1; }
  else
    cp "$OUT/AppForgeStudio-v$VERSION.apk" "$DEST"
    cmp "$OUT/AppForgeStudio-v$VERSION.apk" "$DEST"
  fi
  sha256sum "$DEST"
  echo "APK_READY=$DEST"
  echo "BUILD=PASS"
fi
echo "APP_DATA_CHANGED=NO"
echo "LOCAL_BUILD=PASS"
) 2>&1 | tee "$LOG"
RESULT=$?
echo "EXIT_CODE=$RESULT" | tee -a "$LOG"
{
  head -n 65 "$LOG"
  echo "=== ERROR DIAGNOSTICS ==="
  grep -A 15 -E 'What went wrong|FAILED|^e: |^FAILURE:' "$LOG" | head -n 80 || true
  echo "=== FINAL RESULT ==="
  tail -n 65 "$LOG"
} | termux-clipboard-set && echo "CLIPBOARD=PASS"
echo "FULL_LOG=$LOG"
exit "$RESULT"
