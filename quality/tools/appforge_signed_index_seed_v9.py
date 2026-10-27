#!/usr/bin/env python3
"""V9 Phase 3B: bounded, fail-closed seed of signed Ubuntu Packages.xz indexes.

Read-only upstream retrieval from the pinned Ubuntu snapshot. No R2, no APT,
no GitHub writes, no Android changes, no distribution enablement. All fetched
bytes and their bounded decompressions require hashes/sizes from GPG-verified
InRelease metadata already pinned and present in local store.
"""
import argparse
import hashlib
import importlib.util
import lzma
import os
from pathlib import Path
import tempfile
import urllib.error
import urllib.request

MOD = Path(__file__).with_name('appforge_signed_apt_closure_v9.py')
SPEC = importlib.util.spec_from_file_location('v9_signed_seed_dependency', MOD)
assert SPEC is not None and SPEC.loader is not None
signed = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(signed)

MAX_COMPRESSED_BYTES = 32 * 1024 * 1024
MAX_DOWNLOADS = 1
EMPTY = hashlib.sha256(b'').hexdigest()

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise ValueError('BLOCKED: redirected snapshot source')


def plan(repo, store, keyring, arch):
    signed.require(arch in ('arm64', 'amd64'), 'unsupported host architecture')
    tables = signed.release_tables(repo, store, keyring)
    tasks = []
    for suite in signed.SUITES:
        for component in signed.COMPONENTS:
            key = f'{component}/binary-{arch}/Packages'
            zipped = key + '.xz'
            table = tables[suite]
            signed.require(key in table and zipped in table, 'signed index entries missing: ' + suite + '/' + key)
            raw_sha, raw_size = table[key]
            xz_sha, xz_size = table[zipped]
            signed.require(0 <= raw_size <= signed.MAX_PACKAGES_BYTES, 'raw signed index exceeds verified bound')
            signed.require(0 < xz_size <= MAX_COMPRESSED_BYTES, 'XZ signed index exceeds verified bound')
            signed.require(raw_size != 0 or raw_sha == EMPTY, 'zero length requires empty SHA-256')
            tasks.append({'suite': suite, 'component': component, 'arch': arch,
                          'index': key, 'sha256': raw_sha, 'size': raw_size,
                          'xz_sha256': xz_sha, 'xz_size': xz_size,
                          'url': f'https://snapshot.ubuntu.com/ubuntu/{signed.SNAPSHOT}/dists/{suite}/{zipped}'})
    signed.require(len(tasks) == 16, 'exactly 16 signed index references required')
    return tasks


def existing(store, task):
    p = signed.safe_object(store, task['sha256'], required=False)
    if p is None:
        return False
    signed.require(p.stat().st_size == task['size'], 'existing signed index length mismatch')
    return True


def bounded_unxz(payload, expected_size):
    signed.require(0 < len(payload) <= MAX_COMPRESSED_BYTES, 'compressed size out of bounds')
    signed.require(0 <= expected_size <= signed.MAX_PACKAGES_BYTES, 'uncompressed size out of bounds')
    d = lzma.LZMADecompressor(format=lzma.FORMAT_XZ)
    blocks, size = [], 0
    try:
        for start in range(0, len(payload), 65536):
            signed.require(not d.eof, 'trailing bytes after XZ stream')
            data = payload[start:start + 65536]
            while True:
                chunk = d.decompress(data, max_length=min(1048576, expected_size - size + 1))
                size += len(chunk)
                signed.require(size <= expected_size, 'XZ decompression size exceeded signed index length')
                blocks.append(chunk)
                signed.require(not d.unused_data, 'trailing bytes after XZ stream')
                if d.eof or d.needs_input:
                    break
                data = b''
        signed.require(d.eof, 'truncated XZ stream')
    except (lzma.LZMAError, EOFError) as exc:
        raise ValueError('BLOCKED: invalid XZ stream') from exc
    signed.require(size == expected_size, 'uncompressed signed length mismatch')
    return b''.join(blocks)




def link_exclusive(source, destination):
    """Android-compatible exclusive local staging; not atomic visibility."""
    src = Path(source)
    dst = Path(destination)

    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    flags |= getattr(os, 'O_NOFOLLOW', 0)
    flags |= getattr(os, 'O_CLOEXEC', 0)

    fd = os.open(dst, flags, 0o600)
    created = os.fstat(fd)

    try:
        with os.fdopen(fd, 'wb') as output, src.open('rb') as input_file:
            for chunk in iter(lambda: input_file.read(1048576), b''):
                output.write(chunk)
            output.flush()
            os.fsync(output.fileno())
    except BaseException:
        try:
            now = os.lstat(dst)
            if (now.st_dev == created.st_dev
                    and now.st_ino == created.st_ino
                    and now.st_nlink == 1):
                os.unlink(dst)
        except OSError:
            pass
        raise


