#!/usr/bin/env bash
set -euo pipefail
# Temporary copies only; shipped pins remain unchanged.
mode=${1:-candidate}
case "$mode" in shipped|candidate) ;; *) echo "Invalid probe mode" >&2; exit 2 ;; esac
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
