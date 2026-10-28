#!/usr/bin/env python3
"""TEST-ONLY SQLite witness transaction model, NOT an independent trust anchor.

SQLite is on the SAME local machine, so deletion, snapshot restore, compromised
host, malicious caller, and storage rollback are OUTSIDE guarantees. There is
NO network, no signing, no server identity, no release authorization, no Android
integration and no production security claim. Never use as a production witness.
"""
import contextlib
from dataclasses import dataclass
import json
import os
from pathlib import Path
import re
import secrets
import sqlite3
import stat

SHA = re.compile(r'[0-9a-f]{64}\Z')
DEVICE = SHA
MAX_VERSION = 2**31 - 1
MAX_GENERATION = 2**63 - 2
DB_NAME = 'test-witness.sqlite3'


def need(ok, why):
    if not ok:
        raise ValueError('BLOCKED: '+why)


def check_device(device):
    need(type(device) is str and bool(DEVICE.fullmatch(device)), 'invalid device identity')


def canonical(obj):
    return json.dumps(obj, sort_keys=True, ensure_ascii=True, separators=(',', ':'),
                      allow_nan=False)


def record(value):
    if value is None:
        return None
    need(type(value) is dict and set(value) == {'sha256','root_version','release_sequence'},
         'record field mismatch')
    need(type(value['sha256']) is str and bool(SHA.fullmatch(value['sha256'])),
         'bad checkpoint digest')
    for key in ('root_version', 'release_sequence'):
        need(type(value[key]) is int and 1 <= value[key] <= MAX_VERSION,
             'invalid '+key)
    return value


def parse_json(value):
    if value is None:
        return None
    need(type(value) is str and len(value) <= 1024, 'untrusted stored JSON length')
    def unique(pairs):
        result = {}
        for k,v in pairs:
            need(k not in result, 'duplicate stored JSON key')
            result[k] = v
        return result
    try:
        obj = json.loads(value, object_pairs_hook=unique,
                         parse_constant=lambda _: (_ for _ in ()).throw(ValueError('nonfinite')))
    except (json.JSONDecodeError, TypeError, UnicodeError, OverflowError, RecursionError) as exc:
        raise ValueError('BLOCKED: malformed stored witness JSON') from exc
    need(canonical(obj) == value, 'noncanonical stored witness JSON')
    return obj


def pending_obj(value):
    if value is None:
        return None
    need(type(value) is dict and set(value) == {'token','prior','next'},
         'invalid pending intent fields')
    need(type(value['token']) is str and bool(re.fullmatch(r'[0-9a-f]{32}',value['token'])),
         'invalid pending token')
    prior = record(value['prior'])
    nxt = record(value['next'])
    need(nxt is not None, 'pending successor missing')
    if prior is None:
        need(nxt['root_version']==1, 'bootstrap root must be v1')
    else:
        need(nxt['release_sequence'] > prior['release_sequence'],
             'pending release rollback')
        need(nxt['root_version'] in (prior['root_version'],prior['root_version']+1),
             'pending root rollback or skip')
    return value


@dataclass(frozen=True)
class Snapshot:
    committed: object
    pending: object
    generation: int


