import hashlib
import importlib.util
import json
from pathlib import Path
import unittest

MODULE_PATH = Path(__file__).resolve().parents[1] / 'tools/v549_r23_kotlin_bom.py'
spec = importlib.util.spec_from_file_location('v549_r23_bom', MODULE_PATH)
bom = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bom)

class TestPinnedBom(unittest.TestCase):
    def test_good_pom_and_metadata(self):
        pom = b'<project><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-gradle-plugins-bom</artifactId><version>2.4.10</version><packaging>pom</packaging></project>'
        bom.validate(pom, 'a.pom', hashlib.sha256(pom).hexdigest())
        module = json.dumps({'component':{'group':'org.jetbrains.kotlin','module':'kotlin-gradle-plugins-bom','version':'2.4.10'}}).encode()
        bom.validate(module, 'a.module', hashlib.sha256(module).hexdigest())
    def test_bad_hash_bad_coordinates_and_short_payload(self):
        pom = b'<project><groupId>evil</groupId><artifactId>kotlin-gradle-plugins-bom</artifactId><version>2.4.10</version><packaging>pom</packaging></project>'
        with self.assertRaises(ValueError):bom.validate(pom, 'a.pom', hashlib.sha256(pom).hexdigest())
        with self.assertRaises(ValueError):bom.validate(pom, 'a.pom', 'f'*64)
        with self.assertRaises(ValueError):bom.validate(b'x', 'a.pom', hashlib.sha256(b'x').hexdigest())

if __name__ == '__main__':unittest.main()
