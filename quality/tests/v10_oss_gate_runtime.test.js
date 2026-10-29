import test from 'node:test';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root = fileURLToPath(new URL('../../', import.meta.url));
test('V10 scanner behavioral regression suite', () => {
  execFileSync('python3', ['-m', 'unittest', 'discover', '-s', 'quality/tests', '-p', 'test_v10_oss_gate.py'], {cwd:root, timeout:30000, stdio:'pipe'});
});
