"""Stage 3 POSIX file crash tests. All writes are in owned temporary folders."""
import base64
import hashlib
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import appforge_phase3h_filedisk_v9 as f
import appforge_phase3h_recovery_model_v9 as h


def state(sequence=1, version=1, release_tag='a'):
    der = bytes.fromhex('302a300506032b6570032100') + b'K' * 32
    digest = hashlib.sha256(der).hexdigest()
    return {'schema':1,'stage':h.policy.ROOT,
            'snapshot':h.policy.SNAPSHOT,'evidence_sha256':h.policy.EVIDENCE,
            'root_version':version,'root_keyid':digest,
            'root_spki_der_b64':base64.b64encode(der).decode('ascii'),
            'root_metadata_sha256':hashlib.sha256(('root-'+str(version)).encode()).hexdigest(),
            'release_sequence':sequence,
            'release_metadata_sha256':hashlib.sha256((release_tag+str(sequence)).encode()).hexdigest(),
            'r2_upload_allowed':False,'public_distribution_allowed':False,
            'android_client_enabled':False}


class FileDiskStage3Tests(unittest.TestCase):
    def setUp(self):
        d = tempfile.TemporaryDirectory(prefix='appforge-phase3h-filedisk-')
        self.addCleanup(d.cleanup)
        self.d = Path(d.name)
        self.disk = f.FileDisk(self.d)
        self.addCleanup(self.disk.close)
        self.witness = h.InMemoryWitness()
        self.model = h.RecoveryModel(self.disk, self.witness)
        self.a = state()
        self.b = state(2)
        self.ar = h.policy.canonical(self.a)
        self.br = h.policy.canonical(self.b)

    def bootstrap(self):
        self.assertEqual(self.model.apply_verified(lambda a, b:self.a), 'COMMITTED_IN_MODEL')

    def update(self, *, interrupt_at=None):
        return self.model.apply_verified(lambda raw, cp:self.b, interrupt_at=interrupt_at)

    def reopened(self):
        self.disk.close()
        self.disk = f.FileDisk(self.d)
        self.model = h.RecoveryModel(self.disk, self.witness)
        return self.model

    def test_01_bootstrap_writes_canonical_bytes(self):
        self.bootstrap();self.assertEqual(self.disk.current,self.ar)
        self.assertEqual(self.model.recover(),'STABLE')

    def test_02_written_file_private_mode(self):
        self.bootstrap();p=self.d/f.CURRENT
        self.assertEqual(stat.S_IMODE(p.stat().st_mode),0o600)
        self.assertEqual(stat.S_IMODE(self.d.stat().st_mode),0o700)

    def test_03_update_survives_adapter_reopen(self):
        self.bootstrap();self.update();self.reopened()
        self.assertEqual(self.model.recover(),'STABLE')
        self.assertEqual(self.disk.current,self.br)

    def test_04_after_prepare_reopen_aborts(self):
        self.bootstrap()
        with self.assertRaises(InterruptedError):self.update(interrupt_at='after_prepare')
        self.reopened()
        self.assertEqual(self.model.recover(),'RECOVERED_PREVIOUS')
        self.assertEqual(self.disk.current,self.ar)

    def test_05_after_stage_reopen_aborts(self):
        self.bootstrap()
        with self.assertRaises(InterruptedError):self.update(interrupt_at='after_stage')
        self.assertEqual(self.disk.staged,self.br)
        self.reopened()
        self.assertEqual(self.model.recover(),'RECOVERED_PREVIOUS')
        self.assertIsNone(self.disk.staged)
        self.assertEqual(self.disk.current,self.ar)

    def test_06_after_swap_reopen_finalizes(self):
        self.bootstrap()
        with self.assertRaises(InterruptedError):self.update(interrupt_at='after_swap')
        self.reopened()
        self.assertEqual(self.model.recover(),'RECOVERED_COMMITTED')
        self.assertEqual(self.disk.current,self.br)
        self.assertEqual(self.witness.committed.release_sequence,2)

    def test_07_after_finalize_reopen_stable(self):
        self.bootstrap()
        with self.assertRaises(InterruptedError):self.update(interrupt_at='after_finalize')
        self.reopened()
        self.assertEqual(self.model.recover(),'STABLE')

    def test_08_missing_current_fails_closed(self):
        self.bootstrap();(self.d/f.CURRENT).unlink()
        with self.assertRaisesRegex(ValueError,'missing|rolled back'):
            self.model.recover()

    def test_09_corrupt_current_fails_closed(self):
        self.bootstrap();(self.d/f.CURRENT).write_bytes(b'bad')
        with self.assertRaises(ValueError):self.model.recover()

    def test_10_symlink_current_fails_closed(self):
        self.bootstrap();p=self.d/f.CURRENT;p.unlink();p.symlink_to('/etc/passwd')
        with self.assertRaises(OSError):self.disk.current

    def test_11_symlink_stage_fails_closed(self):
        (self.d/f.STAGED).symlink_to('/etc/passwd')
        with self.assertRaises((OSError,ValueError)):self.disk.stage(self.ar)
        self.assertTrue((self.d/f.STAGED).is_symlink())

    def test_12_world_readable_root_refused(self):
        self.disk.close();os.chmod(self.d,0o755)
        with self.assertRaisesRegex(ValueError,'0700'):
            f.FileDisk(self.d)

    def test_13_symlink_root_refused(self):
        link=self.d.parent/(self.d.name+'-alias')
        link.symlink_to(self.d,target_is_directory=True)
        self.addCleanup(lambda:link.unlink(missing_ok=True))
        with self.assertRaisesRegex(ValueError,'non-symlink'):
            f.FileDisk(link)

    def test_14_existing_stage_exclusive_no_overwrite(self):
        self.disk.stage(self.ar)
        with self.assertRaises(FileExistsError):self.disk.stage(self.br)
        self.assertEqual(self.disk.staged,self.ar)

    def test_15_noncanonical_state_not_staged(self):
        with self.assertRaises(ValueError):self.disk.stage(self.ar+b' ')
        self.assertIsNone(self.disk.staged)

    def test_16_stage_produces_no_current_before_replace(self):
        self.disk.stage(self.ar)
        self.assertIsNone(self.disk.current)
        self.assertEqual(self.disk.staged,self.ar)

    def test_17_swap_yields_new_bytes(self):
        self.disk.stage(self.ar);self.disk.replace()
        self.assertEqual(self.disk.current,self.ar)
        self.assertIsNone(self.disk.staged)

    def test_18_unexpected_stage_without_witness_blocks(self):
        self.disk.stage(self.ar)
        with self.assertRaisesRegex(ValueError,'unwitnessed'):
            self.model.recover()

    def test_19_wrong_witness_checkpoint_blocks(self):
        self.bootstrap();self.witness.committed=h.Record('0'*64,1,1)
        with self.assertRaisesRegex(ValueError,'witness disagrees'):
            self.model.recover()

    def test_20_duplicate_release_idempotent_disk_unchanged(self):
        self.bootstrap();before=self.disk.current
        self.assertEqual(self.model.apply_verified(lambda a,b:self.a),'IDEMPOTENT')
        self.assertEqual(self.disk.current,before)

    def test_21_no_witness_blocks_update(self):
        self.bootstrap();self.witness.online=False
        with self.assertRaisesRegex(ValueError,'unavailable'):
            self.update()
        self.assertEqual(self.disk.current,self.ar)

    def test_22_witness_after_prepare_disk_not_changed(self):
        self.bootstrap()
        with self.assertRaises(InterruptedError):self.update(interrupt_at='after_prepare')
        self.assertEqual(self.disk.current,self.ar)
        self.assertIsNotNone(self.witness.pending)

    def test_23_wrong_file_mode_blocks_read(self):
        self.bootstrap();os.chmod(self.d/f.CURRENT,0o644)
        with self.assertRaisesRegex(ValueError,'file type/mode/size'):
            _=self.disk.current

    def test_24_hardlink_is_rejected(self):
        self.bootstrap()
        current = self.d / f.CURRENT
        duplicate = self.d / 'duplicate'
        # Android may prohibit link(2) via SELinux (even in app-private data).
        # Try an actual hard link first; never silently skip the rejection test.
        attempt = subprocess.run(['ln', str(current), str(duplicate)],
                                 capture_output=True, text=True, timeout=10,
                                 check=False)
        if attempt.returncode == 0:
            original_stat, linked_stat = os.stat(current), os.stat(duplicate)
            self.assertEqual((original_stat.st_dev, original_stat.st_ino),
                             (linked_stat.st_dev, linked_stat.st_ino))
            self.assertGreaterEqual(original_stat.st_nlink, 2)
            mode = 'REAL_HARDLINK'
        else:
            stderr = (attempt.stderr or attempt.stdout).strip()
            android = sys.platform == 'android' or '/data/data/com.termux/' in sys.executable
            denied = any(phrase in stderr.lower() for phrase in
                         ('permission denied', 'operation not permitted',
                          'not supported', 'izin verilmedi'))
            self.assertTrue(android and denied and not duplicate.exists(),
                            'Unexpected hardlink failure: ' + stderr[:300])
            self.assertEqual(os.stat(current).st_nlink, 1)
            # On restrictive Android devices, exercise the *same* fstat guard
            # with an injected st_nlink=2 on a real opened regular-file FD.
            original_fstat = os.fstat
            injected = []
            def linked_fstat(fd):
                metadata = original_fstat(fd)
                if stat.S_ISREG(metadata.st_mode):
                    fields = list(metadata)
                    fields[3] = 2
                    injected.append(True)
                    return os.stat_result(fields)
                return metadata
            with patch.object(f.os, 'fstat', side_effect=linked_fstat):
                with self.assertRaisesRegex(ValueError, 'file type/mode/size'):
                    _ = self.disk.current
            self.assertTrue(injected, 'file nlink guard was not exercised')
            mode = 'SIMULATED_NLINK_2_ANDROID_POLICY'
            print('HARDLINK_REAL_CREATION=DENIED_BY_ANDROID_POLICY', flush=True)
            print('HARDLINK_GUARD_NLINK_2=PASS', flush=True)
            return
        with self.assertRaisesRegex(ValueError, 'file type/mode/size'):
            _ = self.disk.current
        print('HARDLINK_TEST_MODE=' + mode, flush=True)
    def test_25_storage_closed_fails_closed(self):
        self.disk.close()
        with self.assertRaisesRegex(ValueError,'closed'):
            _=self.disk.current

    def test_26_witness_unavailable_recovery_blocks(self):
        self.bootstrap();self.witness.online=False;self.reopened()
        with self.assertRaisesRegex(ValueError,'unavailable'):
            self.model.recover()

    def test_27_fsync_write_stage_and_directory(self):
        actual=os.fsync
        with patch.object(f.os,'fsync',wraps=actual) as spy:
            self.disk.stage(self.ar)
            self.assertGreaterEqual(spy.call_count,2)

    def test_28_fsync_rename_directory(self):
        self.disk.stage(self.ar)
        actual=os.fsync
        with patch.object(f.os,'fsync',wraps=actual) as spy:
            self.disk.replace()
            self.assertGreaterEqual(spy.call_count,1)

    def test_29_stage_process_exit_preserves_bytes(self):
        # A separate helper is killed AFTER stage/fsync; witness stays a mock.
        child='import sys,os;from appforge_phase3h_filedisk_v9 import FileDisk;d=FileDisk(sys.argv[1]);d.stage(bytes.fromhex(sys.argv[2]));os._exit(77)'
        result=subprocess.run([sys.executable,'-B','-c',child,str(self.d),self.ar.hex()],
                              env={**os.environ,'PYTHONPATH':str(Path(f.__file__).parent),
                                   'PYTHONDONTWRITEBYTECODE':'1'},
                              capture_output=True,timeout=10)
        self.assertEqual(result.returncode,77,result.stderr.decode(errors='replace'))
        self.assertEqual(self.disk.staged,self.ar)

    def test_30_swap_process_exit_preserves_bytes(self):
        self.disk.stage(self.ar)
        child='import sys,os;from appforge_phase3h_filedisk_v9 import FileDisk;d=FileDisk(sys.argv[1]);d.replace();os._exit(79)'
        result=subprocess.run([sys.executable,'-B','-c',child,str(self.d)],
                              env={**os.environ,'PYTHONPATH':str(Path(f.__file__).parent),
                                   'PYTHONDONTWRITEBYTECODE':'1'},
                              capture_output=True,timeout=10)
        self.assertEqual(result.returncode,79,result.stderr.decode(errors='replace'))
        self.assertEqual(self.disk.current,self.ar)

    def test_31_no_real_witness_or_android_io(self):
        source=Path(f.__file__).read_text()
        for item in ('requests.post','urllib.request','r2.put','genpkey','-sign',
                     'subprocess.run','http.client'):
            self.assertNotIn(item,source)
        self.assertIn('PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED',source)

    def test_32_lock_not_implemented_must_be_declared(self):
        self.assertIn('External synchronization',f.FileDisk.__doc__)


if __name__=='__main__': unittest.main()
