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
 # Rust uses the reviewed Python/Android packages and, on ARM64 only, the signed Expo Clang/LLD/libc development package rows. No Expo runtime is installed here.
 awk -F '\t' -v a="$as_arch" -v h="$1" -v s="$2" -v u="$3" '($1=="PYTHON_ANDROID" || $1=="ANDROID_CORE" || ($2=="arm64" && $1=="EXPO_REACT_NATIVE")) && $2==a && $6==h && $7==s && $8==u{ok=1}END{exit !ok}' /workspace/runtime/apt-module-packages-v1.tsv
}
appforge_snapshot_apt_get update
appforge_snapshot_apt_get install -y --no-install-recommends python3=3.12.3-0ubuntu2.1 git=1:2.43.0-1ubuntu7.3
/usr/bin/python3.12 -I -S -c 'import tomllib, tarfile, lzma, hashlib, json; import sys; assert sys.version_info[:2] == (3, 12)'

/usr/bin/git --version

# Native Linux Cargo executable probes require a C linker driver. On ARM64
# the required packages are already pinned in the reviewed R2/Ubuntu catalog
# under EXPO_REACT_NATIVE. Ownership and APT receipts remain RUST-scoped.
# Acquire exclusively through the existing Ed25519/GPG/SHA-256 approved APT path.
case "$(dpkg --print-architecture)" in
 arm64)
  appforge_snapshot_apt_get install -y --no-install-recommends \
    'clang=1:18.0-59~exp2' 'lld=1:18.0-59~exp2' \
    'libc6-dev=2.39-0ubuntu8.9'
  [ -x /usr/bin/clang ] && [ -x /usr/bin/ld.lld ] || {
    echo 'APPFORGE_RUST_LINKER_NOT_INSTALLED' >&2; exit 68;
  }
  /usr/bin/clang --version >/dev/null
  /usr/bin/ld.lld --version >/dev/null
  [ -f /usr/lib/aarch64-linux-gnu/Scrt1.o ] || {
    echo 'APPFORGE_RUST_CRT_OBJECT_MISSING' >&2; exit 69;
  }
  ;;
 amd64)
  # No pinned amd64 native clang package set exists in this release. Fail
  # closed if the verified Ubuntu image lacks an existing host C driver.
  [ -x /usr/bin/cc ] || { echo 'APPFORGE_RUST_AMD64_LINKER_NOT_PINNED' >&2; exit 68; }
  /usr/bin/cc --version >/dev/null
  ;;
 *) echo 'APPFORGE_RUST_HOST_UNSUPPORTED' >&2; exit 60 ;;
esac
