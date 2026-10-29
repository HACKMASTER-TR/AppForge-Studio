#!/usr/bin/env python3
"""Opt-in genuine OSV assessment. Requires pre-acquired verified local assets.

No network acquisition, dependency install, build, remediation, or gate bypass.
Reports are private; shareable output contains dependency/advisory counts only.
"""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / '.appforge/tmp/v10/osv'
EXPECTED_BINARY = '4061ef4c021ed3c47f203b818f337e351336cc5cd67c0274fc36bd1dfc728dd7'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def validate_database(root, provenance):
    if {p['ecosystem'] for p in provenance} != {'npm', 'Maven', 'PyPI', 'crates.io'}:
        raise ValueError('incomplete database coverage')
    for p in provenance:
        path = root / 'osv-scalibr' / p['ecosystem'] / 'all.zip'
        age = (datetime.now(timezone.utc) - datetime.fromisoformat(p['updated'].replace('Z', '+00:00'))).total_seconds()
        if (path.is_symlink() or not path.is_file() or path.stat().st_size != p['size']
                or digest(path) != p['sha256'] or not 0 <= age <= 72 * 3600):
            raise ValueError('database missing, corrupt, or stale')


def main():
    os.umask(0o077)
    binary = CACHE / 'osv-scanner'
    if binary.is_symlink() or digest(binary) != EXPECTED_BINARY:
        raise ValueError('scanner integrity mismatch')
    provenance = json.loads((CACHE / 'database/provenance.json').read_text())
    validate_database(CACHE / 'database', provenance)
    work = CACHE / (sys.argv[1] if len(sys.argv) > 1 else 'assessment')
    if work.parent != CACHE or work.name in ('', '.', '..'):
        raise ValueError('invalid assessment directory')
    work.mkdir()  # Never silently overwrite evidence from an earlier attempt.
    home = work / 'home'; home.mkdir()
    prefix = [sys.executable, str(ROOT / 'quality/tools/v10_osv_offline_exec.py'),
              'proot-distro', 'login', 'ubuntu', '--bind', str(ROOT) + ':' + str(ROOT), '--']
    records = []

    def run(name, source, database):
        report = work / (name + '.json')
        command = prefix + ['/usr/bin/env', '-i', 'PATH=/usr/bin:/bin',
            'HOME=' + str(home), 'TMPDIR=' + str(home), 'GOMAXPROCS=2',
            'OSV_SCALIBR_LOCAL_DB_CACHE_DIRECTORY=' + str(database), str(binary),
            'scan', 'source', '--offline', '--no-resolve', '--no-call-analysis=go',
            '--no-call-analysis=rust', '--all-packages', '--all-vulns', '--format=json',
            '--output-file=' + str(report), '--no-ignore', '--recursive', str(source)]
        log = work / (name + '.log')
        with log.open('w') as stream:
            code = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL,
                stdout=stream, stderr=stream, timeout=600, close_fds=True).returncode
        row = dict(name=name, command=command, exit=code, log_sha256=digest(log))
        records.append(row)
        (work / 'commands.json').write_text(json.dumps(records, indent=2) + '\n')
        data = json.loads(report.read_text()) if report.is_file() and report.stat().st_size else None
        print(name, 'exit', code, 'report', bool(data), flush=True)
        return code, data

    # Verify denial in an exec descendant inside PRoot, for both IP families.
    probe = '''import errno,socket
for family in (socket.AF_INET,socket.AF_INET6):
 try: socket.socket(family,socket.SOCK_STREAM)
 except OSError as e: assert e.errno == errno.EPERM
 else: raise RuntimeError('network socket allowed')
print('KERNEL_NETWORK_DENIAL=PASS')'''
    with (work / 'boundary.log').open('w') as stream:
        result = subprocess.run(prefix + [sys.executable, '-c', probe], stdout=stream,
            stderr=stream, stdin=subprocess.DEVNULL, timeout=30, close_fds=True)
    assert result.returncode == 0
    assert 'KERNEL_NETWORK_DENIAL=PASS' in (work / 'boundary.log').read_text()

    fixture = work / 'vulnerable'; fixture.mkdir()
    (fixture / 'package-lock.json').write_text(json.dumps(dict(name='osv-offline-positive-control',
        version='1.0.0', lockfileVersion=3, packages={
            '': dict(name='osv-offline-positive-control', version='1.0.0', dependencies={'lodash': '4.17.20'}),
            'node_modules/lodash': dict(version='4.17.20', resolved='https://registry.npmjs.org/lodash/-/lodash-4.17.20.tgz')})))
    missing = work / 'missing'; missing.mkdir()
    corrupt = work / 'corrupt'; (corrupt / 'osv-scalibr/npm').mkdir(parents=True)
    (corrupt / 'osv-scalibr/npm/all.zip').write_bytes(b'not-a-zip-database')
    for name, database in [('missing', missing), ('corrupt', corrupt)]:
        try:
            validate_database(database, provenance)
        except (ValueError, FileNotFoundError):
            pass
        else:
            raise AssertionError(name + ' database accepted by integrity guard')
        code, data = run(name, fixture, database)
        assert code == 127, name + ' database must fail as scanner error'
        expected_error = 'no offline version' if name == 'missing' else 'not a valid zip file'
        assert expected_error in (work / (name + '.log')).read_text()
    code, positive = run('positive', fixture, CACHE / 'database')
    assert code == 1, 'known-vulnerable fixture must have findings exit'
    ids = {v['id'] for result in positive['results'] for p in result['packages'] for v in p.get('vulnerabilities', [])}
    assert 'GHSA-35jh-r3h4-6jhm' in ids, 'expected lodash advisory was not detected'

    source = work / 'source'; source.mkdir()
    accepted = {'package-lock.json', 'Cargo.lock', 'gradle.lockfile', 'buildscript-gradle.lockfile', 'requirements.txt', 'go.mod', 'pom.xml'}
    inventory = []
    names = subprocess.check_output(['git', 'ls-files', '-co', '--exclude-standard', '-z'], cwd=ROOT).split(b'\0')
    for raw in sorted(set(names)):
        if not raw:
            continue
        relative = Path(raw.decode())
        if relative.name not in accepted:
            continue
        original = ROOT / relative
        if original.is_symlink() or not original.is_file() or '.appforge' in relative.parts:
            raise ValueError('unsafe manifest')
        content = original.read_bytes()
        if len(content) > 16 * 1024**2:
            raise ValueError('manifest size limit')
        dest = source / relative; dest.parent.mkdir(parents=True, exist_ok=True); dest.write_bytes(content)
        inventory.append(dict(path=relative.as_posix(), sha256=hashlib.sha256(content).hexdigest(), bytes=len(content)))
    assert inventory
    (work / 'source-inventory.json').write_text(json.dumps(inventory, indent=2) + '\n')
    code, data = run('project', source, CACHE / 'database')
    assert code in (0, 1) and isinstance(data.get('results'), list), 'project scan failed'
    packages = [p for r in data['results'] for p in r['packages']]
    unique = {(p['package']['ecosystem'], p['package']['name'], p['package']['version']) for p in packages}
    findings = {v['id'] for p in packages for v in p.get('vulnerabilities', [])}
    affected = [p for p in packages if p.get('vulnerabilities')]
    summary = dict(scanner_version='2.7.0', scanner_sha256=EXPECTED_BINARY,
        executed=True, database_verified=True, network_boundary='inherited kernel seccomp; IP socket probes denied inside PRoot exec child',
        security_tests=dict(missing_database='PASS', corrupt_database='PASS', known_vulnerable_dependency='PASS', kernel_network_denial='PASS'),
        fixture_advisories=sorted(ids), project_exit=code, source_manifest_count=len(inventory),
        package_occurrences=len(packages), unique_dependencies=len(unique), vulnerable_package_occurrences=len(affected),
        advisory_ids=sorted(findings), unique_advisory_count=len(findings),
        ecosystem_counts={e: len({p for p in unique if p[0] == e}) for e in sorted({p[0] for p in unique})},
        source_inventory=inventory, commands=records)
    (work / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps({k:v for k,v in summary.items() if k not in ('source_inventory','commands','advisory_ids')}, indent=2))


if __name__ == '__main__':
    main()
