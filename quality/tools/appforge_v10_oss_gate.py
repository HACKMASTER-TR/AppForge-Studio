#!/usr/bin/env python3
"""Opt-in host scanners. No install, network, production access or APK packaging.

Archives are pre-seeded explicitly by an operator. Only fixed, reviewed release
archive hashes are accepted. Run from a designated temporary snapshot of nonignored
repository files, with a clean environment and bounded process resources.
"""
import argparse
import hashlib
import json
import os
import platform
import re
import signal
import subprocess
import tarfile
import tempfile
from pathlib import Path, PurePosixPath

PINS = json.loads(Path(__file__).with_name('v10-oss-pins.json').read_text())
FILE_LIMIT = 8 * 1024 * 1024
TOTAL_LIMIT = 128 * 1024 * 1024
MAX_FILES = 20000

class Blocked(Exception):
    pass

def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''): h.update(block)
    return h.hexdigest()

def safe_relative(name):
    path = PurePosixPath(name)
    return bool(name) and not path.is_absolute() and all(p not in ('', '.', '..') for p in name.split('/')) and '\\' not in name and not any(ord(c) < 32 for c in name)

def summarize_gitleaks(report):
    if not isinstance(report, list) or len(report) > 10000: raise Blocked('INVALID_REPORT')
    summaries = []
    for row in report:
        if not isinstance(row, dict): raise Blocked('INVALID_REPORT')
        rule, name, line = row.get('RuleID'), row.get('File'), row.get('StartLine')
        summaries.append({'rule': rule if isinstance(rule, str) and re.fullmatch(r'[a-z0-9-]{1,100}', rule) else 'REDACTED',
                          'file': name if isinstance(name, str) and safe_relative(name) and len(name) <= 250 else 'REDACTED',
                          'line': line if type(line) is int and 0 < line < 10000000 else 0})
    return summaries

def classify(tool, code):
    if code == 0: return 'PASS'
    if tool == 'gitleaks' and code == 42: return 'FINDINGS'
    return 'BLOCKED'

def snapshot(root, target, names, tool, file_limit=FILE_LIMIT):
    total = 0
    included = 0
    source_digest = hashlib.sha256(b'APPFORGE_V10_SCAN_SOURCE_V1\n')
    if len(names) > MAX_FILES: raise Blocked('FILE_COUNT_LIMIT')
    for name in sorted(names):
        if not safe_relative(name): raise Blocked('UNSAFE_SOURCE_PATH')
        relative = PurePosixPath(name)
        if any(part in {'.git', '.appforge', '.aws', '.codex', 'node_modules', '.gradle', '__pycache__'} for part in relative.parts):
            raise Blocked('LOCAL_DATA_IN_SOURCE_SET')
        if relative.name in {'local.properties', '.env'} or relative.suffix.lower() in {'.jks', '.keystore', '.p12', '.pfx'} or relative.name.startswith('.env.'):
            raise Blocked('SENSITIVE_SOURCE_PATH')
        path = root
        for part in relative.parts:
            path = path / part
            if path.is_symlink(): raise Blocked('SYMLINK_IN_SOURCE_SET')
        if not path.is_file(): raise Blocked('SOURCE_MISSING')
        if not path.resolve().is_relative_to(root.resolve()): raise Blocked('UNSAFE_SOURCE_PATH')
        size = path.stat().st_size
        if size > file_limit: raise Blocked('FILE_SIZE_LIMIT')
        total += size
        if total > TOTAL_LIMIT: raise Blocked('TOTAL_SIZE_LIMIT')
        # SBOM intentionally covers dependency manifests and license notices only.
        # No user's project files, home-directory data or source text is cataloged.
        if tool == 'syft' and not (relative.name in {'package.json', 'package-lock.json', 'yarn.lock', 'pnpm-lock.yaml', 'Cargo.lock', 'Cargo.toml', 'go.mod', 'go.sum', 'requirements.txt', 'pyproject.toml', 'uv.lock', 'gradle.lockfile'} or relative.name.upper().startswith(('LICENSE', 'NOTICE'))):
            continue
        dest = target / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        # Bound the read too: concurrent source growth cannot exceed the limit.
        with path.open('rb') as stream: data = stream.read(file_limit + 1)
        if len(data) > file_limit: raise Blocked('FILE_SIZE_LIMIT')
        total += len(data) - size
        if total > TOTAL_LIMIT: raise Blocked('TOTAL_SIZE_LIMIT')
        if relative.suffix.lower() == '.pem' and (b'PRIVATE KEY' in data or not data.startswith((b'-----BEGIN CERTIFICATE-----', b'-----BEGIN PUBLIC KEY-----'))):
            raise Blocked('SENSITIVE_SOURCE_PATH')
        dest.write_bytes(data)
        included += 1
        source_digest.update(name.encode() + b'\0' + hashlib.sha256(data).digest())
    return {'included_files': included, 'source_sha256': source_digest.hexdigest(), 'source_files': len(names), 'source_bytes': total, 'scope': 'nonignored-repository-working-tree' if tool == 'gitleaks' else 'nonignored-manifests-and-notices',
            'limitations': ['not Git history', 'not external user projects', 'not compiled APK/EXE contents', 'not dependency vulnerability clearance']}

