import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import os from 'node:os';
import path from 'node:path';
const workflow = fs.readFileSync(new URL('../../.github/workflows/v10-host-verification.yml', import.meta.url), 'utf8');
test('V10 host verification has an exact push trigger, is branch restricted and cannot publish', () => {
  assert.match(workflow, /on:\n  workflow_dispatch:\n  push:\n    branches:\n      - feat\/v10-oss-unified-20261028\n    tags-ignore:\n      - '\*\*'\n\npermissions:\n  contents: read/);
  assert.match(workflow, /if: github.ref == 'refs\/heads\/feat\/v10-oss-unified-20261028'/);
  assert.match(workflow, /persist-credentials: false/);
  assert.match(workflow, /runs-on: ubuntu-24.04/);
  assert.doesNotMatch(workflow, /secrets\.|contents: write|id-token:|pull_request_target:|workflow_run:|upload-artifact|gh release|assembleDebug|bundleRelease|signingReport|keystore/i);
  const actions = [...workflow.matchAll(/uses: (\S+)/g)].map(m => m[1]);
  assert.equal(actions.length, 8);
  for (const action of actions) assert.match(action, /^[\w/-]+@[a-f0-9]{40}$/);
});
test('V10 compilation uses existing project pins and excludes packaging tasks', () => {
  const app = fs.readFileSync(new URL('../../android-app/app/build.gradle.kts', import.meta.url), 'utf8');
  const debug = fs.readFileSync(new URL('../../.github/workflows/android-debug.yml', import.meta.url), 'utf8');
  for (const pin of ['28.2.13676358', '3.22.1']) {
    assert.ok(app.includes(pin)); assert.ok(workflow.includes(pin));
  }
  assert.match(app, /compileSdk = 37/);
  assert.ok(debug.includes('platforms;android-37.0'));
  assert.ok(workflow.includes('platforms;android-37.0'));
  assert.match(workflow, /gradle-version: '9.3.1'/);
  assert.match(workflow, /testDebugUnitTest :app:compileDebugKotlin :app:externalNativeBuildDebug/);
});
test('V10 host probes retain candidate isolation and bounded native launch', () => {
  assert.equal((workflow.match(/if: github.ref == 'refs\/heads\/feat\/v10-oss-unified-20261028'/g) || []).length, 3);
  assert.match(workflow, /runs-on: windows-2025/);
  assert.match(workflow, /expo export --platform android --max-workers 2\n/);
  const rust = fs.readFileSync(new URL('../../scripts/v10/pyo3-host-acceptance.sh', import.meta.url), 'utf8');
  assert.match(rust, /mktemp -d/);
  assert.match(rust, /cargo build .*--locked --release/);
  assert.match(rust, /spec.loader.exec_module\(m\)/);
  const windows = fs.readFileSync(new URL('../../scripts/v10/windows-host-acceptance.ps1', import.meta.url), 'utf8');
  assert.match(windows, /WaitForExit\(60000\)/);
  assert.match(windows, /Get-AuthenticodeSignature/);
  assert.match(windows, /NotSigned/);
  assert.match(windows, /localStorageCount/);
  assert.doesNotMatch(windows, /upload-artifact|signtool|gh release|CSC_LINK/);
});

test('V10 Windows explicitly installs local Electron before validating packaging tools', () => {
  const windows = workflow.slice(workflow.indexOf('  windows-native:'));
  const required = [
    'npm ci --prefix windows-host --no-audit --no-fund',
    "if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed' }",
    `Push-Location windows-host
          try {
            npx --no-install install-electron --no
            if ($LASTEXITCODE -ne 0) { throw 'Electron binary installation failed' }
          } finally {
            Pop-Location
          }`,
    `node -e "const fs=require('fs'); for(const p of ['windows-host/node_modules/electron/dist/electron.exe','windows-host/node_modules/.bin/electron-builder.cmd']) { if(!fs.statSync(p).isFile()) throw Error(p); }"`,
    "if ($LASTEXITCODE -ne 0) { throw 'Packaging tools missing' }",
    'run: ./scripts/v10/windows-host-acceptance.ps1',
  ];
  let previous = -1;
  for (const fragment of required) {
    const position = windows.indexOf(fragment);
    assert.ok(position > previous, `Missing or out-of-order prerequisite: ${fragment}`);
    previous = position;
  }
  assert.doesNotMatch(windows, /continue-on-error|--ignore-scripts/);
});

