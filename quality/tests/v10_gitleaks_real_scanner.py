#!/usr/bin/env python3
"""Opt-in real scanner regression. Arguments are a verified scanner command prefix.

Example: python3 quality/tests/v10_gitleaks_real_scanner.py /path/to/gitleaks
A Linux compatibility launcher may precede the binary. No download or gate bypass.
Only synthetic credentials are generated; scanner output is never echoed.
"""
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
import tomllib


def main(command):
    if not command:
        raise SystemExit('verified scanner command required')
    os.umask(0o077)
    root = Path(__file__).resolve().parents[2]
    config = root / 'quality/tools/v10-gitleaks.toml'
    policy = tomllib.loads(config.read_text())
    assert policy['extend'] == {'useDefault': True}
    assert len(policy['allowlists']) == 8
    for row in policy['allowlists']:
        assert row['condition'] == 'AND' and row['regexTarget'] == 'match'
        assert len(row['targetRules']) == len(row['paths']) == len(row['regexes']) == 1
    parent = root / '.appforge/tmp/v10'
    parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='real-scanner-test-', dir=parent) as tmp:
        work = Path(tmp)
        def scan(name, source):
            report = work / (name + '.json')
            result = subprocess.run(command + ['dir', str(source), '--config', str(config),
                '--redact=100', '--exit-code=42', '--no-banner', '--report-format=json',
                '--report-path', str(report)], stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL, timeout=120)
            assert result.returncode == 42, name + ': expected detection exit'
            return json.loads(report.read_text())
        positive = work / 'positive'; positive.mkdir()
        (positive / 'fixture.txt').write_text('api_key = "ghp_' + secrets.token_hex(18) + '"\n')
        hits = scan('positive', positive)
        assert any(x['RuleID'] == 'github-pat' for x in hits)
        negative = work / 'negative'; negative.mkdir()
        key = work / 'disposable.pem'
        subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt',
            'rsa_keygen_bits:2048', '-out', str(key)], check=True,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
        planted = 'api_key = "' + secrets.token_hex(24) + '"\n' + key.read_text()
        paths = set()
        for row in policy['allowlists']:
            # Policies use an anchored, escaped literal repository path.
            path = row['paths'][0].removeprefix('(?:^|/)').removesuffix('$').replace('\\.', '.')
            paths.add(path)
        paths.add('unrelated/credentials.txt')
        for path in paths:
            target = negative / path; target.parent.mkdir(parents=True, exist_ok=True)
            original = root / path
            target.write_text((original.read_text() if original.is_file() else '') + '\n' + planted)
        hits = scan('negative', negative)
        for path in paths:
            rules = {x['RuleID'] for x in hits if x['File'].endswith('/' + path)}
            assert {'generic-api-key', 'private-key'} <= rules, 'detection coverage failed'
        print('REAL_SCANNER_TEST=PASS; POSITIVE_FIXTURE=DETECTED; NEGATIVE_PATHS=' + str(len(paths)))


if __name__ == '__main__':
    main(sys.argv[1:])
