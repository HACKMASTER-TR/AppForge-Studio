#!/usr/bin/env python3
"""Disposable CI emulator only. Refuses physical serials; never uninstalls or clears app data."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

PACKAGE='com.appforge.studio.v9r2isolated3'

def require_emulator(serial):
    if not re.fullmatch(r'emulator-[0-9]+', serial):
        raise ValueError('PHYSICAL_DEVICE_ACTION_FORBIDDEN')

def main():
    parser=argparse.ArgumentParser()
    for key in ['serial','old-apk','new-apk','test-apk','output']:
        parser.add_argument('--'+key,required=True)
    args=parser.parse_args();require_emulator(args.serial)
    def adb(*cmd):
        return subprocess.check_output(['adb','-s',args.serial,*cmd],text=True,stderr=subprocess.STDOUT)
    adb('install','-r',args.old_apk)
    # Synthetic fixture only, written before update to exercise Android package-manager preservation.
    adb('shell','run-as',PACKAGE,'mkdir','-p','files/storage-upgrade-fixture')
    fixture='appforge-v538-project-cache-receipt-fixture'
    for name in ['project.txt','receipt.json','verified-cache.bin']:
        subprocess.run(['adb','-s',args.serial,'shell','run-as',PACKAGE,'sh','-c',
                        f"'cat > files/storage-upgrade-fixture/{name}'"],input=fixture,text=True,check=True)
    adb('install','-r',args.new_apk)
    for name in ['project.txt','receipt.json','verified-cache.bin']:
        assert adb('shell','run-as',PACKAGE,'cat','files/storage-upgrade-fixture/'+name).strip()==fixture
    adb('install','-r',args.test_apk)
    result=adb('shell','am','instrument','-w','-r','-e','class',
               'com.appforge.studio.build.RuntimeStorageInstrumentedTest',
               PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner')
    if 'FAILURES' in result or 'OK (' not in result:
        raise RuntimeError('INSTRUMENTATION_FAILED: '+result)
    Path(args.output).write_text(json.dumps(dict(emulator='PASS',physical_device='NOT_RUN',
        from_version=538,to_version=539,synthetic_fixtures_preserved=True,instrumentation=result),indent=2)+'\n')

if __name__=='__main__':main()