class TestWitnessStore:
    """Development-only transactional state machine. NOT authoritative witness.

    Every state-changing method requires an explicit test_mode=True opt-in on
    construction. There is deliberately no implicit first-run enrollment.
    """
    def __init__(self, directory, *, test_mode=False):
        need(test_mode is True, 'TEST ONLY: explicit opt-in required')
        root = Path(directory)
        st = root.lstat()
        need(stat.S_ISDIR(st.st_mode) and st.st_uid == os.getuid() and
             stat.S_IMODE(st.st_mode) == 0o700, 'untrusted database root')
        path = root / DB_NAME
        if path.is_symlink():
            raise ValueError('BLOCKED: witness DB symlink')
        if path.exists():
            meta = path.lstat()
            need(stat.S_ISREG(meta.st_mode) and meta.st_uid == os.getuid() and
                 stat.S_IMODE(meta.st_mode) == 0o600 and meta.st_nlink == 1,
                 'untrusted database file')
        else:
            fd = os.open(path, os.O_CREAT|os.O_EXCL|os.O_WRONLY|os.O_NOFOLLOW, 0o600)
            os.close(fd)
            root_fd = os.open(root, os.O_RDONLY|os.O_DIRECTORY)
            try: os.fsync(root_fd)
            finally: os.close(root_fd)
        self.path = str(path)
        with self._connect() as db:
            db.execute('''CREATE TABLE IF NOT EXISTS devices (
                device TEXT PRIMARY KEY NOT NULL,
                committed TEXT,
                pending TEXT,
                generation INTEGER NOT NULL CHECK(generation >= 0))''')
            ok = db.execute('PRAGMA integrity_check').fetchone()[0]
            need(ok == 'ok', 'witness SQLite integrity check failed')

    @contextlib.contextmanager
    def _connect(self):
        db = sqlite3.connect(self.path, timeout=5, isolation_level=None)
        try:
            db.execute('PRAGMA busy_timeout=5000')
            db.execute('PRAGMA journal_mode=DELETE')
            db.execute('PRAGMA synchronous=FULL')
            yield db
        finally:
            db.close()

    @staticmethod
    def _state(db, device):
        row = db.execute('SELECT committed,pending,generation FROM devices WHERE device=?',
                         (device,)).fetchone()
        if row is None:
            return Snapshot(None,None,0)
        committed = record(parse_json(row[0]))
        pending = pending_obj(parse_json(row[1]))
        gen = row[2]
        need(type(gen) is int and 0 <= gen <= MAX_GENERATION, 'bad witness generation')
        need(pending is None or pending['prior']==committed,
             'inconsistent stored witness intent')
        # Aborted bootstrap retains generation as anti-replay diagnostic.
        return Snapshot(committed,pending,gen)

    def snapshot(self, device):
        check_device(device)
        with self._connect() as db:
            return self._state(db,device)

    def prepare_model(self, device, expected_prior, successor, *, authorize=None):
        """NO real trust: `authorize` is a user-supplied TEST callback, not proof.

        Future authoritative service MUST verify Phase3G signed transitions
        itself, never authorize a transition from an untrusted client callback.
        """
        check_device(device)
        expected_prior = record(expected_prior)
        successor = record(successor)
        need(successor is not None, 'missing next record')
        need(callable(authorize) and authorize(expected_prior,successor) is True,
             'trusted verifier authorization NOT implemented')
        if expected_prior is None:
            need(successor['root_version'] == 1, 'nonbootstrap root')
        else:
            need(successor['release_sequence'] > expected_prior['release_sequence'],
                 'release rollback or same-sequence mutation')
            need(successor['root_version'] in (expected_prior['root_version'],
                                               expected_prior['root_version'] + 1),
                 'root rollback or version skip')
        with self._connect() as db:
            try:
                db.execute('BEGIN IMMEDIATE')
                s = self._state(db,device)
                need(s.committed==expected_prior, 'compare-and-swap conflict')
                need(s.pending is None, 'pending transaction already exists')
                need(s.generation < MAX_GENERATION, 'generation exhausted')
                token = secrets.token_hex(16)
                intent = {'token':token, 'prior':expected_prior,'next':successor}
                if db.execute('SELECT 1 FROM devices WHERE device=?',(device,)).fetchone() is None:
                    db.execute('INSERT INTO devices(device,committed,pending,generation) VALUES(?,?,?,?)',
                               (device,None,canonical(intent),1))
                else:
                    db.execute('UPDATE devices SET pending=?,generation=? WHERE device=?',
                               (canonical(intent),s.generation+1,device))
                db.execute('COMMIT')
                return token
            except BaseException:
                db.execute('ROLLBACK')
                raise

    def _settle(self, device, token, action):
        check_device(device)
        need(type(token) is str and bool(re.fullmatch(r'[0-9a-f]{32}',token)),
             'invalid transaction token')
        with self._connect() as db:
            try:
                db.execute('BEGIN IMMEDIATE')
                s = self._state(db,device)
                need(s.pending is not None and s.pending['token']==token,
                     'missing or stale pending intent')
                need(s.generation < MAX_GENERATION, 'generation exhausted')
                nxt = s.pending['next'] if action=='finalize' else s.committed
                db.execute('UPDATE devices SET committed=?,pending=NULL,generation=? WHERE device=?',
                           (canonical(nxt) if nxt is not None else None,s.generation+1,device))
                # An aborted initial bootstrap must be distinguishable from a
                # NEVER-used device: leave the row with nonzero generation.
                db.execute('COMMIT')
                return Snapshot(nxt,None,s.generation+1)
            except BaseException:
                db.execute('ROLLBACK')
                raise

    def finalize(self, device, token):
        return self._settle(device,token,'finalize')

    def abort(self, device, token):
        return self._settle(device,token,'abort')


if __name__=='__main__':
    print('PHASE3H_STAGE4_SQLITE_MODEL=TEST_ONLY')
    print('EXTERNAL_WITNESS=NOT_DEPLOYED')
    print('PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED')
    print('ANDROID_CLIENT_ENABLED=NO')
    print('R2_UPLOAD=NO')
    print('PUBLIC_DISTRIBUTION=DISABLED')
    print('ALL_ENGINES_READY=false')
