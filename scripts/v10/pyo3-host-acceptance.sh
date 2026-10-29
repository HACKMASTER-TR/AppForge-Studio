#!/usr/bin/env bash
set -euo pipefail
# Temporary copies only; shipped pins remain unchanged.
mode=${1:-candidate}
case "$mode" in shipped|candidate) ;; *) echo "Invalid probe mode" >&2; exit 2 ;; esac
if [ "${PYO3_EXPORT_LOCK:-0}" = 1 ]; then
  test "$mode" = candidate
  test "$(rustc --version | cut -d ' ' -f 2)" = 1.85.1
  test "$(cargo --version | cut -d ' ' -f 2)" = 1.85.1
  python3 -c 'import sys; assert sys.version_info[:3] == (3, 12, 12)'
fi
test "$(uname -s)" = Linux
rustc --version
cargo --version
python3 --version
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
cp -R examples/rust-python/. "$fixture/"
if [ "$mode" = candidate ]; then
python3 - "$fixture/Cargo.toml" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1])
s = p.read_text()
assert 'version="=0.23.5"' in s
p.write_text(s.replace('version="=0.23.5"', 'version="=0.29.0"'))
PY
fi
export PYO3_PYTHON
PYO3_PYTHON=$(command -v python3)
if [ "$mode" = candidate ]; then
  cargo update --manifest-path "$fixture/Cargo.toml" -p pyo3 --precise 0.29.0
fi
sha256sum "$fixture/Cargo.lock"
cp "$fixture/Cargo.lock" "$fixture/resolved.lock"
cargo tree --manifest-path "$fixture/Cargo.toml" --locked
cargo build --manifest-path "$fixture/Cargo.toml" --locked --release
python3 - "$fixture/target/release/libappforge_native.so" <<'PY'
import importlib.util, pathlib, sys
assert sys.version_info >= (3, 12)
p = pathlib.Path(sys.argv[1])
spec = importlib.util.spec_from_file_location('appforge_native', p)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
for _ in range(1000):
    assert m.answer() == 42
try:
    m.answer(1)
except TypeError:
    pass
else:
    raise AssertionError('unexpected argument accepted')
print('PyO3 compile/import/runtime PASS; repository pin unchanged')
PY
cmp "$fixture/resolved.lock" "$fixture/Cargo.lock"
if [ "${PYO3_EXPORT_LOCK:-0}" = 1 ]; then
  # Only the resolver's non-executable lockfile is exported through job logs.
  # Base64 preserves exact bytes despite GitHub's per-line log timestamps.
  python3 - "$fixture/Cargo.lock" <<'PYLOCK'
import base64, hashlib, json, pathlib, re, sys, tomllib
p = pathlib.Path(sys.argv[1])
assert p.is_file() and not p.is_symlink()
data = p.read_bytes()
assert 0 < len(data) <= 65536
lock = tomllib.loads(data.decode('utf-8'))
assert lock['version'] == 4
packages = lock['package']
assert len({x['name'] for x in packages}) == len(packages)
family = {'pyo3', 'pyo3-build-config', 'pyo3-ffi', 'pyo3-macros', 'pyo3-macros-backend'}
assert {x['name'] for x in packages if x['name'].startswith('pyo3')} == family
names = {x['name'] for x in packages}
assert 'appforge_native' in names
for x in packages:
    if x['name'] == 'appforge_native':
        assert x == {'name': 'appforge_native', 'version': '0.1.0', 'dependencies': ['pyo3']}
        continue
    assert x['source'] == 'registry+https://github.com/rust-lang/crates.io-index'
    assert re.fullmatch('[a-f0-9]{64}', x['checksum'])
    assert set(x.get('dependencies', [])) <= names
    if x['name'] in family:
        assert x['version'] == '0.29.0'
print('APPFORGE_PYO3_CANDIDATE_LOCK_V1=' + json.dumps({
    'sha256': hashlib.sha256(data).hexdigest(),
    'size': len(data), 'base64': base64.b64encode(data).decode('ascii')}, separators=(',', ':')))
PYLOCK
fi
