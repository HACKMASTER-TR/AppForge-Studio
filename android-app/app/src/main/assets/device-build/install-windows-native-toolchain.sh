#!/bin/sh
set -eu
export APPFORGE_MODULE_ID="${APPFORGE_MODULE_ID:-WINDOWS_NATIVE}"
if [ -f /workspace/runtime/module-download-telemetry.sh ]; then
  . /workspace/runtime/module-download-telemetry.sh
fi

ROOT="/opt/appforge-device/windows-native-v1"
READY="$ROOT/.ready"
OFFLINE="${APPFORGE_DEVICE_OFFLINE:-0}"

CC="x86_64-w64-mingw32-gcc-posix"
CXX="x86_64-w64-mingw32-g++-posix"

mkdir -p "$ROOT"

have_tools() {
  command -v cmake >/dev/null 2>&1 &&
  command -v ninja >/dev/null 2>&1 &&
  command -v "$CC" >/dev/null 2>&1 &&
  command -v "$CXX" >/dev/null 2>&1 &&
  command -v x86_64-w64-mingw32-windres >/dev/null 2>&1 &&
  command -v file >/dev/null 2>&1
}

# Restore only signed, version-pinned Ubuntu ARM64 MinGW POSIX drivers.
# GCC must be restored before its dpkg postinst registers update-alternatives.
# No --force, no reset of /var/lib/dpkg, and no shared rootfs mutation.
restore_signed_posix_driver() {
  kind="$1"
  case "$kind" in
    gcc)
      target=/usr/bin/x86_64-w64-mingw32-gcc-posix
      pkg=gcc-mingw-w64-x86-64-posix
      hash=de7ed842e1a166be9726952538cfe520d6e55ff07e4fae009cddbee74ca5de64
      ;;
    gxx)
      target=/usr/bin/x86_64-w64-mingw32-g++-posix
      pkg=g++-mingw-w64-x86-64-posix
      hash=8314d76080dc9979f8ebcfb2d70852804a6b765e38028990d8ff67ced712c99d
      ;;
    *) return 43;;
  esac
  [ -x "$target" ] && return 0
  # A missing alternatives name is not owned by dpkg, even though the
  # signed POSIX driver was authenticated before installation.
  # Only a genuinely absent name may use the signed-byte restoration path.
  target_absent_before_copy=0
  if [ ! -e "$target" ] && [ ! -L "$target" ];then
    target_absent_before_copy=1
  fi
  # A failed Ubuntu postinst can leave an existing non-executable file or
  # dangling alternatives link. The previous name-only check incorrectly
  # rejected such dpkg-owned paths. Never replace an unowned/foreign path.
  if [ -L "$target" ] || [ -e "$target" ]; then
    if [ ! -L "$target" ] && [ ! -f "$target" ]; then
      echo "APPFORGE_WINDOWS_${kind}_EXISTING_PATH_UNTRUSTED" >&2;return 43
    fi
    owner=$(dpkg-query -S "$target" 2>/dev/null || :) 
    [ "$owner" = "$pkg: $target" ] || {
      echo "APPFORGE_WINDOWS_${kind}_EXISTING_PATH_UNTRUSTED" >&2;return 43
    }
    installed_version=$(dpkg-query -W -f='${Version}' "$pkg" 2>/dev/null || :)
    [ "$installed_version" = '13.2.0-6ubuntu1+26.1' ] || {
      echo "APPFORGE_WINDOWS_${kind}_EXISTING_VERSION_UNTRUSTED" >&2;return 43
    }
    echo "APPFORGE_WINDOWS_${kind}_OWNED_INTERRUPTED_PATH_VERIFIED"
  fi
  [ "$(dpkg --print-architecture)" = arm64 ] || return 43
  [ "${APPFORGE_R2_RELEASE_AUTHORIZED:-0}" = 1 ] || return 43
  # Only the SHA-pinned R2 cache within this isolated candidate is trusted.
  archive_dir=/opt/appforge-device/apt-snapshot-v1/arm64-20261018T000000Z/archives
  archive="$archive_dir/$hash.deb"
  [ -d "$archive_dir" ] && [ ! -L "$archive_dir" ] || {
    echo "APPFORGE_WINDOWS_${kind}_R2_ARCHIVE_DIRECTORY_MISSING" >&2; return 44;
  }
  [ -f "$archive" ] && [ ! -L "$archive" ] || {
    echo "APPFORGE_WINDOWS_${kind}_SIGNED_ARCHIVE_MISSING:$hash" >&2;return 44;
  }
  printf '%s  %s\n' "$hash" "$archive" | sha256sum -c - >/dev/null 2>&1 || {
    echo "APPFORGE_WINDOWS_${kind}_ARCHIVE_SHA256_MISMATCH" >&2;return 44;
  }
  [ "$(dpkg-deb -f "$archive" Package)" = "$pkg" ] || {
    echo "APPFORGE_WINDOWS_${kind}_ARCHIVE_PACKAGE_MISMATCH" >&2;return 44;
  }
  [ "$(dpkg-deb -f "$archive" Version)" = '13.2.0-6ubuntu1+26.1' ] || {
    echo "APPFORGE_WINDOWS_${kind}_ARCHIVE_VERSION_MISMATCH" >&2;return 44;
  }
  [ "$(dpkg-deb -f "$archive" Architecture)" = arm64 ] || {
    echo "APPFORGE_WINDOWS_${kind}_ARCHIVE_ARCH_MISMATCH" >&2;return 44;
  }
  echo "APPFORGE_WINDOWS_${kind}_SIGNED_ARCHIVE_VERIFIED=PASS"
  stage=$(mktemp -d /tmp/appforge-signed-posix.XXXXXXXX) || {
    echo "APPFORGE_WINDOWS_${kind}_TEMP_DIR_FAILED" >&2;return 44;
  }
  dpkg-deb -x "$archive" "$stage" || {
    echo "APPFORGE_WINDOWS_${kind}_ARCHIVE_EXTRACT_FAILED" >&2;
    rm -rf -- "$stage";return 44;
  }
  echo "APPFORGE_WINDOWS_${kind}_SIGNED_ARCHIVE_EXTRACTED=PASS"
  source_driver="$stage$target"
  echo "APPFORGE_WINDOWS_${kind}_SIGNED_SOURCE_KIND=$([ -L "$source_driver" ] && echo LINK || ([ -f "$source_driver" ] && echo FILE || echo MISSING))"
  # Ubuntu can package the versioned executable without the unversioned
  # alternatives name. Synthesize only this exact GCC13 link when the signed
  # executable is present and byte-for-byte identical to installed bytes.
  if [ "$kind" = gcc ];then
    if [ -L "$source_driver" ];then
      [ "$(readlink "$source_driver")" = x86_64-w64-mingw32-gcc-13-posix ] || {
        echo 'APPFORGE_WINDOWS_gcc_SIGNED_SOURCE_LINK_UNTRUSTED' >&2
        rm -rf -- "$stage";return 44
      }
    fi
    signed_versioned="$stage/usr/bin/x86_64-w64-mingw32-gcc-13-posix"
    installed_versioned=/usr/bin/x86_64-w64-mingw32-gcc-13-posix
    # A half-configured dpkg candidate can lack GCC13's real executable.
    # Recover only its exact signed, dpkg-owned, version-pinned ARM64 ELF.
    if [ -f "$signed_versioned" ] && [ -x "$signed_versioned" ] &&
       [ ! -x "$installed_versioned" ]; then
      [ ! -L "$installed_versioned" ] || {
        echo "APPFORGE_WINDOWS_gcc_VERSIONED_SYMLINK_UNTRUSTED" >&2;rm -rf -- "$stage";return 44;
      }
      original_owner=$(dpkg-query -S "$installed_versioned" 2>/dev/null || :)
      [ "$original_owner" = "$pkg: $installed_versioned" ] || {
        echo "APPFORGE_WINDOWS_gcc_VERSIONED_OWNER_UNTRUSTED" >&2;rm -rf -- "$stage";return 44;
      }
      signed_header=$(od -An -tx1 -N6 "$signed_versioned" | tr -d ' \n')
      signed_machine=$(od -An -tx1 -j18 -N2 "$signed_versioned" | tr -d ' \n')
      [ "$signed_header" = 7f454c460201 ] && [ "$signed_machine" = b700 ] || {
        echo "APPFORGE_WINDOWS_gcc_VERSIONED_ELF_UNTRUSTED" >&2;rm -rf -- "$stage";return 44;
      }
      signed_tmp="/usr/bin/.appforge-gcc13-posix.$$"
      [ ! -e "$signed_tmp" ] && [ ! -L "$signed_tmp" ] || {
        rm -rf -- "$stage";return 44;
      }
      cp -p "$signed_versioned" "$signed_tmp" && cmp -s "$signed_versioned" "$signed_tmp" || {
        rm -f -- "$signed_tmp";rm -rf -- "$stage";return 44;
      }
      mv -T "$signed_tmp" "$installed_versioned" || {
        rm -f -- "$signed_tmp";rm -rf -- "$stage";return 44;
      }
      echo "APPFORGE_WINDOWS_gcc_SIGNED_VERSIONED_DRIVER_RESTORED=PASS"
    fi
    if [ -f "$signed_versioned" ] && [ -x "$signed_versioned" ] &&
       [ -f "$installed_versioned" ] && [ ! -L "$installed_versioned" ] &&
       [ -x "$installed_versioned" ] &&
       cmp -s "$signed_versioned" "$installed_versioned";then
      if [ ! -e "$source_driver" ] && [ ! -L "$source_driver" ];then
        ln -s x86_64-w64-mingw32-gcc-13-posix "$source_driver" || {
          echo "APPFORGE_WINDOWS_gcc_SIGNED_ALIAS_STAGE_FAILED" >&2
          rm -rf -- "$stage";return 44
        }
      fi
      echo 'APPFORGE_WINDOWS_gcc_SIGNED_VERSIONED_DRIVER_VERIFIED'
    fi
  fi
  tmp="/usr/bin/.appforge-${kind}-posix.$$"
  [ ! -e "$tmp" ] && [ ! -L "$tmp" ] || {
    echo "APPFORGE_WINDOWS_${kind}_TMP_TARGET_ALREADY_EXISTS" >&2;rm -rf -- "$stage";return 43;
  }
  if [ -L "$source_driver" ]; then
    link=$(readlink "$source_driver") || { rm -rf -- "$stage";return 44; }
    case "$kind:$link" in
      gcc:x86_64-w64-mingw32-gcc-[0-9]*-posix|gxx:x86_64-w64-mingw32-g++-[0-9]*-posix) ;;
      *) rm -rf -- "$stage";return 44;;
    esac
    [ -x "/usr/bin/$link" ] || {
      echo "APPFORGE_WINDOWS_${kind}_SIGNED_TARGET_MISSING" >&2
      rm -rf -- "$stage";return 44;
    }
    ln -s "$link" "$tmp" || { rm -rf -- "$stage";return 44; }
  elif [ -f "$source_driver" ] && [ -x "$source_driver" ] && [ ! -L "$source_driver" ]; then
    command -v od >/dev/null 2>&1 || { echo "APPFORGE_WINDOWS_${kind}_OD_UNAVAILABLE" >&2;rm -rf -- "$stage";return 44; }
    magic=$(od -An -tx1 -N2 "$source_driver" | tr -d ' \n') || { rm -rf -- "$stage";return 44; }
    if [ "$magic" = 2321 ];then
      # Script is authenticated by the exact pinned Ubuntu .deb above.
      # Only a simple POSIX shell shebang is permitted as a driver.
      first_line=$(sed -n '1p' "$source_driver")
      case "$first_line" in
        '#!/bin/sh'|'#!/bin/sh -e')
          echo "APPFORGE_WINDOWS_${kind}_SIGNED_POSIX_SCRIPT=VERIFIED";;
        *) echo "APPFORGE_WINDOWS_${kind}_SIGNED_SCRIPT_INTERPRETER_DENIED" >&2;rm -rf -- "$stage";return 44;;
      esac
    else
      header=$(od -An -tx1 -N6 "$source_driver" | tr -d ' \n') || { rm -rf -- "$stage";return 44; }
      machine=$(od -An -tx1 -j18 -N2 "$source_driver" | tr -d ' \n') || { rm -rf -- "$stage";return 44; }
      [ "$header" = 7f454c460201 ] && [ "$machine" = b700 ] || {
        echo "APPFORGE_WINDOWS_${kind}_SIGNED_ELF_ARCH_MISMATCH" >&2
        rm -rf -- "$stage";return 44;
      }
      echo "APPFORGE_WINDOWS_${kind}_SIGNED_ARM64_ELF=VERIFIED"
    fi
    cp -p "$source_driver" "$tmp" || {
      echo "APPFORGE_WINDOWS_${kind}_SIGNED_COPY_FAILED" >&2;rm -rf -- "$stage";return 44;
    }
    cmp -s "$source_driver" "$tmp" || {
      echo "APPFORGE_WINDOWS_${kind}_SIGNED_COPY_MISMATCH" >&2;rm -f -- "$tmp";rm -rf -- "$stage";return 44;
    }
  else
    echo "APPFORGE_WINDOWS_${kind}_AUTHENTIC_DRIVER_MISSING_IN_SIGNED_DEB" >&2
    rm -rf -- "$stage";return 44
  fi
  # R31 DIAG V2: executable mode is enforced ONLY on authenticated copied
  # regular bytes, before the atomic move. Never chmod an untrusted path.
  if [ -f "$source_driver" ] && [ ! -L "$source_driver" ];then
    [ -f "$tmp" ] && [ ! -L "$tmp" ] &&
    cmp -s "$source_driver" "$tmp" || {
      echo "APPFORGE_WINDOWS_${kind}_SIGNED_TMP_BYTES_MISMATCH" >&2
      rm -f -- "$tmp";rm -rf -- "$stage";return 44
    }
    chmod 0755 "$tmp" || {
      echo "APPFORGE_WINDOWS_${kind}_SIGNED_TMP_CHMOD_FAILED" >&2
      rm -f -- "$tmp";rm -rf -- "$stage";return 44
    }
    echo "APPFORGE_WINDOWS_${kind}_SIGNED_TMP_MODE=VERIFIED"
  fi
  # Atomic replacement only after the fixed package and executable checks.
  if ! mv -T "$tmp" "$target"; then
    echo "APPFORGE_WINDOWS_${kind}_ATOMIC_REPLACE_FAILED" >&2
    rm -f -- "$tmp";rm -rf -- "$stage";return 44
  fi
  # An interrupted PRoot/dpkg state can preserve the signed bytes while
  # losing executable permission on the versioned alternatives path.
  # Restore x-bit only after independently comparing the installed file to
  # the exact authenticated archive bytes and checking package ownership.
  if [ ! -x "$target" ];then
    # Read-only PRoot/dpkg diagnostics distinguish missing, non-executable,
    # unowned and changed paths. No guard is bypassed on disagreement.
    if [ -L "$target" ];then target_kind=SYMLINK
    elif [ -f "$target" ];then target_kind=REGULAR
    elif [ -e "$target" ];then target_kind=OTHER
    else target_kind=MISSING;fi
    echo "APPFORGE_WINDOWS_${kind}_AFTER_MOVE_TYPE=$target_kind"
    echo "APPFORGE_WINDOWS_${kind}_BEFORE_COPY_ABSENT=$target_absent_before_copy"
    target_mode=$(stat -c %a "$target" 2>/dev/null || printf 'UNAVAILABLE')
    echo "APPFORGE_WINDOWS_${kind}_AFTER_MOVE_MODE=$target_mode"
    if [ -f "$source_driver" ] && [ ! -L "$source_driver" ];then
      echo "APPFORGE_WINDOWS_${kind}_SIGNED_STAGE_REGULAR=YES"
    else
      echo "APPFORGE_WINDOWS_${kind}_SIGNED_STAGE_REGULAR=NO"
    fi
    if [ -f "$target" ] && [ ! -L "$target" ];then
      echo "APPFORGE_WINDOWS_${kind}_AFTER_MOVE_REGULAR=YES"
    else
      echo "APPFORGE_WINDOWS_${kind}_AFTER_MOVE_REGULAR=NO"
    fi
    if cmp -s "$source_driver" "$target" 2>/dev/null;then
      echo "APPFORGE_WINDOWS_${kind}_SIGNED_BYTES_COMPARE=PASS"
    else
      echo "APPFORGE_WINDOWS_${kind}_SIGNED_BYTES_COMPARE=FAIL"
    fi
    installed_owner=$(dpkg-query -S "$target" 2>/dev/null || :)
    if [ "$installed_owner" = "$pkg: $target" ];then
      echo "APPFORGE_WINDOWS_${kind}_DPKG_TARGET_OWNER=MATCH"
    else
      echo "APPFORGE_WINDOWS_${kind}_DPKG_TARGET_OWNER=NOT_MATCHED"
    fi
    # R31 SAFE STAT FALLBACK: /bin/sh -f/-x may return false after an
    # atomic move inside PRoot even when GNU stat reports the authentic ELF.
    # Independent lstat type must be REGULAR (never a link), mode exactly 755,
    # package owner exact, and bytes identical to the signed Ubuntu payload.
    target_stat_kind=$(stat -c %F -- "$target" 2>/dev/null || printf 'UNAVAILABLE')
    target_stat_mode=$(stat -c %a -- "$target" 2>/dev/null || printf 'UNAVAILABLE')
    echo "APPFORGE_WINDOWS_${kind}_STAT_KIND=$target_stat_kind"
    echo "APPFORGE_WINDOWS_${kind}_STAT_MODE=$target_stat_mode"
    if [ "$target_stat_kind" = 'regular file' ] &&
       [ "$target_stat_mode" = 755 ] &&
       { [ "$installed_owner" = "$pkg: $target" ] ||
         [ "$target_absent_before_copy" = 1 ]; } &&
       [ -f "$source_driver" ] && [ ! -L "$source_driver" ] &&
       cmp -s "$source_driver" "$target";then
      echo "APPFORGE_WINDOWS_${kind}_RESTORED_SIGNED_BYTES=VERIFIED"
      echo "APPFORGE_WINDOWS_${kind}_SIGNED_NEW_TARGET=$target_absent_before_copy"
      echo "APPFORGE_WINDOWS_${kind}_PROROOT_STAT_REGULAR_SIGNED=PASS"
      # Already mode 755: never chmod a target whose shell type conflicts.
      # A real --version exec below is required even after these proofs.
    else
      echo "APPFORGE_WINDOWS_${kind}_RESTORED_BYTES_OR_OWNERSHIP_MISMATCH" >&2
      rm -rf -- "$stage";return 44
    fi
  fi
  if [ ! -x "$target" ];then
    # Fail closed unless the same target STILL has all independent signed
    # regular-file proofs. Don't rely on PRoot's inconsistent -x predicate.
    independent_kind=$(stat -c %F -- "$target" 2>/dev/null || :)
    independent_mode=$(stat -c %a -- "$target" 2>/dev/null || :)
    independent_owner=$(dpkg-query -S "$target" 2>/dev/null || :)
    if [ "$independent_kind" != 'regular file' ] ||
       [ "$independent_mode" != 755 ] ||
       [ "$independent_owner" != "$pkg: $target" ] ||
       [ ! -f "$source_driver" ] || [ -L "$source_driver" ] ||
       ! cmp -s "$source_driver" "$target";then
      echo "APPFORGE_WINDOWS_${kind}_RESTORED_DRIVER_NOT_EXECUTABLE" >&2
      rm -rf -- "$stage";return 44
    fi
    echo "APPFORGE_WINDOWS_${kind}_EXEC_REQUIRES_PHYSICAL_PROBE=PASS"
  fi
  # This actually runs the verified ARM64 driver. No READY on failure.
  if ! "$target" --version >/dev/null;then
    echo "APPFORGE_WINDOWS_${kind}_RESTORED_DRIVER_EXEC_PROBE_FAILED" >&2
    rm -rf -- "$stage";return 44
  fi
  rm -rf -- "$stage"
  echo "APPFORGE_WINDOWS_${kind}_SIGNED_DRIVER_RESTORED=PASS"
}
repair_mingw_links() {
  restore_signed_posix_driver gcc
  restore_signed_posix_driver gxx
}

