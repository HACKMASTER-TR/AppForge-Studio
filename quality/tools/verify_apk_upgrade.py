#!/usr/bin/env python3
"""Fail-closed package/version/certificate comparison against the user-supplied APK reference."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

DEFAULT_REFERENCE = Path(__file__).resolve().parents[1] / 'fixtures/appforge-v9r2isolated3-reference.json'

def sha256(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as source:
        for block in iter(lambda: source.read(65536), b''): result.update(block)
    return result.hexdigest()

def identity(apk, aapt2, apksigner):
    badging = subprocess.check_output([aapt2, 'dump', 'badging', str(apk)], text=True)
    match = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'", badging, re.M)
    if not match: raise ValueError('INVALID_PACKAGE_METADATA')
    minimum = re.search(r"^(?:minSdkVersion|sdkVersion):'([0-9]+)'", badging, re.M)
    target = re.search(r"^targetSdkVersion:'([0-9]+)'", badging, re.M)
    if not minimum or not target: raise ValueError('INVALID_SDK_METADATA')
    signature = subprocess.check_output([apksigner, 'verify', '--print-certs', str(apk)], text=True)
    certs = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$', signature, re.M)
    if len(certs) != 1: raise ValueError('SINGLE_VERIFIED_SIGNER_REQUIRED')
    return dict(package=match[1], version_code=int(match[2]), version_name=match[3],
                min_sdk=int(minimum[1]), target_sdk=int(target[1]), certificate_sha256=certs[0].lower())

def check(reference, old, new, old_hash, new_hash, source_sha):
    if not re.fullmatch('[a-f0-9]{40}', source_sha): raise ValueError('INVALID_SOURCE_SHA')
    if old_hash != reference['apk_sha256']: raise ValueError('REFERENCE_APK_HASH_MISMATCH')
    for key in ('package', 'version_code', 'min_sdk', 'target_sdk', 'certificate_sha256'):
        if old[key] != reference[key]: raise ValueError('REFERENCE_IDENTITY_MISMATCH')
    if new['package'] != old['package']: raise ValueError('UPDATE_PACKAGE_MISMATCH')
    if new['certificate_sha256'] != old['certificate_sha256']: raise ValueError('UPDATE_CERTIFICATE_MISMATCH')
    if new['version_code'] != reference['upgrade_version_code'] or new['version_code'] <= old['version_code']:
        raise ValueError('UPDATE_VERSION_MUST_INCREASE')
    if new['min_sdk'] != old['min_sdk'] or new['target_sdk'] != old['target_sdk']:
        raise ValueError('UPDATE_SDK_MISMATCH')
    return dict(new, source_sha=source_sha, apk_sha256=new_hash, reference_sha256=old_hash,
                update_compatible=True, physical_device='NOT_RUN')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--old-apk', required=True, type=Path)
    parser.add_argument('--new-apk', required=True, type=Path)
    parser.add_argument('--aapt2', required=True)
    parser.add_argument('--apksigner', required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.output.is_symlink(): raise SystemExit('STOP: UNSAFE_OUTPUT')
    # A failed repeat must never leave a stale positive report.
    args.output.write_text(json.dumps({'update_compatible': False})+'\n')
    reference = json.loads(DEFAULT_REFERENCE.read_text())
    result = check(reference, identity(args.old_apk, args.aapt2, args.apksigner),
                   identity(args.new_apk, args.aapt2, args.apksigner),
                   sha256(args.old_apk), sha256(args.new_apk), args.source_sha)
    args.output.write_text(json.dumps(result, indent=2)+'\n')
    print('UPDATE_IDENTITY=PASS PACKAGE='+result['package']+' VERSION_CODE='+str(result['version_code']))
    print('CERT_SHA256='+result['certificate_sha256']+' PHYSICAL_DEVICE=NOT_RUN')

if __name__ == '__main__': main()
