import importlib.util
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

SPEC = importlib.util.spec_from_file_location('v10gate', Path(__file__).parents[1] / 'tools/appforge_v10_oss_gate.py')
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)

class OssGateTests(unittest.TestCase):
    def test_missing_archive_is_not_run(self):
        with tempfile.TemporaryDirectory() as temp:
            result = gate.scan('gitleaks', Path(temp), Path(temp), machine='aarch64')
            self.assertEqual(result['status'], 'NOT_RUN')
            self.assertEqual(result['reason'], 'ARCHIVE_MISSING')

    def test_hash_mismatch_fails_before_execution(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            pin = gate.PINS['gitleaks']['artifacts']['aarch64']
            (root / pin['name']).write_bytes(b'invalid binary')
            self.assertEqual(gate.scan('gitleaks', root, root, machine='aarch64')['reason'], 'HASH_MISMATCH')

    def test_unreviewed_transitive_license_closure_cannot_execute(self):
        import hashlib
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            body = b'synthetic non-executable archive fixture'
            pin = dict(gate.PINS['gitleaks'])
            pin['artifacts'] = {'aarch64': {'name': 'fixture.tar.gz', 'sha256': hashlib.sha256(body).hexdigest()}}
            pin['license_closure_reviewed'] = False
            (root / 'fixture.tar.gz').write_bytes(body)
            with patch.dict(gate.PINS, {'gitleaks': pin}), patch.object(gate, 'execute') as execute:
                result = gate.scan('gitleaks', root, root, machine='aarch64')
                self.assertEqual(result['reason'], 'LICENSE_CLOSURE_UNVERIFIED')
                execute.assert_not_called()

    def test_unverified_abi_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            result = gate.scan('gitleaks', Path(temp), Path(temp), machine='riscv64')
            self.assertEqual(result['reason'], 'UNSUPPORTED_ABI')

    def test_reports_only_rule_location_never_secret_or_source(self):
        synthetic = 'synthetic-' + 'credential-value-for-negative-test'
        raw = [{'RuleID': 'generic-api-key', 'File': 'source/settings.txt', 'StartLine': 7,
                'Secret': synthetic, 'Match': synthetic, 'Line': synthetic}]
        result = gate.summarize_gitleaks(raw)
        self.assertEqual(result, [{'rule': 'generic-api-key', 'file': 'source/settings.txt', 'line': 7}])
        self.assertNotIn(synthetic, json.dumps(result))

    def test_malicious_report_fields_are_not_echoed(self):
        result = gate.summarize_gitleaks([{'RuleID': 'synthetic secret\n', 'File': '../credentials', 'StartLine': -1}])
        self.assertEqual(result, [{'rule': 'REDACTED', 'file': 'REDACTED', 'line': 0}])

    def test_exit_mapping(self):
        self.assertEqual(gate.classify('gitleaks', 0), 'PASS')
        self.assertEqual(gate.classify('gitleaks', 42), 'FINDINGS')
        self.assertEqual(gate.classify('gitleaks', 1), 'BLOCKED')
        self.assertEqual(gate.classify('gitleaks', -9), 'BLOCKED')

    def test_snapshot_rejects_traversal_symlink_and_local_files(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); source = root / 'repo'; source.mkdir(); dest = root / 'scan'; dest.mkdir()
            (source / 'safe.txt').write_text('reviewed')
            (source / 'link').symlink_to(source / 'safe.txt')
            for names in [['../escape'], ['/absolute'], ['link'], ['.env'], ['local.properties']]:
                with self.assertRaises(gate.Blocked): gate.snapshot(source, dest, names, 'gitleaks')
            gate.snapshot(source, dest, ['safe.txt'], 'gitleaks')
            self.assertEqual((dest / 'safe.txt').read_text(), 'reviewed')

    def test_snapshot_resource_limit(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); (root / 'big').write_bytes(b'a' * 20); dest = root / 'out'; dest.mkdir()
            with self.assertRaises(gate.Blocked): gate.snapshot(root, dest, ['big'], 'gitleaks', file_limit=10)

    def test_archive_zip_slip_never_extracts(self):
        import tarfile
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); archive = root / 'bad.tar.gz'
            with tarfile.open(archive, 'w:gz') as tar:
                entry = tarfile.TarInfo('../gitleaks'); entry.size = 1; tar.addfile(entry, io.BytesIO(b'x'))
            with self.assertRaises(gate.Blocked): gate.extract_binary(archive, root / 'tool', 'gitleaks')
            self.assertFalse((root.parent / 'gitleaks').exists())

    def test_sbom_export_omits_private_urls_properties_and_license_text(self):
        synthetic = 'synthetic-' + 'credential-value-for-negative-test'
        raw = {'components': [{'name': 'fixture', 'version': '1.0',
            'externalReferences': [{'url': 'https://user:' + synthetic + '@registry.invalid'}],
            'properties': [{'name': 'secret', 'value': synthetic}],
            'licenses': [{'license': {'id': 'MIT', 'text': {'content': synthetic}}}]}]}
        result = gate.sanitize_sbom(raw, '1.55.0')
        self.assertNotIn(synthetic, json.dumps(result))
        self.assertEqual(result['bomFormat'], 'CycloneDX')
        self.assertEqual(result['components'][0]['licenses'], [{'license': {'id': 'MIT'}}])

    def test_scanner_stdout_and_stderr_are_discarded(self):
        import sys
        import os
        from unittest.mock import patch
        synthetic = 'synthetic-' + 'credential-value-for-negative-test'
        # Isolate output suppression from Linux-only RLIMIT_AS; Android's loader
        # cannot execute under that bound. Production scan rejects Android first.
        with tempfile.TemporaryDirectory() as temp, io.StringIO() as buf, redirect_stdout(buf), patch.object(gate, 'limits', os.setsid):
            root = Path(temp)
            self.assertEqual(gate.execute([sys.executable, '-c', 'import sys; print(sys.argv[1]); print(sys.argv[1], file=sys.stderr)', synthetic], root, root), 0)
            self.assertEqual(buf.getvalue(), '')

    def test_syft_offline_request_is_blocked_without_network_isolation(self):
        with tempfile.TemporaryDirectory() as temp:
            result = gate.scan('syft', Path(temp), Path(temp), offline=True)
            self.assertEqual(result['reason'], 'OFFLINE_NETWORK_ISOLATION_UNVERIFIED')

    def test_offline_osv_cannot_silently_fall_back_to_network(self):
        with tempfile.TemporaryDirectory() as temp:
            result = gate.scan('osv-scanner', Path(temp), Path(temp), offline=True)
            self.assertEqual(result['status'], 'BLOCKED')
            self.assertEqual(result['reason'], 'OFFLINE_DATABASE_AND_NETWORK_ISOLATION_UNVERIFIED')

if __name__ == '__main__': unittest.main()