repair_interrupted_dpkg() {
  audit="$(
    dpkg --audit 2>/dev/null ||
    true
  )"

  if [ -z "$audit" ]; then
    return 0
  fi

  if ! printf '%s\n' "$audit" |
       grep -Eq 'mingw-w64|gcc-mingw|g\+\+-mingw'
  then
    echo "APPFORGE_WINDOWS_NATIVE_DPKG_UNRELATED_BROKEN_STATE" >&2
    printf '%s\n' "$audit" >&2
    return 43
  fi

  echo "APPFORGE_WINDOWS_NATIVE_DPKG_REPAIR=START"

  repair_mingw_links
  # Satisfy the C compiler dependency before g++'s postinst alternatives.
  # Already-configured packages must not be needlessly reconfigured.
  gcc_state=$(dpkg-query -W -f='${db:Status-Abbrev}' gcc-mingw-w64-x86-64-posix 2>/dev/null || true)
  case "$gcc_state" in
    ii*) ;;
    iU*|iF*|iH*)
      dpkg --configure gcc-mingw-w64-x86-64-posix || {
        echo 'APPFORGE_WINDOWS_GCC_POSIX_CONFIGURE_BLOCKED' >&2;return 44;
      };;
    *) echo 'APPFORGE_WINDOWS_GCC_POSIX_STATE_UNVERIFIED' >&2;return 44;;
  esac
  gxx_state=$(dpkg-query -W -f='${db:Status-Abbrev}' g++-mingw-w64-x86-64-posix 2>/dev/null || true)
  case "$gxx_state" in
    ii*) ;;
    iU*|iF*|iH*)
      dpkg --configure g++-mingw-w64-x86-64-posix || {
        echo 'APPFORGE_WINDOWS_GXX_POSIX_CONFIGURE_BLOCKED' >&2;return 44;
      };;
    *) echo 'APPFORGE_WINDOWS_GXX_POSIX_STATE_UNVERIFIED' >&2;return 44;;
  esac
  dpkg --configure -a

  echo "APPFORGE_WINDOWS_NATIVE_DPKG_REPAIR=PASS"
}

