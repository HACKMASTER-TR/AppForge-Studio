#!/usr/bin/env python3
"""AppForge V9 Phase 3C resumable, signed-catalog-pinned local DEB staging.

Local-only acceptance: no Git writes, R2, public services, system APT, APK,
Android app integration, or readiness switching. Atomic no-replace publication
is mandatory (Linux renameat2); unsupported platforms fail closed.
"""
import argparse
import ctypes
import errno
import fcntl
import hashlib
import importlib.util
import os
from pathlib import Path
import platform
import re
import stat
import sys
import tempfile
import urllib.error
import urllib.request

PINNED_SNAPSHOT = '20261018T000000Z'
MAX_SINGLE_BYTES = 40 * 1024 * 1024
MAX_COUNT = 5
MAX_TOTAL = 64 * 1024 * 1024
AT_FDCWD = -100
RENAME_NOREPLACE = 1
PARTS_NAME = '.appforge-v9-phase3c-partials'
HEX = re.compile(r'[0-9a-f]{64}\Z')


def require(ok, message):
    if not ok:
        raise ValueError('BLOCKED: ' + message)


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    require(spec is not None and spec.loader is not None, 'module loading unavailable')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('BLOCKED: HTTP redirect disallowed')


def no_replace_rename(source, dest):
    """Linux kernel RENAME_NOREPLACE; no os.rename / replace fallback."""
    lib = ctypes.CDLL(None, use_errno=True)
    src, dst = os.fsencode(source), os.fsencode(dest)
    try:
        renameat2 = lib.renameat2
    except AttributeError:
        renameat2 = None
    if renameat2 is not None:
        renameat2.argtypes = (ctypes.c_int, ctypes.c_char_p,
                              ctypes.c_int, ctypes.c_char_p, ctypes.c_uint)
        renameat2.restype = ctypes.c_int
        res = renameat2(AT_FDCWD, src, AT_FDCWD, dst, RENAME_NOREPLACE)
    else:
        machine = platform.machine().lower()
        nr = {'aarch64': 276, 'arm64': 276,
              'x86_64': 316, 'amd64': 316}.get(machine)
        require(nr is not None, 'renameat2 syscall unsupported architecture')
        syscall = lib.syscall
        syscall.restype = ctypes.c_long
        res = syscall(ctypes.c_long(nr), ctypes.c_int(AT_FDCWD),
                      ctypes.c_char_p(src), ctypes.c_int(AT_FDCWD),
                      ctypes.c_char_p(dst), ctypes.c_uint(RENAME_NOREPLACE))
    if res != 0:
        code = ctypes.get_errno()
        raise OSError(code, os.strerror(code), str(dest))


def capability_probe(dirpath):
    """Prove atomic no-replace cannot overwrite an existing destination."""
    a, b = (Path(dirpath) / ('.renameat2-probe-' + x)
            for x in ('source-' + str(os.getpid()), 'dest-' + str(os.getpid())))
    require(not a.exists() and not b.exists(), 'atomic probe name collision')
    try:
        with a.open('xb') as f:
            f.write(b'source')
            f.flush()
            os.fsync(f.fileno())
        with b.open('xb') as f:
            f.write(b'destination')
            f.flush()
            os.fsync(f.fileno())
        try:
            no_replace_rename(a, b)
        except OSError as e:
            require(e.errno == errno.EEXIST,
                    'kernel atomic no-replace unsupported: ' + str(e))
        else:
            raise ValueError('BLOCKED: atomic no-replace unexpectedly overwrote destination')
        require(a.read_bytes() == b'source' and b.read_bytes() == b'destination',
                'atomic no-replace probe mismatch')
    finally:
        a.unlink(missing_ok=True)
        b.unlink(missing_ok=True)


