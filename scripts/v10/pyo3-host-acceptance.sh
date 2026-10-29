#!/usr/bin/env bash
set -euo pipefail
# Both modes use temporary copies of the exact promoted manifest/lock pair.
# Candidate is an optional isolated recheck, never an implicit resolver/update.
mode=${1:-shipped}
case "$mode" in shipped|candidate) ;; *) echo "Invalid probe mode" >&2; exit 2 ;; esac
test "$(uname -s)" = Linux
test "$(uname -m)" = x86_64
test "$(rustc --version | cut -d ' ' -f 2)" = 1.85.1
test "$(cargo --version | cut -d ' ' -f 2)" = 1.85.1
python3 -c 'import sys; assert sys.version_info[:3] == (3, 12, 12)'
rustc --version
cargo --version
python3 --version
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
cp examples/rust-python/Cargo.toml examples/rust-python/Cargo.lock "$fixture/"
cp -R examples/rust-python/src "$fixture/src"
export CARGO_TARGET_DIR="$fixture/target"
python3 - "$fixture" <<'PYPAIR'
import hashlib, pathlib, sys, tomllib
root = pathlib.Path(sys.argv[1])
manifest = tomllib.loads((root / 'Cargo.toml').read_text())
assert manifest['dependencies']['pyo3'] == {
    'version': '=0.29.0', 'features': ['extension-module', 'abi3-py312']}
data = (root / 'Cargo.lock').read_bytes()
assert len(data) == 3391
assert hashlib.sha256(data).hexdigest() == 'a3be190006d0345960589c281dc5b913a26325c4dd4efbd2b117a2fe3e948f1a'
lock = tomllib.loads(data.decode('utf-8'))
assert lock['version'] == 4
packages = lock['package']
family = {'pyo3', 'pyo3-build-config', 'pyo3-ffi', 'pyo3-macros', 'pyo3-macros-backend'}
assert {x['name'] for x in packages if x['name'].startswith('pyo3')} == family
assert all(x['version'] == '0.29.0' for x in packages if x['name'] in family)
assert [x for x in packages if x['name'] == 'appforge_native'] == [
    {'name': 'appforge_native', 'version': '0.1.0', 'dependencies': ['pyo3']}]
PYPAIR
export PYO3_PYTHON
PYO3_PYTHON=$(command -v python3)
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