def immutable_import(store, task, data):
    signed.require(len(data) == task['size'], 'index size mismatch before import')
    signed.require(hashlib.sha256(data).hexdigest() == task['sha256'], 'index SHA256 mismatch before import')
    if existing(store, task):
        return 'VERIFIED_REUSED'
    root = Path(store).absolute()
    signed.require(not root.is_symlink(), 'symlinked mirror root')
    obj = signed.mirror.object_path(root, task['sha256'])
    for parent in (root, *list(reversed(obj.parents))):
        if parent == root or parent.is_relative_to(root):
            signed.require(not parent.is_symlink(), 'symlinked object parent')
    obj.parent.mkdir(parents=True, exist_ok=True)
    signed.require(not obj.parent.is_symlink(), 'symlinked object parent after mkdir')
    fd, path = tempfile.mkstemp(prefix='.signed-index-', dir=obj.parent)
    try:
        with os.fdopen(fd, 'wb') as dest:
            dest.write(data)
            dest.flush()
            os.fsync(dest.fileno())
        signed.require(hashlib.sha256(Path(path).read_bytes()).hexdigest() == task['sha256'], 'temp object hash mismatch')
        try:
            link_exclusive(path, obj)
        except FileExistsError:
            signed.require(existing(store, task), 'concurrent destination mismatch')
    finally:
        Path(path).unlink(missing_ok=True)
    signed.require(existing(store, task), 'object missing after atomic import')
    return 'IMPORTED'


def fetch(task, timeout=15):
    url = task['url']
    signed.require(url.startswith('https://snapshot.ubuntu.com/ubuntu/' + signed.SNAPSHOT + '/dists/'), 'unapproved source URL')
    req = urllib.request.Request(url, method='GET', headers={'User-Agent': 'AppForge-V9-Signed-Index-Probe/1.0'})
    opener = urllib.request.build_opener(NoRedirect)
    try:
        with opener.open(req, timeout=timeout) as resp:
            signed.require(resp.status == 200, 'snapshot response not HTTP 200')
            if resp.headers.get('Content-Length'):
                signed.require(int(resp.headers['Content-Length']) == task['xz_size'], 'snapshot compressed length mismatch')
            raw = resp.read(task['xz_size'] + 1)
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise ValueError('BLOCKED: snapshot network unavailable: ' + str(exc)[:200]) from exc
    signed.require(len(raw) == task['xz_size'], 'snapshot XZ length mismatch')
    signed.require(hashlib.sha256(raw).hexdigest() == task['xz_sha256'], 'snapshot XZ SHA256 mismatch')
    data = bounded_unxz(raw, task['size'])
    signed.require(hashlib.sha256(data).hexdigest() == task['sha256'], 'snapshot uncompressed SHA256 mismatch')
    return data


def run(repo, store, keyring, arch, *, max_downloads=1, timeout=15):
    tasks = plan(repo, store, keyring, arch)  # GPG happens before any network
    # Signed zero-length Packages may be materialized without network bytes.
    for task in tasks:
        if task['size'] == 0 and not existing(store, task):
            immutable_import(store, task, b'')
    total_verified = sum(existing(store, item) for item in tasks)
    print(f'SIGNED_INDEXES_BEFORE={total_verified}/16', flush=True)
    remaining = sorted((t for t in tasks if not existing(store, t)), key=lambda x:(x['xz_size'], x['suite'], x['component']))
    downloads = 0
    for item in remaining:
        if downloads >= max_downloads:
            break
        print('PROBE=' + item['suite'] + '/' + item['index'], flush=True)
        print('EXPECTED_XZ_BYTES=' + str(item['xz_size']), flush=True)
        try:
            data = fetch(item, timeout)
        except ValueError as exc:
            print('SNAPSHOT_FETCH=BLOCKED', flush=True)
            print(str(exc), flush=True)
            break
        print('INDEX_IMPORT=' + immutable_import(store, item, data), flush=True)
        downloads += 1
    final = sum(existing(store, item) for item in tasks)
    print('SIGNED_INRELEASE=4_PASS', flush=True)
    print(f'SIGNED_INDEXES={final}/16', flush=True)
    print(f'NETWORK_INDEX_IMPORTS={downloads}', flush=True)
    print('PACKAGES_CLOSURE=' + ('PENDING_CATALOG_CHECK' if final == 16 else 'INCOMPLETE'), flush=True)
    print('R2_UPLOAD_ALLOWED=NO', flush=True)
    print('PUBLIC_DISTRIBUTION_ALLOWED=NO', flush=True)
    print('ANDROID_CLIENT_ENABLED=NO', flush=True)
    print('ALL_ENGINES_READY=false', flush=True)
    return final, downloads


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--store', type=Path, required=True)
    parser.add_argument('--keyring', type=Path, required=True)
    parser.add_argument('--arch', choices=('arm64','amd64'), default='arm64')
    parser.add_argument('--max-downloads', type=int, default=1)
    parser.add_argument('--timeout', type=int, default=15)
    args = parser.parse_args(argv)
    try:
        signed.require(0 <= args.max_downloads <= MAX_DOWNLOADS, 'bounded probe permits at most one remote index')
        signed.require(3 <= args.timeout <= 30, 'bounded network timeout required')
        run(args.repo, args.store, args.keyring, args.arch, max_downloads=args.max_downloads, timeout=args.timeout)
    except (OSError, RuntimeError, ValueError) as exc:
        print(str(exc))
        print('SIGNED_METADATA_PROBE=BLOCKED')
        print('R2_UPLOAD_ALLOWED=NO')
        print('ALL_ENGINES_READY=false')
        return 1
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
