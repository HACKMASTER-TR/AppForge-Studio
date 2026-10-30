#!/usr/bin/env python3
"""Stage 5E.6 fail-closed source audit. Read-only, no network, no APK build."""
from __future__ import annotations
import argparse
import hashlib
import re
import subprocess
from pathlib import Path

V9 = 'befab2ac58fae62f8ae2f18e897fbce269807f88'
OLD = {
  'android-app/app/src/main/java/com/appforge/studio/security/V9AdminHttpTransport.kt': '3376afcf8d69fdbd116cce994f7344c6dba366ead4c61ab6fc70e57eae2c4c8d',
  'android-app/app/src/main/java/com/appforge/studio/security/V9AdminProofWire.kt': '6b782cae9906a471b00b4307087144a713380f1101f03d5b33ba2facd16d453a',
  'android-app/app/src/test/java/com/appforge/studio/security/V9AdminProofWireOfflineTest.kt': '5cc12033943c5a87444522e70f15022e5d183188fa46df6453cd624e6a675c8d',
  'cloudflare/v9-witness/device-auth/stage5e4-admin-gateway.mjs': '60f25536997a880ed7752408be16fa656ba12c9e48c0c3da18fd9d29e748df45',
  'cloudflare/v9-witness/device-auth/stage5e4-admin-gateway.test.mjs': '7cee417a1da6f3ff91b450f0bd1624cafbba918570d6aaee1e7aee0525c73b51',
  'docs/engineering/appforge-v9-stage5e4-admin-transport.md': '80c9c8c081c2e18164e99569602ea51caa6335b185414578f6059a803b5edd69',
  'cloudflare/v9-witness/device-auth/stage5e5-admin-proof.mjs': 'aae7bb6cdf6d9ac0b638416749f4fb3573782eceee8afa14c6b2a16fed375d90',
  'cloudflare/v9-witness/device-auth/stage5e5-admin-proof.test.mjs': '8d909e61f00a59d61f1cf4dd565c084bd31e985ff31f863fa8fd98a9dc35489d',
  'android-app/app/src/test/java/com/appforge/studio/security/V9AdminP256SigningOfflineTest.kt': 'b578429c7fd624aa0060de1e3aa65c757eff0892f08578e23e4cb4ffcff92fb6',
  'android-app/app/src/main/java/com/appforge/studio/security/V9AdminKeystoreRequestSigner.kt': '6b5e782930781dd3128fc33041975ffa054ac138246b3d6e74d0ecc202aea9c2',
  'android-app/app/src/main/java/com/appforge/studio/security/V9AdminP256SigningCore.kt': '4945893a0ac27faf4a5a390ee2b703db0d733f4048b9d0415f9dbb992356b943',
  'docs/engineering/appforge-v9-stage5e5-p256-admin-proof.md': 'ac0e85db7360126f69b54461d103267df1eb83ff380786319a4c79a1ed3d30a2',
}
NEW = (
 '.github/workflows/v9-stage5e6-android-keystore-compile-only.yml',
 'quality/v9-stage5e6-keystore-probe/settings.gradle.kts',
 'quality/v9-stage5e6-keystore-probe/build.gradle.kts',
 'quality/v9-stage5e6-keystore-probe/src/main/AndroidManifest.xml',
 'quality/v9-stage5e6-keystore-probe/src/main/java/com/appforge/studio/security/V9S5E6KeystoreProbeActivity.kt',
 'quality/tools/appforge_v9_stage5e6_preflight.py',
 'quality/tests/test_v9_stage5e6_contract.py',
 'docs/engineering/appforge-v9-stage5e6-keystore-acceptance.md',
)

def git(repo: Path, *args: str) -> str:
    return subprocess.check_output(['git', '-C', str(repo), *args], text=True).strip()

def check(repo: Path, ci: bool = False) -> None:
    assert (repo/'.git').exists(), 'repository missing'
    assert git(repo, 'diff', '--name-only') == '', 'tracked source modified'
    assert git(repo, 'diff', '--cached', '--name-only') == '', 'staged tracked source changed'
    actual_head = git(repo, 'rev-parse', 'HEAD')
    if ci:
        assert git(repo, 'rev-parse', 'HEAD^') == V9, 'CI commit parent mismatch'
        diff = set(git(repo, 'diff-tree', '--no-commit-id', '--name-only', '-r', 'HEAD').splitlines())
        assert diff == set(OLD) | set(NEW), 'CI committed scope changed'
    else:
        assert actual_head == V9, 'baseline changed'
        untracked = set(git(repo, 'ls-files', '--others', '--exclude-standard').splitlines())
        assert untracked == set(OLD) | set(NEW), f'untracked scope mismatch count={len(untracked)}'
    for path, expected in OLD.items():
        f = repo/path
        assert f.is_file() and not f.is_symlink(), f'missing/unsafe {path}'
        assert hashlib.sha256(f.read_bytes()).hexdigest() == expected, f'old source changed: {path}'
    for path in NEW:
        f = repo/path
        assert f.is_file() and not f.is_symlink(), f'new file missing/unsafe: {path}'
    print('S5E6_SOURCE_SCOPE=PASS;OLD=12;NEW=8;TRACKED_UNCHANGED=YES')

    root = repo/'quality/v9-stage5e6-keystore-probe'
    manifest = (root/'src/main/AndroidManifest.xml').read_text()
    assert 'android.permission.INTERNET' not in manifest, 'probe network permission forbidden'
    assert 'android:allowBackup="false"' in manifest, 'backup must be disabled'
    assert 'usesCleartextTraffic="false"' in manifest, 'cleartext denied'
    assert '<uses-permission' not in manifest, 'probe cannot request permissions'
    build = (root/'build.gradle.kts').read_text()
    assert 'applicationId = "com.appforge.v9.keystoreprobe"' in build
    assert 'compileSdk = 37' in build
    assert ':app:' not in build and 'implementation(project(' not in build
    assert not re.search(r'assemble|bundle|publish|signingConfig', build, re.I), 'unsafe build task'
    kotlin = (root/'src/main/java/com/appforge/studio/security/V9S5E6KeystoreProbeActivity.kt').read_text()
    for value in ('KeyPairGenerator.getInstance(', '"AndroidKeyStore"', '"secp256r1"', 'V9AdminKeystoreRequestSigner(', 'encoded == null', 'deleteEntry(alias)', 'S5E6_PHYSICAL_KEYSTORE=PASS', 'S5E6_ADMIN_ENROLLMENT=NO'):
        assert value in kotlin, f'missing physical check: {value}'
    workflow = (repo/NEW[0]).read_text()
    for value in ("'on':", 'persist-credentials: false', 'contents: read', ':app:compileDebugKotlin', ':app:compileDebugUnitTestKotlin', ':compileDebugKotlin', 'S5E6_COMPILE_ACCEPTANCE=PASS'):
        assert value in workflow, f'missing CI control: {value}'
    assert 'secrets.' not in workflow and 'wrangler ' not in workflow
    assert not re.search(r'\b:app:(?:assemble|bundle)', workflow), 'AppForge APK task forbidden'
    print('S5E6_DIAGNOSTIC_ISOLATION=PASS;NO_INTERNET=YES;DISTINCT_PACKAGE=YES;NO_PRODUCTION_APK=YES')
    print('S5E6_SOURCE_PREFLIGHT=PASS;REAL_SDK_COMPILE=NOT_VERIFIED;PHYSICAL_KEYSTORE=NOT_VERIFIED;PRODUCTION=BLOCKED')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--ci', action='store_true')
    args = parser.parse_args()
    check(args.repo.resolve(), args.ci)
