#!/bin/sh
# Rust's internal Python data tools, without Android SDK/Chaquopy/Node.
# Reuses the exact frozen snapshot implementation; only Rust's owner/catalog
# projection differs. Shared APT scripts/catalog bytes remain unchanged.
set -eu
export DEBIAN_FRONTEND=noninteractive
[ "${APPFORGE_MODULE_ID:-}" = RUST ] || exit 60
[ "${APPFORGE_APT_MANAGED_INSTALL:-0}" = 1 ] || exit 60
[ -f /workspace/runtime/rust-apt-base.sh ] && [ ! -L /workspace/runtime/rust-apt-base.sh ] || exit 60
. /workspace/runtime/apt-download-telemetry.sh
. /workspace/runtime/rust-apt-base.sh
as_configure() {
 APPFORGE_MODULE_ID=PYTHON_ANDROID as_configure_original || return $?
 as_owner=RUST
 as_safe_dir /opt/appforge-device/apt-locks-v5/RUST/snapshot-v1 || return $?
 as_safe_dir /opt/appforge-device/apt-locks-v5/RUST/archives || return $?
 as_locks=/opt/appforge-device/apt-locks-v5/RUST/snapshot-v1
}
as_catalog_allows() {
 # Same signed/pinned Python package set already reviewed in this repository.
 # The request below is only Python, not the Android/Chaquopy module.
 awk -F '\t' -v a="$as_arch" -v h="$1" -v s="$2" -v u="$3" '$1=="PYTHON_ANDROID" && $2==a && $6==h && $7==s && $8==u{ok=1}END{exit !ok}' /workspace/runtime/apt-module-packages-v1.tsv
}
appforge_snapshot_apt_get update
appforge_snapshot_apt_get install -y --no-install-recommends python3=3.12.3-0ubuntu2.1
/usr/bin/python3.12 -I -S -c 'import tomllib, tarfile, lzma, hashlib, json; import sys; assert sys.version_info[:2] == (3, 12)'
