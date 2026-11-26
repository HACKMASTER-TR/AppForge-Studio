#!/usr/bin/env python3
"""Regression contracts for candidate only; physical tests remain mandatory."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1]
base=root/'android-app/app/src/main/assets/device-build'
for name in ['install-windows-native-toolchain.sh','apt-snapshot-lock.sh','install-toolchain.sh','module-functional-test.sh']:
    subprocess.run(['sh','-n',str(base/name)],check=True)
w=(base/'install-windows-native-toolchain.sh').read_text()
a=(base/'apt-snapshot-lock.sh').read_text()
e=(base/'install-toolchain.sh').read_text()
f=(base/'module-functional-test.sh').read_text()
k=(root/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
g=(root/'android-app/app/build.gradle.kts').read_text()
assert 'repair_mingw_links || return $?' in w
assert 'APPFORGE_R63_WINDOWS_APT_REPAIR_ONLY=1 /bin/sh' in a
assert a.index('APPFORGE_R63_WINDOWS_APT_REPAIR_ONLY=1 /bin/sh') < a.index('as_inventory > "$as_transaction/installed.tsv"')
assert '[ "$as_install_rc" = 100 ] && [ "$as_owner" = WINDOWS_NATIVE ]' in a
assert 'as_verify_lock "$as_transaction" || return 60' in a
assert 'have_windows_lock || exit 60' in w
assert 'require_direct_windows_drivers || exit $?' in w
assert 'APPFORGE_R63_WINDOWS_REENTRY=1 $command' in k and 'windowsProrootReentry < 2' in k
assert 'APPFORGE_WINDOWS_NATIVE_SAME_GENERATION_RETRY=BLOCKED' in w
assert 'file -L "$smoke_cmake"' in e and 'file -L "$smoke_ninja"' in e
assert 'smoke_cmake="$ROOT/ndk-host-v7/sysroot/usr/bin/cmake"' in e
assert 'rust-android.py --probe-linker --sdk "$ROOT/android-sdk"' in f
assert 'APPFORGE_R63_NATIVE_API26_FUNCTIONAL_LINK=PASS' in f
assert 'historicalR63=true' in k
assert 'output.versionCode.set(580)' in g
assert 'output.versionName.set("5.0.78-r63-physical-repair")' in g
print('R63_SOURCE_REGRESSION_CONTRACTS=PASS_NOT_PHYSICAL_ACCEPTANCE')
