#!/usr/bin/env python3
"""Read-only inventory of the exact Ubuntu-signed Packages objects missing in R2.

Derives hashes/sizes from real gpgv-authenticated pinned InRelease bytes, never
from an unsigned filename list. Does not download bodies, upload, sign or deploy.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import urllib.request
import urllib.error

_spec = importlib.util.spec_from_file_location('v9_signed_inventory', Path(__file__).with_name('appforge_signed_apt_closure_v9.py'))
signed = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(signed)
ORIGIN = 'https://appforge-v9-packages-readonly.28550040284a.workers.dev'


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError('R2 redirects are prohibited')


def inventory(repo, resources, keyring, architectures=('arm64', 'amd64'), now=None):
    signed.require(set(architectures) <= {'arm64', 'amd64'} and architectures, 'unsupported architecture')
    assets = Path(repo) / signed.mirror.ASSETS
    signed.verify_repo_pins(repo)
    rows = []
    for line in (assets / 'apt-snapshot-profile.tsv').read_text().splitlines():
        suite, pin = line.split('\t')
        path = Path(resources) / (suite + '_InRelease.asc')
        table = signed.verify_inrelease(path, pin, suite, keyring, now=now)
        # These fields are read only after exact-file pin and Ubuntu gpgv validation.
        text = path.read_text()
        fields = dict(line.split(': ', 1) for line in text.splitlines()
                      if line.startswith(('Architectures: ', 'Components: ')))
        for arch in architectures:
            signed.require(arch in fields.get('Architectures', '').split(), 'architecture absent from signed Release')
            for component in signed.COMPONENTS:
                signed.require(component in fields.get('Components', '').split(), 'component absent from signed Release')
                name = f'{component}/binary-{arch}/Packages'
                signed.require(name in table, 'signed Packages entry missing')
                sha, size = table[name]
                signed.require(0 <= size <= signed.MAX_PACKAGES_BYTES, 'signed index exceeds bound')
                rows.append(dict(suite=suite, architecture=arch, index=name, sha256=sha,
                                 bytes=size, r2_url=f'{ORIGIN}/v9/objects/sha256/{sha[:2]}/{sha}',
                                 provenance=f'https://snapshot.ubuntu.com/ubuntu/{signed.SNAPSHOT}/dists/{suite}/{name}'))
    return rows


def inspect_local(rows, store):
    result = []
    for row in rows:
        item = dict(row)
        file = Path(store) / row['sha256']
        signed.require(not file.is_symlink(), 'symlinked index cache')
        if not file.exists():
            item['status'] = 'MISSING'
        else:
            item['status'] = 'PASS' if (file.is_file() and file.stat().st_size == row['bytes']
                and hashlib.sha256(file.read_bytes()).hexdigest() == row['sha256']) else 'FAIL'
        result.append(item)
    return result


def probe(rows, timeout=10):
    opener = urllib.request.build_opener(NoRedirect)
    results = {}
    for row in rows:
        if row['sha256'] in results:
            continue
        try:
            request = urllib.request.Request(row['r2_url'], method='HEAD', headers={'Accept-Encoding': 'identity'})
            with opener.open(request, timeout=timeout) as response:
                # HEAD only proves advertised availability, never content validation.
                results[row['sha256']] = dict(status='AVAILABLE_UNVERIFIED', http=response.status,
                    length_match=response.headers.get('Content-Length') == str(row['bytes']))
        except (OSError, ValueError, urllib.error.URLError) as exc:
            results[row['sha256']] = dict(status='BLOCKED', error=str(exc)[:160])
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--resources', type=Path)
    parser.add_argument('--keyring', type=Path)
    parser.add_argument('--store', type=Path)
    parser.add_argument('--probe-r2', action='store_true')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    resources = args.resources or args.repo / 'android-app/app/src/test/resources/apt-snapshot'
    keyring = args.keyring or resources / 'ubuntu-archive-keyring.gpg'
    rows = inventory(args.repo, resources, keyring)
    report = dict(schema=1, observed_at=datetime.now(timezone.utc).isoformat(),
        signed_inrelease='PASS', required_references=len(rows),
        unique_objects=len({r['sha256'] for r in rows}), indexes=rows,
        installation='NOT_RUN', device_acceptance='NOT_RUN', production_mutations=0)
    if args.store:
        report['indexes'] = inspect_local(rows, args.store)
    if args.probe_r2:
        report['r2_head'] = probe(rows)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(f"SIGNED_INRELEASE=PASS;PACKAGES_REFERENCES={len(rows)};UNIQUE_OBJECTS={report['unique_objects']}")
    print('INSTALLATION=NOT_RUN;PHYSICAL=NOT_RUN;PRODUCTION_MUTATIONS=0')


if __name__ == '__main__':
    main()