test('V10 promoted pin and authentic lock stay paired before real locked acceptance', () => {
  const rust = fs.readFileSync(new URL('../../scripts/v10/pyo3-host-acceptance.sh', import.meta.url), 'utf8');
  const manifest = fs.readFileSync(new URL('../../examples/rust-python/Cargo.toml', import.meta.url), 'utf8');
  const lock = fs.readFileSync(new URL('../../examples/rust-python/Cargo.lock', import.meta.url));
  assert.match(manifest, /pyo3=\{version="=0\.29\.0",features=\["extension-module","abi3-py312"\]\}/);
  assert.equal(lock.length, 3391);
  assert.equal(createHash('sha256').update(lock).digest('hex'), 'a3be190006d0345960589c281dc5b913a26325c4dd4efbd2b117a2fe3e948f1a');
  assert.doesNotMatch(rust, /0\.23\.5|cargo update|cargo generate-lockfile|PYO3_EXPORT_LOCK/);
  assert.doesNotMatch(workflow, /PYO3_EXPORT_LOCK|pyo3-host-acceptance.sh candidate/);
  assert.match(workflow, /run: bash scripts\/v10\/pyo3-host-acceptance.sh shipped/);
  assert.ok(rust.includes('mode=${1:-shipped}'));
  assert.ok(rust.includes('shipped|candidate'));
  for (const tool of ['rustc', 'cargo']) assert.ok(rust.includes(`$(${tool} --version | cut -d ' ' -f 2)" = 1.85.1`));
  assert.ok(rust.includes('sys.version_info[:3] == (3, 12, 12)'));
  assert.ok(rust.includes('test "$(uname -m)" = x86_64'));
  const pair = rust.indexOf("assert manifest['dependencies']['pyo3']");
  const build = rust.indexOf('cargo build ');
  const runtime = rust.indexOf('spec.loader.exec_module(m)');
  const unchanged = rust.indexOf('cmp "$fixture/resolved.lock" "$fixture/Cargo.lock"');
  assert.ok(pair > 0 && pair < build && build < runtime && runtime < unchanged);
  assert.ok(rust.includes('export CARGO_TARGET_DIR="$fixture/target"'));
  assert.ok(rust.includes('cp examples/rust-python/Cargo.toml examples/rust-python/Cargo.lock "$fixture/"'));
  assert.match(rust, /trap 'rm -rf "\$fixture"' EXIT/);
  for (const check of ['range(1000)', 'm.answer() == 42', 'm.answer(1)', 'except TypeError:', "raise AssertionError('unexpected argument accepted')"]) assert.ok(rust.includes(check));
  // Execute the actual pre-build validator against shipped bytes, then mismatches.
  const validator = rust.split("<<'PYPAIR'\n")[1].split('\nPYPAIR')[0];
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'appforge-pyo3-pair-'));
  try {
    fs.writeFileSync(`${dir}/Cargo.toml`, manifest);
    fs.writeFileSync(`${dir}/Cargo.lock`, lock);
    const validate = () => spawnSync('python3', ['-c', validator, dir], { encoding: 'utf8' });
    assert.equal(validate().status, 0);
    fs.appendFileSync(`${dir}/Cargo.lock`, '\n');
    assert.notEqual(validate().status, 0);
    fs.writeFileSync(`${dir}/Cargo.lock`, lock);
    fs.writeFileSync(`${dir}/Cargo.toml`, manifest.replace('=0.29.0', '=0.28.0'));
    assert.notEqual(validate().status, 0);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