def extract_binary(archive, destination, tool):
    if archive.stat().st_size > 100 * 1024 * 1024: raise Blocked('ARCHIVE_SIZE_LIMIT')
    with tarfile.open(archive, 'r:gz') as tar:
        match = None
        count = 0
        for entry in tar:
            count += 1
            if count > 5000: raise Blocked('ARCHIVE_ENTRY_LIMIT')
            if not safe_relative(entry.name.rstrip('/')) or entry.issym() or entry.islnk(): raise Blocked('UNSAFE_ARCHIVE')
            if entry.name == tool:
                if match is not None or not entry.isfile() or not 0 < entry.size <= 100 * 1024 * 1024: raise Blocked('INVALID_EXECUTABLE')
                match = entry
        if match is None: raise Blocked('EXECUTABLE_MISSING')
        with tar.extractfile(match) as source, destination.open('xb') as dest:
            remaining = match.size
            while remaining:
                block = source.read(min(1024 * 1024, remaining))
                if not block: raise Blocked('INVALID_EXECUTABLE')
                dest.write(block); remaining -= len(block)
    destination.chmod(0o500)

def sanitize_sbom(data, version):
    # Omit arbitrary properties, URLs, source locations and embedded license text.
    # Only package identities and SPDX IDs from a manifest-only scan are exported.
    components = []
    def safe(value):
        return isinstance(value, str) and re.fullmatch(r'[A-Za-z0-9_.@/+%-]{1,250}', value) is not None
    for row in data['components']:
        if not isinstance(row, dict) or not safe(row.get('name')): raise Blocked('INVALID_SBOM_COMPONENT')
        component = {'type': 'library', 'name': row['name']}
        if isinstance(row.get('version'), str) and re.fullmatch(r'[A-Za-z0-9_.+~:-]{1,128}', row['version']): component['version'] = row['version']
        licenses = []
        for item in row.get('licenses', []):
            ident = item.get('license', {}).get('id') if isinstance(item, dict) else None
            if isinstance(ident, str) and re.fullmatch(r'[A-Za-z0-9.+-]{1,100}', ident): licenses.append({'license': {'id': ident}})
        if licenses: component['licenses'] = licenses
        components.append(component)
    return {'bomFormat': 'CycloneDX', 'specVersion': '1.5', 'version': 1,
            'metadata': {'tools': [{'vendor': 'Anchore', 'name': 'syft', 'version': version}]},
            'components': components}

def limits():
    import resource
    os.setsid()
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    resource.setrlimit(resource.RLIMIT_CPU, (120, 120))
    resource.setrlimit(resource.RLIMIT_FSIZE, (FILE_LIMIT, FILE_LIMIT))
    resource.setrlimit(resource.RLIMIT_NOFILE, (128, 128))
    resource.setrlimit(resource.RLIMIT_AS, (2 * 1024**3, 2 * 1024**3))

def execute(argv, cwd, home):
    env = {'PATH': '/usr/bin:/bin', 'HOME': str(home), 'TMPDIR': str(home), 'LANG': 'C.UTF-8',
           'SYFT_CHECK_FOR_APP_UPDATE': 'false', 'SYFT_PARALLELISM': '2', 'GOMAXPROCS': '2'}
    with subprocess.Popen(argv, cwd=cwd, env=env, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL, preexec_fn=limits) as process:
        try: return process.wait(timeout=150)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
            raise Blocked('TIMEOUT')

