import importlib.util
import json
import pathlib
import tempfile
import unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
MOD=ROOT/'quality/tools/v547_agp_seed_manifest.py'
spec=importlib.util.spec_from_file_location('v547_manifest',MOD)
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
class TestV547AgpSeed(unittest.TestCase):
    def test_create_verify_and_tamper(self):
        with tempfile.TemporaryDirectory() as d:
            p=pathlib.Path(d)
            for part in ('com.android.tools.build/gradle/9.1.1/a.jar',
                         'com.android.application/com.android.application.gradle.plugin/9.1.1/plugin.pom'):
                path=p/'modules-2/files-2.1'/part
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_text('CI candidate',encoding='utf8')
            data=m.create(p)
            self.assertEqual(m.verify(p,data),2)
            target=p/'modules-2/files-2.1/com.android.tools.build/gradle/9.1.1/a.jar'
            target.write_text('tampered')
            with self.assertRaises(ValueError):m.verify(p,data)
            with self.assertRaises(ValueError):m.verify(p,dict(data,phase='SIGNED'))
    def test_symlink_refused(self):
        with tempfile.TemporaryDirectory() as d:
            p=pathlib.Path(d)
            child=p/'modules-2';child.mkdir()
            (child/'link').symlink_to('/etc/passwd')
            with self.assertRaises(ValueError):m.create(p)
if __name__ == '__main__':unittest.main()
