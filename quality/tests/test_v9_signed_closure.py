"""V9 Phase 3A: real GPGV signature fixtures and strict index/package closure."""
from datetime import datetime, timedelta, timezone
from email.utils import format_datetime
import hashlib
import os
import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

FILE = Path(__file__).resolve().parents[1] / 'tools' / 'appforge_signed_apt_closure_v9.py'
spec = importlib.util.spec_from_file_location('signed_closure_v9', FILE)
v9 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v9)

class APTClosureTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='appforge-signed-index-')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.repo = self.root / 'repo'
        self.assets = self.repo / v9.mirror.ASSETS
        self.assets.mkdir(parents=True)
        self.store = self.root / 'store'
        self.index = (b'Package: demo\nVersion: 1.0\nArchitecture: arm64\n'
                      b'Filename: pool/main/d/demo/demo_1.0_arm64.deb\n'
                      b'Size: 6\nSHA256: ' + b'a'*64 + b'\n\n')
        self.indexsha = hashlib.sha256(self.index).hexdigest()
        self._put(self.index)
        self.catalog_line = ('NODE_WEB\tarm64\tdemo\t1.0\tarm64\t' + 'a'*64 + '\t6\t' +
            v9.mirror.ORIGIN + 'pool/main/d/demo/demo_1.0_arm64.deb\n')
        (self.assets / 'apt-module-packages-v1.tsv').write_text(self.catalog_line)
        # Unsigned parser tests also need a structurally valid profile.
        # When GPG is available, _sign_all() replaces this placeholder
        # with freshly signed test fixtures.
        (self.assets / 'apt-snapshot-profile.tsv').write_text(
            ''.join(suite + '\t' + '0' * 64 + '\n' for suite in v9.SUITES))
        self.keyring = self.root / 'trusted-keyring.gpg'
        self.signer = None
        if shutil.which('gpg') and shutil.which('gpgv'):
            homedir = self.root / 'gpg'
            homedir.mkdir(mode=0o700)
            self.gpg_env = os.environ.copy()
            self.gpg_env['GNUPGHOME'] = str(homedir)
            try:
                subprocess.run(['gpg', '--batch', '--pinentry-mode', 'loopback', '--passphrase', '',
                    '--quick-generate-key', 'AppForge test-only <test@example.invalid>',
                    'ed25519', 'sign', '0'], check=True, capture_output=True, text=True, env=self.gpg_env, timeout=30)
            except subprocess.CalledProcessError as err:
                raise RuntimeError('GPG_KEYGEN_FAILED: ' + (err.stderr or err.stdout or 'unknown error')[-2500:]) from err
            f = subprocess.run(['gpg', '--batch', '--with-colons', '--fingerprint'],
                               capture_output=True, text=True, check=True, env=self.gpg_env)
            self.signer = next(x.split(':')[9] for x in f.stdout.splitlines() if x.startswith('fpr:'))
            pub = subprocess.run(['gpg', '--batch', '--export'], check=True,
                                 capture_output=True, env=self.gpg_env)
            self.keyring.write_bytes(pub.stdout)
            self._sign_all()

    def _put(self, content):
        digest = hashlib.sha256(content).hexdigest()
        dest = v9.mirror.object_path(self.store, digest)
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(content)
        return digest

    def _sign_all(self, expiry=2):
        profile = []
        now = datetime.now(timezone.utc)
        for suite in v9.SUITES:
            body = ('Origin: Ubuntu\nCodename: noble\nSuite: ' + suite + '\nDate: ' +
                    format_datetime(now - timedelta(minutes=10)) + '\nValid-Until: ' +
                    format_datetime(now + timedelta(days=expiry)) + '\nSHA256:\n')
            for component in v9.COMPONENTS:
                body += f' {self.indexsha} {len(self.index)} {component}/binary-arm64/Packages\n'
            untrusted = self.root / 'cleartext'
            signed = self.root / 'InRelease'
            untrusted.write_text(body)
            subprocess.run(['gpg','--batch','--yes','--armor','--clearsign','--output',str(signed),str(untrusted)],
                           check=True,capture_output=True,env=self.gpg_env)
            profile.append(suite + '\t' + self._put(signed.read_bytes()))
        (self.assets / 'apt-snapshot-profile.tsv').write_text('\n'.join(profile) + '\n')

    def _verify(self, **kwargs):
        arch=kwargs.pop('arch', 'arm64')
        return v9.verify_architecture(self.repo,self.store,self.keyring,arch,
             signer=self.signer, expected_profile=v9.mirror.sha_file(self.assets/'apt-snapshot-profile.tsv'),
             expected_catalog=v9.mirror.sha_file(self.assets/'apt-module-packages-v1.tsv'), **kwargs)

    def needs_gpg(self):
        if not self.signer:
            self.skipTest('GPG and GPGV not installed — signed fixture only')

    def test_signed_release_parser_requires_sha256(self):
        with self.assertRaisesRegex(ValueError,'signed SHA256'):
            v9.read_release_fields(b'Origin: Ubuntu\nDate: Tue, 27 Oct 2026 10:00:00 UTC\n')

    def test_parser_rejects_duplicate_index(self):
        body = (f'SHA256:\n {self.indexsha} 100 main/binary-arm64/Packages\n'
                f' {self.indexsha} 100 main/binary-arm64/Packages\n').encode()
        with self.assertRaisesRegex(ValueError,'duplicate signed index'):
            v9.read_release_fields(body)

    def test_package_parser_rejects_duplicate_identity(self):
        with self.assertRaisesRegex(ValueError,'duplicate Packages identity'):
            list(v9.package_rows(self.index.replace(b'Package: demo',b'Package: demo\nPackage: fake')))

    def test_invalid_catalog_url_is_blocked(self):
        (self.assets/'apt-module-packages-v1.tsv').write_text(self.catalog_line.replace('snapshot.ubuntu.com','evil.invalid'))
        with self.assertRaisesRegex(ValueError,'unpinned package source'):
            self._verify()

    def test_missing_signature_pins_fail_closed(self):
        self.needs_gpg()
        with self.assertRaisesRegex(ValueError,'pinned package catalog changed'):
            v9.release_tables(self.repo,self.store,self.keyring)

    def test_real_gpg_signature_and_all_16_indexes_verified(self):
        self.needs_gpg()
        result=self._verify()
        self.assertEqual(4,result['signed_suites'])
        self.assertEqual(16,result['verified_indexes'])
        self.assertEqual(1,result['catalog_identities'])
        self.assertTrue(result['catalog_closure'])

    def test_missing_packages_index_is_rejected(self):
        self.needs_gpg()
        v9.mirror.object_path(self.store,self.indexsha).unlink()
        with self.assertRaisesRegex(ValueError,'missing pinned object'):
            self._verify()

    def test_corrupted_packages_index_is_rejected(self):
        self.needs_gpg()
        v9.mirror.object_path(self.store,self.indexsha).write_bytes(b'X'*len(self.index))
        with self.assertRaisesRegex(ValueError,'object hash mismatch'):
            self._verify()

    def test_signed_package_closure_rejects_catalog_not_present(self):
        self.needs_gpg()
        (self.assets/'apt-module-packages-v1.tsv').write_text(self.catalog_line.replace('demo\t1.0','demo\t2.0'))
        with self.assertRaisesRegex(ValueError,'closure missing'):
            self._verify()

    def test_expired_metadata_is_rejected(self):
        self.needs_gpg()
        self._sign_all(expiry=-1)
        with self.assertRaisesRegex(ValueError,'expired signed metadata'):
            self._verify()

    def test_wrong_ubuntu_signer_is_rejected(self):
        self.needs_gpg()
        with self.assertRaisesRegex(ValueError,'unexpected Ubuntu signing fingerprint'):
            v9.verify_architecture(self.repo,self.store,self.keyring,'arm64',
                signer=v9.UBUNTU_2018_FPR,
                expected_profile=v9.mirror.sha_file(self.assets/'apt-snapshot-profile.tsv'),
                expected_catalog=v9.mirror.sha_file(self.assets/'apt-module-packages-v1.tsv'))

    def test_symlinked_index_is_rejected(self):
        self.needs_gpg()
        idx = v9.mirror.object_path(self.store, self.indexsha)
        original = self.root/'saved-index'
        idx.rename(original)
        idx.symlink_to(original)
        with self.assertRaisesRegex(ValueError,'symlinked object ancestor'):
            self._verify()

    def test_architecture_restriction(self):
        with self.assertRaisesRegex(ValueError,'unsupported architecture'):
            self._verify(arch='riscv64')

if __name__ == '__main__':
    unittest.main()
