#!/usr/bin/env python3
"""Publish immutable update identity only after cryptographic APK verification evidence."""
import argparse
import hashlib
import json
from pathlib import Path

PACKAGE = 'com.appforge.studio.v9r2isolated3'
CERT = 'dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082'
ASSETS = Path(__file__).resolve().parents[2]/'android-app/app/src/main/assets/device-build'

def create(evidence, apk):
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    if (evidence.get('package') != PACKAGE or evidence.get('certificate_sha256') != CERT
            or evidence.get('version_code') != 545 or evidence.get('apk_sha256') != digest
            or evidence.get('update_compatible') is not True):
        raise ValueError('UNVERIFIED_UPDATE_IDENTITY')
    source = evidence.get('source_sha', '')
    if len(source) != 40 or any(c not in '0123456789abcdef' for c in source):
        raise ValueError('INVALID_SOURCE_SHA')
    catalogs={}
    for name in ['verified-toolchain-sources-v4.json','rust-toolchain-v1.json','apt-module-packages-v1.tsv','apt-snapshot-profile.tsv']:
        data=(ASSETS/name).read_bytes()
        catalogs[name]=dict(sha256=hashlib.sha256(data).hexdigest(),bytes=len(data),
                            contents=json.loads(data) if name.endswith('.json') else data.decode())
    return dict(schema=1, source_sha=source, package=PACKAGE, version_code=545,
                certificate_sha256=CERT, apk_sha256=digest, apk_bytes=apk.stat().st_size,
                reference_sha256=evidence['reference_sha256'], pin_catalogs=catalogs, physical_device='NOT_RUN')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    content = json.dumps(create(json.loads(args.evidence.read_text()), args.apk), indent=2)+'\n'
    # Never overwrite a manifest for a different build; write only a new immutable file.
    with args.output.open('x') as out:
        out.write(content)

if __name__ == '__main__':
    main()
