"""Stage4A SQLite transactional witness REFERENCE MODEL only, not security proof."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import os
from pathlib import Path
import sqlite3
from contextlib import closing
import stat
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import appforge_phase3h_stage4_witness_store_v9 as w

DEVICE=hashlib.sha256(b'phase4a-device').hexdigest()
DEVICE_B=hashlib.sha256(b'phase4a-other-device').hexdigest()


def rec(seq=1,root=1,tag='a'):
    return {'sha256':hashlib.sha256(f'{seq}/{root}/{tag}'.encode()).hexdigest(),
            'root_version':root,'release_sequence':seq}


class Stage4ASQLiteTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='appforge-v9-stage4a-')
        self.addCleanup(self.tmp.cleanup)
        self.d=Path(self.tmp.name)
        self.d.chmod(0o700)
        self.store=w.TestWitnessStore(self.d,test_mode=True)

    def begin(self,after=None,prior=None,device=DEVICE,authorizer=None):
        return self.store.prepare_model(device,prior,after or rec(),
             authorize=(lambda *_:True) if authorizer is None else authorizer)

    def boot(self,device=DEVICE):
        token=self.begin(device=device)
        return self.store.finalize(device,token)

    def test_01_explicit_test_mode_required(self):
        with self.assertRaisesRegex(ValueError,'TEST ONLY'):
            w.TestWitnessStore(self.d)

    def test_02_empty_witness_snapshot(self):
        s=self.store.snapshot(DEVICE)
        self.assertEqual((s.committed,s.pending,s.generation),(None,None,0))

    def test_03_prepare_bootstrap_is_durable(self):
        token=self.begin()
        s=w.TestWitnessStore(self.d,test_mode=True).snapshot(DEVICE)
        self.assertEqual(s.pending['token'],token)
        self.assertEqual(s.generation,1)

    def test_04_finalize_bootstrap_is_durable(self):
        self.boot()
        s=w.TestWitnessStore(self.d,test_mode=True).snapshot(DEVICE)
        self.assertEqual(s.committed,rec())
        self.assertEqual(s.generation,2)

    def test_05_valid_update_and_reopen(self):
        self.boot()
        t=self.begin(rec(2),rec())
        self.store.finalize(DEVICE,t)
        self.assertEqual(w.TestWitnessStore(self.d,test_mode=True).snapshot(DEVICE).committed,rec(2))

    def test_06_abort_keeps_prior(self):
        self.boot()
        t=self.begin(rec(2),rec())
        self.assertEqual(self.store.abort(DEVICE,t).committed,rec())

    def test_07_abort_bootstrap_still_has_generation(self):
        t=self.begin();self.store.abort(DEVICE,t)
        self.assertEqual(self.store.snapshot(DEVICE).generation,2)
        self.assertIsNone(self.store.snapshot(DEVICE).committed)

    def test_08_unauthorized_prepare_rejected(self):
        with self.assertRaisesRegex(ValueError,'authorization'):
            self.store.prepare_model(DEVICE,None,rec())
        self.assertEqual(self.store.snapshot(DEVICE).generation,0)

    def test_09_explicit_denial_keeps_state(self):
        with self.assertRaisesRegex(ValueError,'authorization'):
            self.begin(authorizer=lambda *_:False)
        self.assertEqual(self.store.snapshot(DEVICE).generation,0)

    def test_10_verifier_failure_no_write(self):
        def fail(*_): raise ValueError('signature invalid')
        with self.assertRaisesRegex(ValueError,'signature invalid'):
            self.begin(authorizer=fail)
        self.assertEqual(self.store.snapshot(DEVICE).generation,0)

    def test_11_stale_expected_prior_rejected(self):
        self.boot()
        with self.assertRaisesRegex(ValueError,'compare-and-swap'):
            self.begin(rec(2),None)

    def test_12_conflicting_pending_rejected(self):
        self.boot();first=self.begin(rec(2),rec())
        with self.assertRaisesRegex(ValueError,'pending transaction'):
            self.begin(rec(3),rec())
        self.assertEqual(self.store.snapshot(DEVICE).pending['token'],first)

    def test_13_stale_finalize_token_rejected(self):
        self.boot();self.begin(rec(2),rec())
        with self.assertRaisesRegex(ValueError,'stale'):
            self.store.finalize(DEVICE,'f'*32)

    def test_14_stale_abort_token_rejected(self):
        self.boot();self.begin(rec(2),rec())
        with self.assertRaisesRegex(ValueError,'stale'):
            self.store.abort(DEVICE,'f'*32)

    def test_15_duplicate_finalize_rejected(self):
        self.boot();t=self.begin(rec(2),rec())
        self.store.finalize(DEVICE,t)
        with self.assertRaisesRegex(ValueError,'stale'):
            self.store.finalize(DEVICE,t)

    def test_16_release_rollback_rejected(self):
        self.boot();t=self.begin(rec(2),rec());self.store.finalize(DEVICE,t)
        with self.assertRaisesRegex(ValueError,'release rollback'):
            self.begin(rec(),rec(2))

    def test_17_same_sequence_mutation_rejected(self):
        self.boot()
        with self.assertRaisesRegex(ValueError,'same-sequence'):
            self.begin(rec(1,tag='different'),rec())

    def test_18_root_skip_rejected(self):
        self.boot()
        with self.assertRaisesRegex(ValueError,'root rollback or version skip'):
            self.begin(rec(2,root=3),rec())

    def test_19_root_decrease_rejected(self):
        self.boot();t=self.begin(rec(2,root=2),rec());self.store.finalize(DEVICE,t)
        with self.assertRaisesRegex(ValueError,'root rollback'):
            self.begin(rec(3,root=1),rec(2,root=2))

    def test_20_root_rotation_accepted_in_model(self):
        self.boot();t=self.begin(rec(2,root=2),rec())
        self.assertEqual(self.store.finalize(DEVICE,t).committed['root_version'],2)

    def test_21_bootstrap_root_v2_rejected(self):
        with self.assertRaisesRegex(ValueError,'nonbootstrap'):
            self.begin(rec(1,root=2))

    def test_22_invalid_device_rejected(self):
        with self.assertRaisesRegex(ValueError,'device'):
            self.store.snapshot('x\' OR 1=1 --')

    def test_23_devices_are_isolated(self):
        self.boot()
        self.assertEqual(self.store.snapshot(DEVICE_B).generation,0)
        self.boot(DEVICE_B)
        self.assertEqual(self.store.snapshot(DEVICE_B).committed,rec())

    def test_24_parallel_first_writer_wins(self):
        start=threading.Barrier(2)
        def attempt():
            start.wait(timeout=3)
            try:return ('pass',self.begin())
            except ValueError as e:return ('block',str(e))
        with ThreadPoolExecutor(max_workers=2) as pool:
            r=list(pool.map(lambda _:attempt(),range(2)))
        self.assertEqual([x[0] for x in r].count('pass'),1)
        self.assertEqual([x[0] for x in r].count('block'),1)
        self.assertEqual(self.store.snapshot(DEVICE).generation,1)

    def test_25_parallel_finalization_wins_once(self):
        self.boot();token=self.begin(rec(2),rec())
        def attempt():
            try:self.store.finalize(DEVICE,token);return 'pass'
            except ValueError:return 'block'
        with ThreadPoolExecutor(max_workers=2) as pool:
            outcomes=list(pool.map(lambda _:attempt(),range(2)))
        self.assertCountEqual(outcomes,['pass','block'])
        self.assertEqual(self.store.snapshot(DEVICE).committed,rec(2))

    def test_26_corrupted_committed_record_blocks(self):
        self.boot()
        with closing(sqlite3.connect(self.store.path)) as db, db:
            db.execute('UPDATE devices SET committed=? WHERE device=?',('{bad-json',DEVICE))
        with self.assertRaises(ValueError): self.store.snapshot(DEVICE)

    def test_27_wrong_pending_prior_blocks(self):
        self.boot();self.begin(rec(2),rec())
        with closing(sqlite3.connect(self.store.path)) as db, db:
            row=db.execute('SELECT pending FROM devices WHERE device=?',(DEVICE,)).fetchone()[0]
            intent=w.parse_json(row); intent['prior']=rec(tag='evil')
            db.execute('UPDATE devices SET pending=? WHERE device=?',(w.canonical(intent),DEVICE))
        with self.assertRaisesRegex(ValueError,'inconsistent stored'):
            self.store.snapshot(DEVICE)

    def test_28_duplicate_stored_key_blocks(self):
        self.boot()
        with closing(sqlite3.connect(self.store.path)) as db, db:
            db.execute('UPDATE devices SET committed=? WHERE device=?',
                       ('{"sha256":"'+rec()['sha256']+'","sha256":"'+rec()['sha256']+'"}',DEVICE))
        with self.assertRaisesRegex(ValueError,'duplicate'):
            self.store.snapshot(DEVICE)

    def test_29_invalid_bool_record_rejected(self):
        bad=rec();bad['release_sequence']=True
        with self.assertRaisesRegex(ValueError,'release_sequence'):
            self.begin(bad)

    def test_30_symlinked_root_rejected(self):
        other=self.d.parent/(self.d.name+'-link')
        try:
            other.symlink_to(self.d,target_is_directory=True)
            with self.assertRaisesRegex(ValueError,'root'):
                w.TestWitnessStore(other,test_mode=True)
        finally:
            other.unlink(missing_ok=True)

    def test_31_world_readable_db_rejected(self):
        os.chmod(self.store.path,0o644)
        with self.assertRaisesRegex(ValueError,'database file'):
            w.TestWitnessStore(self.d,test_mode=True)

    def test_32_world_readable_root_rejected(self):
        self.d.chmod(0o755)
        with self.assertRaisesRegex(ValueError,'root'):
            w.TestWitnessStore(self.d,test_mode=True)

    def test_33_process_exit_after_prepare_survives(self):
        src=("import sys,os;sys.path.insert(0,sys.argv[1]);"
             "import appforge_phase3h_stage4_witness_store_v9 as w;"
             "d=w.TestWitnessStore(sys.argv[2],test_mode=True);"
             "d.prepare_model(sys.argv[3],None,{'sha256':sys.argv[3],"
             "'root_version':1,'release_sequence':1},authorize=lambda *_:True);"
             "os._exit(17)")
        p=subprocess.run([sys.executable,'-B','-c',src,
            str(Path(w.__file__).parent),str(self.d),DEVICE],timeout=10,
            capture_output=True)
        self.assertEqual(p.returncode,17,p.stderr.decode(errors='replace'))
        self.assertIsNotNone(self.store.snapshot(DEVICE).pending)

    def test_34_process_exit_during_uncommitted_transaction_rolls_back(self):
        self.boot()
        code=("import sqlite3,os,sys;db=sqlite3.connect(sys.argv[1]);"
              "db.execute('BEGIN IMMEDIATE');"
              "db.execute('UPDATE devices SET generation=111 WHERE device=?',(sys.argv[2],));"
              "os._exit(17)")
        proc=subprocess.run([sys.executable,'-B','-c',code,self.store.path,DEVICE],
                            capture_output=True,timeout=10)
        self.assertEqual(proc.returncode,17)
        self.assertEqual(self.store.snapshot(DEVICE).generation,2)

    def test_35_malformed_pending_token_rejected(self):
        self.boot()
        with self.assertRaisesRegex(ValueError,'token'):
            self.store.finalize(DEVICE,'oops')

    def test_36_no_real_witness_or_distribution_calls(self):
        src=Path(w.__file__).read_text()
        for forbidden in ('requests.post','urllib.request','r2.put(',
                          'subprocess.run','genpkey','-sign\''):
            self.assertNotIn(forbidden,src)
        self.assertIn('NOT an independent trust anchor',src)

    def test_37_generation_increments_on_abort(self):
        self.boot();t=self.begin(rec(2),rec())
        self.assertEqual(self.store.abort(DEVICE,t).generation,4)

    def test_38_generation_increments_on_finalize(self):
        self.boot();t=self.begin(rec(2),rec())
        self.assertEqual(self.store.finalize(DEVICE,t).generation,4)

    def test_39_reopen_has_same_generation(self):
        self.boot();first=self.store.snapshot(DEVICE).generation
        self.assertEqual(w.TestWitnessStore(self.d,test_mode=True).snapshot(DEVICE).generation,first)

    def test_41_old_local_database_restore_remains_possible(self):
        """Explicit LIMITATION: local SQLite is NOT independent high-water trust."""
        import shutil
        self.boot()
        saved=self.d/'attacker-snapshot-copy'
        shutil.copy2(self.store.path,saved)
        t=self.begin(rec(2),rec());self.store.finalize(DEVICE,t)
        self.assertEqual(self.store.snapshot(DEVICE).committed['release_sequence'],2)
        shutil.copy2(saved,self.store.path)
        # This is evidence of a MISSING SECURITY PROPERTY, not a PASS for
        # rollback protection. A remote/independent witness is mandatory.
        self.assertEqual(self.store.snapshot(DEVICE).committed['release_sequence'],1)

    def test_40_documented_no_production_claim(self):
        self.assertEqual(w.DB_NAME,'test-witness.sqlite3')
        self.assertTrue(w.TestWitnessStore.__doc__.find('NOT authoritative')!=-1)

if __name__=='__main__':unittest.main()
