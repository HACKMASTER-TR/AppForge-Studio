"""V540 physical-failure guards: R2 signed libarchive & MinGW C++ driver."""
from pathlib import Path
import unittest

R=Path(__file__).resolve().parents[2]
T=R/'android-app/app/src/main/assets/device-build'
class NativeRecovery(unittest.TestCase):
    def test_cmake_signed_libarchive_soname_is_private(self):
        s=(T/'install-ndk-host-isolated-v7.sh').read_text()
        for x in ('LIBARCHIVE_REAL="$LIBDIR/libarchive.so.13.7.2"',
                  'LIBARCHIVE_SONAME="$LIBDIR/libarchive.so.13"',
                  'APPFORGE_NDK_LIBARCHIVE_PRIVATE_SONAME=PASS',
                  'cmp -s "$LIBARCHIVE_REAL"',
                  'APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER'):
            self.assertIn(x,s)
        self.assertLess(s.index('dpkg-deb -x'),s.index('LIBARCHIVE_REAL='))
        self.assertLess(s.index('APPFORGE_NDK_LIBARCHIVE_PRIVATE_SONAME=PASS'),s.index('cmake_probe_dir='))
        for bad in ('apt-get install','dpkg -i','LD_PRELOAD=','--allow-unauthenticated'):
            self.assertNotIn(bad,s)
    def test_mingw_gxx_restored_only_from_signed_package(self):
        s=(T/'install-windows-native-toolchain.sh').read_text()
        for x in ('APPFORGE_WINDOWS_GXX_SIGNED_DRIVER_RESTORED=PASS',
                  '8314d76080dc9979f8ebcfb2d70852804a6b765e38028990d8ff67ced712c99d',
                  'dpkg-deb -f "$archive" Version',
                  'dpkg-deb -x "$archive" "$recover"',
                  'dpkg --configure gcc-mingw-w64-x86-64-posix',
                  'dpkg --configure g++-mingw-w64-x86-64-posix'):
            self.assertIn(x,s)
        self.assertNotIn('ln -s "$(basename "$gcc_real")" "$gxx_link"',s)
        self.assertNotIn('dpkg --force',s)
        self.assertNotIn('apt-get -f install',s)
if __name__=='__main__': unittest.main()