verify_smoke() {
  smoke="$ROOT/smoke"

  rm -rf "$smoke"
  mkdir -p "$smoke"

  cat > "$smoke/main.cpp" <<'CPP'
#include <windows.h>

int WINAPI WinMain(
    HINSTANCE,
    HINSTANCE,
    LPSTR,
    int
) {
    return 0;
}
CPP

  "$CXX" \
    -O2 \
    -s \
    -static-libgcc \
    -static-libstdc++ \
    "$smoke/main.cpp" \
    -o "$smoke/appforge-windows-native-smoke.exe"

  test -s "$smoke/appforge-windows-native-smoke.exe"

  file "$smoke/appforge-windows-native-smoke.exe" |
    grep -q 'PE32+ executable'

  rm -rf "$smoke"
}

repair_interrupted_dpkg

WINDOWS_LOCK="/opt/appforge-device/apt-locks-v5/WINDOWS_NATIVE/snapshot-v1"
have_windows_lock() {
  [ -d "$WINDOWS_LOCK" ] && [ ! -L "$WINDOWS_LOCK" ] || return 1
  find "$WINDOWS_LOCK" -mindepth 2 -maxdepth 2 -name installed.tsv -type f -print -quit | grep -q .
}

if [ "${APPFORGE_MODULE_REFRESH:-0}" != 1 ] && have_tools && have_windows_lock; then
  verify_smoke

  printf '%s\n' \
    'windows-native-v1' \
    > "$READY"

  echo "APPFORGE_WINDOWS_NATIVE_TOOLCHAIN_READY"
  exit 0
