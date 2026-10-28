"""Production readiness gate is intentionally fail-closed and review-only."""
from datetime import datetime, timedelta, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import appforge_v9_final_release_gate as gate

HEAD='cddde90bb2a6062e018242f8c7db4a223c25fd89'

class TestV9FinalReleaseGate(unittest.TestCase):
    def setUp(self):
        d=tempfile.TemporaryDirectory(prefix='v9-inventory-')
        self.addCleanup(d.cleanup)
        self.root=Path(d.name)
        self.doc=gate.template(HEAD)
        self.path=self.root/'inventory.json'
        self.write()

    def write(self):
        self.path.write_text(json.dumps(self.doc,indent=2)+'\n',encoding='utf8')

    def test_01_required_categories_are_complete(self):
        self.assertEqual(len(gate.EVIDENCE),11)
        self.assertEqual(len(self.doc['items']),11)

    def test_02_template_all_missing(self):
        state=gate.evaluate(self.path,expected_source=HEAD)
        self.assertEqual(len(state['missing']),11)
        self.assertFalse(state['production_ready'])

    def test_03_wrong_git_commit_rejected(self):
        with self.assertRaisesRegex(ValueError,'pinned baseline'):
            gate.evaluate(self.path,expected_source='0'*40)

    def test_04_invalid_sha_format_rejected(self):
        self.doc['source_commit']='1'*64;self.write()
        with self.assertRaisesRegex(ValueError,'Git SHA'):
            gate.evaluate(self.path)

    def test_05_missing_category_rejected(self):
        self.doc['items'].pop(gate.EVIDENCE[0]);self.write()
        with self.assertRaisesRegex(ValueError,'category'):
            gate.evaluate(self.path)

    def test_06_extra_category_rejected(self):
        self.doc['items']['fake']={};self.write()
        with self.assertRaisesRegex(ValueError,'category'):
            gate.evaluate(self.path)

    def test_07_duplicate_json_key_rejected(self):
        self.path.write_text('{"schema":1,"schema":1}')
        with self.assertRaisesRegex(ValueError,'duplicate key'):
            gate.evaluate(self.path)

    def test_08_symlinked_manifest_rejected(self):
        alias=self.root/'alias.json';alias.symlink_to(self.path)
        with self.assertRaisesRegex(ValueError,'regular file'):
            gate.evaluate(alias)

    def test_09_missing_item_cannot_claim_digest(self):
        self.doc['items'][gate.EVIDENCE[0]]['sha256']='0'*64;self.write()
        with self.assertRaisesRegex(ValueError,'cannot contain'):
            gate.evaluate(self.path)

    def submit(self, name=None, *, path='report.txt', content=b'only demo, NOT external proof'):
        if name is None:name=gate.EVIDENCE[0]
        (self.root/path).write_bytes(content)
        self.doc['items'][name]={
            'status':'SUBMITTED','evidence_file':path,'sha256':hashlib.sha256(content).hexdigest(),
            'reviewed_utc':(datetime.now(timezone.utc)-timedelta(minutes=1)).isoformat()}
        self.write()

    def test_10_single_hash_is_not_independent_attestation(self):
        self.submit()
        state=gate.evaluate(self.path)
        self.assertEqual(len(state['submitted_unverified']),1)
        self.assertFalse(state['production_ready'])

    def test_11_forged_all_submissions_still_not_ready(self):
        for i,k in enumerate(gate.EVIDENCE):self.submit(k,path=f'demo{i}.txt')
        state=gate.evaluate(self.path)
        self.assertEqual(len(state['missing']),0)
        self.assertFalse(state['production_ready'])

    def test_12_digest_mismatch_blocked(self):
        self.submit(); (self.root/'report.txt').write_text('tamper')
        with self.assertRaisesRegex(ValueError,'digest mismatch'):
            gate.evaluate(self.path)

    def test_13_missing_evidence_file_blocked(self):
        self.submit(); (self.root/'report.txt').unlink()
        with self.assertRaisesRegex(ValueError,'untrusted evidence'):
            gate.evaluate(self.path)

    def test_14_path_traversal_blocked(self):
        self.submit();self.doc['items'][gate.EVIDENCE[0]]['evidence_file']='../outside';self.write()
        with self.assertRaisesRegex(ValueError,'unsafe evidence'):
            gate.evaluate(self.path)

    def test_15_symlink_evidence_blocked(self):
        self.submit();alias=self.root/'ln.txt';alias.symlink_to(self.root/'report.txt')
        self.doc['items'][gate.EVIDENCE[0]]['evidence_file']='ln.txt';self.write()
        with self.assertRaisesRegex(ValueError,'untrusted evidence'):
            gate.evaluate(self.path)

    def test_16_future_review_timestamp_blocked(self):
        self.submit();self.doc['items'][gate.EVIDENCE[0]]['reviewed_utc']=(datetime.now(timezone.utc)+timedelta(days=2)).isoformat();self.write()
        with self.assertRaisesRegex(ValueError,'future or naive'):
            gate.evaluate(self.path)

    def test_17_naive_review_timestamp_blocked(self):
        self.submit();self.doc['items'][gate.EVIDENCE[0]]['reviewed_utc']='2026-10-28T12:34:00';self.write()
        with self.assertRaisesRegex(ValueError,'future or naive'):
            gate.evaluate(self.path)

    def test_18_invalid_status_rejected(self):
        self.doc['items'][gate.EVIDENCE[0]]['status']='PASS';self.write()
        with self.assertRaisesRegex(ValueError,'invalid status'):
            gate.evaluate(self.path)

    def test_19_cli_returns_blocked_even_all_missing(self):
        cmd=[sys.executable,str(Path(gate.__file__)),'--manifest',str(self.path),'--expected-source',HEAD]
        p=subprocess.run(cmd,capture_output=True,text=True,timeout=8)
        self.assertEqual(p.returncode,1)
        self.assertIn('V9_PRODUCTION_READINESS=BLOCKED',p.stdout)
        self.assertIn('ALL_ENGINES_READY=false',p.stdout)

    def test_20_cli_never_grants_ready_on_all_submitted(self):
        for i,k in enumerate(gate.EVIDENCE):self.submit(k,path=f'demo{i}.txt')
        p=subprocess.run([sys.executable,str(Path(gate.__file__)),'--manifest',str(self.path)],capture_output=True,text=True,timeout=8)
        self.assertEqual(p.returncode,1)
        self.assertIn('V9_PRODUCTION_READINESS=BLOCKED',p.stdout)
        self.assertNotIn('V9_PRODUCTION_READINESS=READY',p.stdout)

if __name__=='__main__': unittest.main()
