#!/usr/bin/env python3
"""Phase 3H Stage 3: POSIX temp-dir disk adapter for crash-injection tests.

NOT trusted anti-rollback persistence. The durable witness is still a RAM mock;
no independent checkpoint, server, production Android integration or guarantee
about storage hardware. This adapter must not be pointed at production state.
"""
import os
import stat
from pathlib import Path

import appforge_phase3h_recovery_model_v9 as recovery

CURRENT = 'trust-state.json'
STAGED = 'staged.json'
MAX = recovery.policy.MAX_STATE


def need(value, reason):
    if not value:
        raise ValueError('BLOCKED: ' + reason)


class FileDisk:
    """Single-process POSIX file adapter with symlink refusal and fsync.

    Constructor accepts only a preexisting owned 0700 directory. All file
    operations are relative to a pinned directory FD, never arbitrary paths.
    External synchronization and authenticated witness are NOT implemented.
    """
    def __init__(self, directory):
        p = Path(directory)
        st = p.lstat()  # reject symlinked root
        need(stat.S_ISDIR(st.st_mode) and st.st_uid == os.getuid()
             and stat.S_IMODE(st.st_mode) == 0o700,
             'disk root must be owned non-symlink 0700 directory')
        flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC
        self._fd = os.open(p, flags)
        got = os.fstat(self._fd)
        need(got.st_dev == st.st_dev and got.st_ino == st.st_ino,
             'disk root swapped during open')

    def close(self):
        fd = getattr(self, '_fd', None)
        if fd is not None:
            self._fd = None
            os.close(fd)

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

    def _root(self):
        need(self._fd is not None, 'disk is closed')
        return self._fd

    def _read(self, name):
        fd = self._root()
        try:
            handle = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC,
                             dir_fd=fd)
        except FileNotFoundError:
            return None
        try:
            meta = os.fstat(handle)
            need(stat.S_ISREG(meta.st_mode) and meta.st_nlink == 1
                 and meta.st_uid == os.getuid()
                 and stat.S_IMODE(meta.st_mode) == 0o600
                 and 0 < meta.st_size <= MAX,
                 'untrusted state file type/mode/size')
            data = os.read(handle, MAX + 1)
            need(len(data) == meta.st_size, 'state changed during read')
            return data
        finally:
            os.close(handle)

    @property
    def current(self):
        return self._read(CURRENT)

    @property
    def staged(self):
        return self._read(STAGED)

    @staged.setter
    def staged(self, value):
        need(value is None, 'staging assignment forbidden')
        fd = self._root()
        # Do not let cleanup follow or unlink symlinks or foreign files.
        data = self._read(STAGED)
        if data is not None:
            os.unlink(STAGED, dir_fd=fd)
            os.fsync(fd)

    def stage(self, raw):
        recovery.inspect(raw)  # canonical Phase 3F state, policy pinned evidence
        fd = self._root()
        out = os.open(STAGED, os.O_WRONLY | os.O_CREAT | os.O_EXCL |
                      os.O_NOFOLLOW | os.O_CLOEXEC, 0o600, dir_fd=fd)
        try:
            todo = memoryview(raw)
            while todo:
                n = os.write(out, todo)
                need(n > 0, 'stage write did not progress')
                todo = todo[n:]
            os.fsync(out)
        finally:
            os.close(out)
        os.fsync(fd)
        need(self.staged == raw, 'staged bytes mismatch')

    def replace(self):
        fd = self._root()
        candidate = self.staged
        need(candidate is not None, 'staging absent')
        # The file previously fsynced in stage; atomically replace directory entry.
        os.replace(STAGED, CURRENT, src_dir_fd=fd, dst_dir_fd=fd)
        os.fsync(fd)
        need(self.current == candidate, 'swapped state mismatch')


if __name__ == '__main__':
    print('PHASE3H_STAGE3_FILEDISK=TEST_ONLY')
    print('PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED')
    print('EXTERNAL_WITNESS=SIMULATED_NOT_DEPLOYED')
    print('R2_UPLOAD=NO')
    print('PUBLIC_DISTRIBUTION=DISABLED')
    print('ANDROID_CLIENT_ENABLED=NO')
    print('ALL_ENGINES_READY=false')