fi

if [ "$OFFLINE" = "1" ]; then
  echo "APPFORGE_WINDOWS_NATIVE_OFFLINE_TOOLCHAIN_MISSING" >&2
  echo "CMake/Ninja/MinGW-w64 POSIX x64 araçları önceden kurulmalı." >&2
  exit 42
fi

export DEBIAN_FRONTEND=noninteractive

# A plain apt-get would bypass authenticated R2 snapshot, use ports.ubuntu.com
# and fail DNS on mobile data. The module manager supplies the mandatory release
# authorization and separately owns this Linux filesystem.
managed_apt() {
  [ "${APPFORGE_APT_MANAGED_INSTALL:-0}" = 1 ] &&
  [ "${APPFORGE_R2_RELEASE_AUTHORIZED:-0}" = 1 ] || {
    echo 'APPFORGE_WINDOWS_SIGNED_R2_APT_AUTHORIZATION_REQUIRED' >&2;return 60;
  }
  wrapper=/opt/appforge-device/telemetry-bin/apt-get
  [ -f "$wrapper" ] && [ ! -L "$wrapper" ] &&
  [ -f /workspace/runtime/apt-download-telemetry.sh ] &&
  [ -f /workspace/runtime/apt-snapshot-lock.sh ] || {
    echo 'APPFORGE_WINDOWS_SIGNED_R2_APT_WRAPPER_MISSING' >&2;return 60;
  }
  /bin/sh "$wrapper" "$@"
}
managed_apt update

install_packages() {
  managed_apt install \
    -y \
    --no-install-recommends \
    --reinstall \
    ca-certificates \
    cmake \
    ninja-build \
    file \
    binutils-mingw-w64-x86-64 \
    gcc-mingw-w64-x86-64-posix \
    g++-mingw-w64-x86-64-posix \
    mingw-w64-x86-64-dev \
    mingw-w64-tools
}

if ! install_packages; then
  echo "APPFORGE_WINDOWS_NATIVE_APT_REPAIR=START"

  repair_mingw_links

  dpkg --configure -a

  # Only the requested pinned packages may be retried.
  install_packages

  echo "APPFORGE_WINDOWS_NATIVE_APT_REPAIR=PASS"
fi

repair_mingw_links

have_tools

verify_smoke

printf '%s\n' \
  'windows-native-v1' \
  > "$READY"

echo "APPFORGE_WINDOWS_NATIVE_TOOLCHAIN_INSTALL=PASS"
