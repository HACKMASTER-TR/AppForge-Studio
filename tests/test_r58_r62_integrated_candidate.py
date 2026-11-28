#!/usr/bin/env python3
"""Integrated candidate identity and reference gate; no physical acceptance claim."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
g=(root/'android-app/app/build.gradle.kts').read_text()
profile='appforgeR58IntegratedDeviceCandidate'
assert 'providers.gradleProperty("'+profile+'").orNull == "true"' in g
for flag in ['appforgeR31OriginalIdentity']+[f'appforgeR{i}OriginalIdentityUpgrade' for i in range(32,55)]+['appforgeR57WOriginalIdentityUpgrade']:
    assert 'providers.gradleProperty("'+flag+'").orNull == "true" || '+profile in g,flag
assert 'else if ('+profile+') {\n                    output.versionCode.set(579)' in g
assert 'output.versionName.set("5.0.77-r58-r62-integrated")' in g
assert 'R31_ORIGINAL_PKCS12_SIGNER_REQUIRED' in g
assert g.count('applicationId = "com.appforge.studio"')==1
for name in ['r58-r62-integrated-v579-ci.yml','r58-r62-integrated-v579-unsigned-ci.yml']:
    w=(root/'.github/workflows'/name).read_text()
    assert '-PappforgeR72BPathRepairCandidate=true' in w
    assert "versionCode='589'" in w
    assert 'generate-second-brain-snapshot.py --check' in w
refs=(root/'.github/workflows/r58-r62-module-reference-ci.yml').read_text()
for job in ['rust-arm64-reference:', 'expo-arm64-prebuild:', 'windows-arm64-cross-reference:']:
    assert job in refs
print('R58_R62_PROFILE_AND_REFERENCE_GATE=PASS_PHYSICAL_ACCEPTANCE_PENDING')