def scan(tool, root, cache, machine=None, offline=False, output=None):
    base = {'tool': tool, 'status': 'BLOCKED'}
    if tool == 'osv-scanner':
        # Do not present an unvalidated flag as a no-network security boundary.
        return dict(base, reason='OFFLINE_DATABASE_AND_NETWORK_ISOLATION_UNVERIFIED' if offline else 'OSV_ADAPTER_NOT_VERIFIED')
    if tool not in PINS: return dict(base, reason='UNSUPPORTED_TOOL')
    if offline: return dict(base, reason='OFFLINE_NETWORK_ISOLATION_UNVERIFIED')
    pin = PINS[tool]
    base['version'] = pin['version']
    artifact = pin['artifacts'].get(machine or platform.machine())
    if not artifact: return dict(base, reason='UNSUPPORTED_ABI')
    archive = cache / artifact['name']
    if not archive.is_file(): return dict(base, status='NOT_RUN', reason='ARCHIVE_MISSING')
    try:
        if archive.is_symlink(): raise Blocked('HASH_MISMATCH')
        with archive.open('rb') as stream: archive_bytes = stream.read(100 * 1024 * 1024 + 1)
        if len(archive_bytes) > 100 * 1024 * 1024 or hashlib.sha256(archive_bytes).hexdigest() != artifact['sha256']:
            raise Blocked('HASH_MISMATCH')
        if pin.get('license_closure_reviewed') is not True:
            raise Blocked('LICENSE_CLOSURE_UNVERIFIED')
        if platform.system() != 'Linux' or Path('/system/bin/app_process').exists():
            raise Blocked('ANDROID_HOST_ABI_NOT_ACCEPTED')
        if output is not None and (output.exists() or output.is_symlink()): raise Blocked('OUTPUT_ALREADY_EXISTS')
        raw = subprocess.check_output(['git', 'ls-files', '-co', '--exclude-standard', '-z'], cwd=root, stderr=subprocess.DEVNULL)
        names = [name.decode('utf-8', errors='strict') for name in raw.split(b'\0') if name]
        if not names: raise Blocked('EMPTY_SOURCE_SET')
        with tempfile.TemporaryDirectory(prefix='appforge-v10-scan-') as work:
            work = Path(work); source = work / 'source'; source.mkdir(); home = work / 'home'; home.mkdir()
            coverage = snapshot(root, source, names, tool)
            verified_archive = work / 'verified.tar.gz'
            verified_archive.write_bytes(archive_bytes)
            del archive_bytes
            binary = work / tool; extract_binary(verified_archive, binary, tool)
            report = work / 'report.json'
            if tool == 'gitleaks':
                # Explicit config prevents imported project gitleaks config from changing policy.
                config = work / 'gitleaks.toml'
                config.write_bytes(Path(__file__).with_name('v10-gitleaks.toml').read_bytes())
                argv = [str(binary), 'dir', '.', '--config', str(config), '--redact=100', '--exit-code=42',
                        '--no-banner', '--report-format=json', '--report-path', str(report)]
            else:
                config = work / 'syft.yaml'; config.write_text('check-for-app-update: false\n')
                argv = [str(binary), 'scan', 'dir:.', '--config', str(config), '-o', 'cyclonedx-json=' + str(report)]
            code = execute(argv, source, home)
            state = classify(tool, code)
            result = dict(base, status=state, scanner_exit=code, archive_sha256=artifact['sha256'], coverage=coverage)
            if state == 'BLOCKED': return dict(result, reason='SCANNER_ERROR')
            if not report.is_file() or report.stat().st_size > FILE_LIMIT: raise Blocked('INVALID_REPORT')
            data = json.loads(report.read_text())
            if tool == 'gitleaks':
                result['findings'] = summarize_gitleaks(data)
                if (code == 0 and result['findings']) or (code == 42 and not result['findings']): raise Blocked('INCONSISTENT_REPORT')
            else:
                if not isinstance(data, dict) or data.get('bomFormat') != 'CycloneDX' or not isinstance(data.get('components'), list): raise Blocked('INVALID_SBOM')
                # Publish only the requested artifact; do not print raw SBOM content.
                data = sanitize_sbom(data, pin['version'])
                encoded = (json.dumps(data, sort_keys=True, indent=2) + '\n').encode()
                result.update(component_count=len(data['components']), output_sha256=hashlib.sha256(encoded).hexdigest())
                if output is None: raise Blocked('OUTPUT_REQUIRED')
                with output.open('xb') as dest: dest.write(encoded)
            return result
    except Blocked as error: return dict(base, reason=str(error))
    except (OSError, ValueError, tarfile.TarError, subprocess.SubprocessError):
        return dict(base, reason='INPUT_OR_RUNTIME_ERROR')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('tool', choices=['gitleaks', 'syft', 'osv-scanner'])
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    # Existing guard always precedes optional scanning, including in CI.
    guard = subprocess.run(['python3', str(Path(__file__).resolve().parents[2] / 'scripts/secret-hygiene-guard.py')],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    result = scan(args.tool, args.root.resolve(), args.cache, offline=args.offline, output=args.output) if guard.returncode == 0 else {'status': 'BLOCKED', 'reason': 'SECRET_HYGIENE_GUARD_FAILED'}
    print(json.dumps(result, sort_keys=True))
    return {'PASS': 0, 'FINDINGS': 1, 'NOT_RUN': 2, 'BLOCKED': 3}[result['status']]

if __name__ == '__main__': raise SystemExit(main())
