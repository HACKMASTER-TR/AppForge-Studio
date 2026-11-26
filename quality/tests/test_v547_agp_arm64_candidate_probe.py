import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
path = ROOT / 'quality/tools/v547_agp_arm64_candidate_probe.py'
spec = importlib.util.spec_from_file_location('arm64_probe', path)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class CandidateProbeTests(unittest.TestCase):
    def make_candidate(self, root, evil=False):
        artifact = root/'source'
        artifact.mkdir()
        rows = {}
        for rel in (
            'modules-2/files-2.1/com.android.tools.build/gradle/9.1.1/a.jar',
            'modules-2/files-2.1/com.android.application/com.android.application.gradle.plugin/9.1.1/a.pom',
        ):
            rows[rel] = b'dummy-' + rel.encode()
        if evil:
            rows['modules-2/../../escape.txt'] = b'escape'
        entries = [dict(path=k, bytes=len(v), sha256=hashlib.sha256(v).hexdigest())
                   for k,v in rows.items()]
        payload_bytes = sum(len(v) for v in rows.values())
        (artifact/'manifest.json').write_text(json.dumps({
            'schema':1, 'phase':'CI_CANDIDATE_NOT_SIGNED', 'agp':'9.1.1',
            'gradle':'9.3.1', 'files':entries,
        }))
        tgz = artifact/mod.ARTIFACT
        with tarfile.open(tgz, 'w:gz') as tar:
            for name, content in rows.items():
                entry = tarfile.TarInfo(name)
                entry.size = len(content)
                tar.addfile(entry, io.BytesIO(content))
        (artifact/'archive.sha256').write_text(mod.sha_file(tgz)+'  '+mod.ARTIFACT+'\n')
        return artifact, payload_bytes

    def test_good_candidate_only_in_disposable_destination(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);artifact,size=self.make_candidate(root)
            output=root/'cache'
            self.assertEqual(mod.audit_and_expand(artifact,output,2,size),2)
            self.assertTrue((output/'modules-2/files-2.1/com.android.tools.build/gradle/9.1.1/a.jar').is_file())
            with self.assertRaises(AssertionError):
                mod.audit_and_expand(artifact,output,2,size)

    def test_rejects_tamper(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);artifact,size=self.make_candidate(root)
            archive=artifact/mod.ARTIFACT
            with archive.open('ab') as f: f.write(b'modified')
            with self.assertRaisesRegex(AssertionError,'ARCHIVE_SHA256_MISMATCH'):
                mod.audit_and_expand(artifact,root/'cache',2,size)
            self.assertFalse((root/'cache').exists())

    def test_rejects_traversal_before_write(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);artifact,size=self.make_candidate(root,evil=True)
            with self.assertRaisesRegex(AssertionError,'UNSAFE_ENTRY'):
                mod.audit_and_expand(artifact,root/'cache',3,size)
            self.assertFalse((root/'cache').exists())

if __name__ == '__main__': unittest.main()
