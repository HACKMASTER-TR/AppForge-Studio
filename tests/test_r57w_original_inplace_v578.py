#!/usr/bin/env python3
"""R57W upgrade identity contract. Static evidence, not physical acceptance."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
gradle = (root / "android-app/app/build.gradle.kts").read_text(encoding="utf-8")
signer = (root / "quality/tools/verify-r31-original-identity.py").read_text(encoding="utf-8")
config = (root / "quality/contracts/r31-original-identity.json").read_text(encoding="utf-8")

assert gradle.count('applicationId = "com.appforge.studio"') == 1
assert 'appforgeR31OriginalIdentity' in gradle
assert 'appforgeR54OriginalIdentityUpgrade' in gradle
assert 'providers.gradleProperty("appforgeR57WOriginalIdentityUpgrade")' in gradle
assert 'appforgeR31OriginalIdentity && appforgeR54OriginalIdentityUpgrade' in gradle
assert 'R57W requires the existing R54 original-identity upgrade' in gradle
assert 'signingConfigs.create("r31Original")' in gradle
assert 'signingConfig = requireNotNull(r31OriginalSigning)' in gradle
assert 'R31_ORIGINAL_PKCS12_SIGNER_REQUIRED' in gradle
assert 'APPFORGE_R31_KEYSTORE_PATH' in gradle
assert '99:0E:F3:3B:99:0F:D5:78:E9:DC:E7:24:34:81:96:F9:9B:88:07:BF' in signer
assert '"com.appforge.studio"' in config
assert re.search(
    r'if \(appforgeR57WOriginalIdentityUpgrade\) \{\s*'
    r'output\.versionCode\.set\(578\)\s*'
    r'output\.versionName\.set\("5\.0\.76-r57w-original-upgrade"\)\s*'
    r'\} else if \(appforgeR54OriginalIdentityUpgrade\) \{\s*'
    r'output\.versionCode\.set\(577\)\s*'
    r'output\.versionName\.set\("5\.0\.75-r54-shell-tail"\)',
    gradle,
), 'R57W v578 original-identity override must supersede R54 v577'
assert 'output.versionCode.set(578)' in gradle
assert 'output.versionCode.set(577)' in gradle
assert 'applicationIdSuffix = ".v9r2isolated3"' in gradle
assert 'R31 original package cannot combine with isolated/suffixed test identities.' in gradle
print('R57W_STATIC_ORIGINAL_PACKAGE_VERSION_SIGNER_CONTRACT=PASS')
print('R57W_CANDIDATE_VERSION_CODE=578')
print('R57W_IN_PLACE_DATA_PRESERVATION=REQUIRES_PHYSICAL_INSTALL_PROOF')