def acquire_batch_lock(staging):
    """Serialize staged writers; do not wait on or override another session."""
    path = Path(staging) / '.batch.lock'
    require(not path.is_symlink(), 'batch lock symlink')
    flags = os.O_RDWR | os.O_CREAT | getattr(os, 'O_NOFOLLOW', 0)
    fd = os.open(path, flags, 0o600)
    st = os.fstat(fd)
    try:
        require(stat.S_ISREG(st.st_mode) and st.st_nlink == 1 and
                st.st_mode & 0o077 == 0, 'unsafe batch lock')
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as e:
            raise ValueError('BLOCKED: another Phase 3C batch is active') from e
        return fd
    except BaseException:
        os.close(fd)
        raise


def sha_file(p):
    h = hashlib.sha256()
    with Path(p).open('rb') as src:
        for chunk in iter(lambda: src.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def check_directory(p, create=False):
    p = Path(p)
    require(not p.is_symlink(), 'directory symlink: ' + str(p))
    if create:
        p.mkdir(mode=0o700, parents=False, exist_ok=True)
    require(p.is_dir(), 'missing directory: ' + str(p))
    require(not p.is_symlink(), 'directory symlink after creation')
    return p


def check_destination_path(store, digest, mirror):
    require(bool(HEX.fullmatch(digest)), 'invalid digest')
    root = check_directory(Path(store).absolute())
    current = root
    obj = mirror.object_path(root, digest)
    for part in obj.relative_to(root).parts[:-1]:
        current = current / part
        require(not current.is_symlink(), 'symlinked object directory')
        if not current.exists():
            current.mkdir(mode=0o700)
        require(current.is_dir() and not current.is_symlink(), 'invalid object directory')
    require(not obj.is_symlink(), 'symlinked destination')
    return obj


def verify_object(path, item):
    p = Path(path)
    require(p.is_file() and not p.is_symlink(), 'missing or symlinked object')
    s = p.stat()
    require(stat.S_ISREG(s.st_mode) and s.st_nlink == 1,
            'unsafe existing object')
    require(s.st_size == item['size'], 'object byte count mismatch')
    require(sha_file(p) == item['sha256'], 'object SHA-256 mismatch')


def open_partial(staging, digest, length):
    name = Path(staging) / (digest + '.part')
    require(not name.is_symlink(), 'partial symlink')
    flags = os.O_RDWR | os.O_CREAT | getattr(os, 'O_NOFOLLOW', 0)
    fd = os.open(name, flags, 0o600)
    st = os.fstat(fd)
    try:
        require(stat.S_ISREG(st.st_mode) and st.st_nlink == 1 and
                st.st_mode & 0o077 == 0,
                'invalid partial type/link count/permissions')
        require(st.st_size <= length, 'oversized retained partial')
    except BaseException:
        os.close(fd)
        raise
    return fd, name


def fetch_remainder(item, fd, offset, *, timeout=20, opener=None):
    expected = item['size']
    require(offset < expected, 'no remaining bytes')
    require(0 <= offset < expected <= MAX_SINGLE_BYTES, 'invalid bounded transfer size')
    headers = {'User-Agent': 'AppForge-V9-Phase3C-Resumable/1.0',
               'Accept-Encoding': 'identity'}
    if offset:
        headers['Range'] = f'bytes={offset}-{expected-1}'
    req = urllib.request.Request(item['url'], method='GET', headers=headers)
    opener = opener or urllib.request.build_opener(NoRedirect)
    with opener.open(req, timeout=timeout) as response:
        status = response.status
        if offset:
            require(status == 206, 'Range resume not honored with HTTP 206')
            wanted = f'bytes {offset}-{expected-1}/{expected}'
            require(response.headers.get('Content-Range') == wanted,
                    'unexpected Content-Range')
        else:
            require(status == 200, 'unexpected initial HTTP status')
        encoding = response.headers.get('Content-Encoding')
        require(encoding in (None, 'identity'), 'unexpected Content-Encoding')
        cl = response.headers.get('Content-Length')
        if cl is not None:
            require(cl.isascii() and cl.isdecimal() and int(cl) == expected - offset,
                    'unexpected Content-Length')
        os.lseek(fd, offset, os.SEEK_SET)
        written = 0
        try:
            while True:
                chunk = response.read(min(65536, expected - offset - written + 1))
                if not chunk:
                    break
                written += len(chunk)
                require(offset + written <= expected, 'server exceeded expected byte count')
                view = memoryview(chunk)
                while view:
                    n = os.write(fd, view)
                    require(n > 0, 'partial write failed')
                    view = view[n:]
        finally:
            os.fsync(fd)
        require(offset + written == expected, 'incomplete download; resumable partial retained')
    return written


def fsync_dir(path):
    flags = os.O_RDONLY | getattr(os, 'O_DIRECTORY', 0)
    fd = os.open(path, flags)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def import_one(item, store, staging, mirror, *, timeout=20, opener=None):
    digest = item['sha256']
    require(bool(HEX.fullmatch(digest)) and 0 < item['size'] <= MAX_SINGLE_BYTES,
            'invalid item digest/size')
    require(item['url'] in item['sources'] and
            item['url'].startswith(mirror.ORIGIN + 'pool/'), 'unpinned DEB URL')
    dest = check_destination_path(store, digest, mirror)
    if dest.exists():
        verify_object(dest, item)
        return 'VERIFIED_REUSED', 0
    fd, part = open_partial(staging, digest, item['size'])
    network = 0
    try:
        offset = os.fstat(fd).st_size
        if offset < item['size']:
            network = fetch_remainder(item, fd, offset, timeout=timeout, opener=opener)
        os.fsync(fd)
        require(os.fstat(fd).st_size == item['size'], 'incorrect completed size')
        os.lseek(fd, 0, os.SEEK_SET)
        require(os.read(fd, 8) == b'!<arch>\n', 'not a DEB archive')
        require(sha_file(part) == digest, 'completed DEB SHA256 mismatch')
    finally:
        os.close(fd)
    try:
        no_replace_rename(part, dest)
    except OSError as exc:
        if exc.errno == errno.EEXIST:
            verify_object(dest, item)
            part.unlink(missing_ok=True)
            return 'VERIFIED_REUSED', network
        raise
    fsync_dir(dest.parent)
    fsync_dir(staging)
    verify_object(dest, item)
    return 'VERIFIED_IMPORTED', network


def prepare(repo, store, keyring):
    tools = Path(repo) / 'quality/tools'
    mirror = load('appforge_v9_phase3c_resume_mirror', tools / 'appforge_package_mirror_v9.py')
    signed = load('appforge_v9_phase3c_resume_signed', tools / 'appforge_signed_apt_closure_v9.py')
    for arch, identities in (('arm64', 139), ('amd64', 94)):
        r = signed.verify_architecture(repo, store, keyring, arch)
        require(r['signed_suites'] == 4 and r['verified_indexes'] == 16 and
                r['catalog_closure'] and r['catalog_identities'] == identities,
                'signed catalog closure: ' + arch)
        print('CATALOG_CLOSURE_' + arch + '=PASS', flush=True)
    spec, count = mirror.inspect_store(repo, store)
    require(spec['snapshot'] == PINNED_SNAPSHOT and count['total'] == 218,
            'mirror manifest drift')
    objs = [dict(obj) for obj in spec['objects'] if obj['kind'] == 'deb']
    require(len(objs) == 214 and sum(o['size'] for o in objs) == 366187036,
            'DEB catalog drift')
    objs.sort(key=lambda o: (o['size'],
                             0 if any('_arm64.deb' in x for x in o['sources']) else 1,
                             o['sha256']))
    for obj in objs:
        require(obj['sources'], 'catalog object without source')
        obj['url'] = obj['sources'][0]
    return mirror, objs, count


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path.cwd())
    parser.add_argument('--store', type=Path, default=Path.home()/'appforge-v9-local-mirror')
    parser.add_argument('--keyring', type=Path,
                        default=Path('android-app/app/src/test/resources/apt-snapshot/ubuntu-archive-keyring.gpg'))
    parser.add_argument('--max-files', type=int, default=5)
    parser.add_argument('--max-total-bytes', type=int, default=2 * 1024 * 1024)
    parser.add_argument('--max-single-bytes', type=int, default=1 * 1024 * 1024)
    parser.add_argument('--timeout', type=int, default=20)
    args = parser.parse_args(argv)
    imported = resumed = reused = downloaded = 0
    blocked = 0
    lockfd = None
    try:
        require(1 <= args.max_files <= MAX_COUNT, 'too many files per acceptance batch')
        require(1 <= args.max_total_bytes <= MAX_TOTAL, 'batch network budget exceeded')
        require(1 <= args.max_single_bytes <= MAX_SINGLE_BYTES, 'single file bound exceeded')
        require(3 <= args.timeout <= 30, 'unsupported timeout')
        store = check_directory(args.store)
        mirror, objs, before = prepare(args.repo, store, args.keyring)
        staging = check_directory(store / PARTS_NAME, create=True)
        require(staging.stat().st_dev == store.stat().st_dev, 'cross-device staging forbidden')
        require(staging.stat().st_mode & 0o077 == 0, 'staging permissions too broad')
        lockfd = acquire_batch_lock(staging)
        capability_probe(staging)
        print('ATOMIC_NOREPLACE=PASS', flush=True)
        print('STARTING_DEB=' + str(before['present'] - 4) + '/214', flush=True)
        pending = [o for o in objs if not mirror.object_path(store, o['sha256']).is_file()]
        # Exact ordering keeps resuming predictable; stop before files > cap.
        plan = pending[:args.max_files]
        remaining_budget = args.max_total_bytes
        for obj in plan:
            expected = obj['size']
            part = staging / (obj['sha256'] + '.part')
            existing = part.stat().st_size if part.exists() and not part.is_symlink() else 0
            require(not part.is_symlink(), 'symlinked retained partial')
            require(existing <= expected, 'partial bigger than pinned length')
            new_bytes = expected - existing
            if expected > args.max_single_bytes or new_bytes > remaining_budget:
                print('BATCH_BUDGET=STOP_BEFORE_NEXT_OBJECT', flush=True)
                break
            statv = os.statvfs(store)
            free = statv.f_bavail * statv.f_frsize
            require(free >= new_bytes + 256 * 1024 * 1024, 'insufficient free space')
            print('CANDIDATE_SHA256=' + obj['sha256'], flush=True)
            print('EXPECTED_BYTES=' + str(expected), flush=True)
            print('RESUME_OFFSET=' + str(existing), flush=True)
            result, received = import_one(obj, store, staging, mirror, timeout=args.timeout)
            remaining_budget -= received
            downloaded += received
            imported += int(result == 'VERIFIED_IMPORTED')
            reused += int(result == 'VERIFIED_REUSED')
            resumed += int(existing > 0 and result == 'VERIFIED_IMPORTED')
            print('DEB_IMPORT=' + result, flush=True)
            print('NETWORK_BYTES=' + str(received), flush=True)
        _, final = mirror.inspect_store(args.repo, store)
        require(final['present'] >= before['present'] and
                final['present'] <= before['present'] + imported,
                'store count inconsistency')
        print('DEB_OBJECTS_PRESENT=' + str(final['present'] - 4) + '/214', flush=True)
    except (ValueError, OSError, RuntimeError, urllib.error.URLError) as exc:
        blocked = 1
        print('PHASE3C_BATCH=BLOCKED', flush=True)
        print('REASON=' + type(exc).__name__ + ': ' + str(exc)[:250], flush=True)
    finally:
        if lockfd is not None:
            os.close(lockfd)
    print('NEW_VERIFIED_DEB=' + str(imported), flush=True)
    print('RESUMED_VERIFIED_DEB=' + str(resumed), flush=True)
    print('NETWORK_BYTES=' + str(downloaded), flush=True)
    print('DOWNLOAD_BLOCKED=' + str(blocked), flush=True)
    print('R2_UPLOAD=NO')
    print('GITHUB_PUSH=NO')
    print('MAIN_MODIFIED=NO')
    print('PHONE_APK_MODIFIED=NO')
    print('ALL_ENGINES_READY=false')
    return blocked

if __name__ == '__main__':
    raise SystemExit(main())
