#!/usr/bin/env python3
"""Non-secret, static identity contract. Actual certificate is checked by CI."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[2]
conf=json.loads((root/'quality/contracts/r31-original-identity.json').read_text())
gradle=(root/'android-app/app/build.gradle.kts').read_text()
assert conf['package']=='com.appforge.studio'
assert conf['certificate_sha1']=='99:0E:F3:3B:99:0F:D5:78:E9:DC:E7:24:34:81:96:F9:9B:88:07:BF'
assert gradle.count('applicationId = "com.appforge.studio"')==1
assert 'providers.gradleProperty("appforgeR31OriginalIdentity")' in gradle
assert 'if (appforgeR31OriginalIdentity) {' in gradle
assert 'signingConfigs.create("r31Original")' in gradle
assert 'signingConfig = requireNotNull(r31OriginalSigning)' in gradle
assert 'R31_ORIGINAL_PKCS12_SIGNER_REQUIRED' in gradle
assert 'keyAlias = "appforge-debug"' in gradle
assert 'storeType = "PKCS12"' in gradle
assert conf['signer_alias']=='appforge-debug'
assert conf['signer_store_type']=='PKCS12'
assert conf['ci_signer_secret_names']==['APPFORGE_R31_KEYSTORE_B64','APPFORGE_R31_STORE_PASSWORD']
assert 'output.versionCode.set(554)' in gradle
assert 'output.versionName.set("5.0.52-r31-original-id-test")' in gradle
assert 'R31 original package cannot combine with isolated/suffixed test identities.' in gradle
print('APPFORGE_R31_PACKAGE_AND_SOURCE_CONTRACT=PASS')
