"""Executable shell-driver regressions with Git/GitHub boundaries mocked; no hosted CI claim."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/termux/github-apk.sh'
HEAD = 'b' * 40
MOCK = r'''#!/usr/bin/env python3
import json,os,pathlib,sys,zipfile,hashlib
name=pathlib.Path(sys.argv[0]).name;a=sys.argv[1:]
with open(os.environ['APPFORGE_TRACE'],'a') as f:f.write(json.dumps([name]+a)+'\n')
if name=='git':
 if a[:2]==['bundle','list-heads']:print('b'*40+' HEAD')
 elif 'clone' in a:pathlib.Path(a[-1]).mkdir(parents=True)
 elif a[:2]==['merge-base','--is-ancestor'] and os.environ.get('BAD_BASE'):sys.exit(1)
 elif a[:2]==['rev-parse','FETCH_HEAD']:print('b'*40)
 elif a[:2]==['branch','--show-current']:print(('codex/v9r2-inplace-upgrade-' if os.environ.get('UPGRADE') else 'codex/termux-apk-')+('b'*12))
 elif a and a[0]=='show':
  if os.environ.get('MISSING_GUARD'):print('unreviewed workflow')
  else:print("inputs.publish_latest_release == true && github.ref == 'refs/heads/main' dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082 appforgeV9R2Isolated3Upgrade=true")
elif name=='gh':
 if a[:2]==['api','user']:print('HACKMASTER-TR')
 elif a and a[0]=='api':print('false' if os.environ.get('NO_PUSH') else 'true')
 elif a[:2]==['pr','create']:print('https://github.com/HACKMASTER-TR/AppForge-Studio/pull/999')
 elif a[:2]==['run','list']:print(json.dumps([{'databaseId':999,'createdAt':'2999-01-01T00:00:00Z'}]))
 elif a[:2]==['run','watch'] and os.environ.get('WATCH_FAIL'):sys.exit(1)
 elif a[:2]==['run','view']:print(json.dumps({'workflowName':os.environ.get('WORKFLOW','AppForge V9 Isolated3 In-place Upgrade' if os.environ.get('UPGRADE') else 'AppForge Android Debug'),'headBranch':os.environ.get('BRANCH',('codex/v9r2-inplace-upgrade-' if os.environ.get('UPGRADE') else 'codex/termux-apk-')+('b'*12)),'headSha':os.environ.get('RUN_HEAD','b'*40),'status':'completed','conclusion':os.environ.get('CONCLUSION','success'),'url':'https://github.com/HACKMASTER-TR/AppForge-Studio/actions/runs/999'}))
 elif a[:2]==['run','download']:
  dest=pathlib.Path(a[a.index('--dir')+1]);dest.mkdir(parents=True,exist_ok=True)
  apk=dest/('AppForgeStudio-v9r2isolated3-upgrade.apk' if os.environ.get('UPGRADE') else 'AppForgeStudio-latest.apk')
  with zipfile.ZipFile(apk,'w') as z:z.writestr('AndroidManifest.xml','test boundary only')
  if os.environ.get('UPGRADE') and not os.environ.get('MISSING_UPGRADE_EVIDENCE'):
   proof={'update_compatible':True,'source_sha':'b'*40,'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'package':'com.appforge.studio.v9r2isolated3','version_code':535,'min_sdk':26,'target_sdk':37,'certificate_sha256':'dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082','reference_sha256':'720bfb85a8dd77645391042a247b6954ad64aa1cbd382dea647cd32232c46e4d'}
   if os.environ.get('BAD_UPGRADE_CERT'):proof['certificate_sha256']='a'*64
   if os.environ.get('BAD_UPGRADE_PACKAGE'):proof['package']='com.appforge.studio'
   if os.environ.get('BAD_UPGRADE_APK_HASH'):proof['apk_sha256']='a'*64
   (dest/'upgrade-evidence.json').write_text(json.dumps(proof))
   (dest/'unit-tests.json').write_text(json.dumps({'tests':722,'failures':0,'errors':0,'skipped':0}))
'''

class TermuxGitHubApkTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='termux-gh-apk-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bin = self.root / 'bin'; self.bin.mkdir()
        for name in ['git', 'gh']:
            p = self.bin / name; p.write_text(MOCK); p.chmod(0o755)
        self.bundle = self.root / 'source-change.bundle'
        self.bundle.write_bytes(b'fixture git bundle boundary')
        self.checksum = Path(str(self.bundle)+'.sha256')
        self.checksum.write_text(hashlib.sha256(self.bundle.read_bytes()).hexdigest()+'  source-change.bundle\n')
        self.trace = self.root / 'trace.jsonl'
        self.work = self.root / 'work';self.work.mkdir()
        self.keep = self.work / 'existing-project.txt';self.keep.write_text('keep user data')

    def run_driver(self, mode='publish', **changes):
        env = dict(os.environ, PATH=str(self.bin)+os.pathsep+os.environ['PATH'], APPFORGE_TRACE=str(self.trace), APPFORGE_WORK_ROOT=str(self.work), APPFORGE_POLL_DELAY='0')
        env.update(changes)
        arg = str(self.bundle) if mode in ('publish','upgrade') else '999'
        return subprocess.run(['bash',str(SCRIPT),mode,arg],env=env,text=True,capture_output=True,timeout=15)

    def calls(self):
        return [json.loads(x) for x in self.trace.read_text().splitlines()] if self.trace.exists() else []

    def test_publish_pushes_only_development_branch_and_creates_draft(self):
        r=self.run_driver();self.assertEqual(r.returncode,0,r.stderr)
        calls=self.calls()
        push=next(c for c in calls if 'push' in c);self.assertEqual(push[-1],'HEAD:refs/heads/codex/termux-apk-'+HEAD[:12])
        pr=next(c for c in calls if c[:3]==['gh','pr','create']);self.assertIn('--draft',pr)
        self.assertEqual(pr[pr.index('--base')+1],'feat/v9-global-package-distribution-20261027')
        dispatch=next(c for c in calls if c[:3]==['gh','workflow','run']);self.assertIn('publish_latest_release=false',dispatch)
        self.assertFalse(any(c[:3] in [['gh','pr','merge'],['gh','release','create']] for c in calls))
        self.assertEqual(self.keep.read_text(),'keep user data')
        self.assertEqual(len(list(self.work.glob('session-*/artifacts/*.apk'))),1)
        self.assertEqual(len(list(self.work.glob('session-*/artifacts/*.sha256'))),1)

    def test_git_directory_override_cannot_modify_an_existing_repository(self):
        r=self.run_driver(GIT_DIR=str(self.root/'foreign.git'));self.assertNotEqual(r.returncode,0)
        self.assertEqual(self.calls(),[])

    def test_account_without_push_permission_stops_before_clone(self):
        r=self.run_driver(NO_PUSH='1');self.assertNotEqual(r.returncode,0)
        self.assertFalse(any('clone' in c for c in self.calls()))

    def test_corrupted_bundle_stops_before_mutations(self):
        self.bundle.write_bytes(b'corrupt')
        r=self.run_driver();self.assertNotEqual(r.returncode,0)
        self.assertFalse(any('clone' in c or 'push' in c for c in self.calls()))

    def test_checksum_cannot_select_another_file(self):
        self.checksum.write_text('a'*64+'  ../private-file\n')
        r=self.run_driver();self.assertNotEqual(r.returncode,0)
        self.assertFalse(any('clone' in c for c in self.calls()))

    def test_unknown_base_stops_before_push(self):
        r=self.run_driver(BAD_BASE='1');self.assertNotEqual(r.returncode,0)
        self.assertFalse(any('push' in c for c in self.calls()))

    def test_missing_workflow_safety_guard_stops_before_push(self):
        r=self.run_driver(MISSING_GUARD='1');self.assertNotEqual(r.returncode,0)
        self.assertFalse(any('push' in c for c in self.calls()))

    def test_failed_ci_never_downloads_apk(self):
        r=self.run_driver(WATCH_FAIL='1');self.assertNotEqual(r.returncode,0)
        self.assertFalse(any(c[:3]==['gh','run','download'] for c in self.calls()))

    def test_wrong_commit_never_downloads_apk(self):
        r=self.run_driver(RUN_HEAD='c'*40);self.assertNotEqual(r.returncode,0)
        self.assertFalse(any(c[:3]==['gh','run','download'] for c in self.calls()))

    def test_download_rejects_failed_or_foreign_or_main_run(self):
        for settings in [dict(CONCLUSION='failure'),dict(WORKFLOW='Production Release'),dict(BRANCH='main')]:
            with self.subTest(settings=settings):
                r=self.run_driver('download-standard',**settings);self.assertNotEqual(r.returncode,0)
        self.assertFalse(any(c[:3]==['gh','run','download'] for c in self.calls()))

    def test_read_only_account_can_download_an_existing_successful_debug_run(self):
        r=self.run_driver('download-standard',NO_PUSH='1');self.assertEqual(r.returncode,0,r.stderr)
        self.assertTrue(any(c[:3]==['gh','run','download'] for c in self.calls()))
        self.assertFalse(any('push' in c for c in self.calls()))

    def test_download_uses_new_directories_without_repository_mutations(self):
        for _ in range(2):self.assertEqual(self.run_driver('download-standard').returncode,0)
        self.assertEqual(len(list(self.work.glob('session-*/artifacts/*.apk'))),2)
        self.assertFalse(any(c[:2]==['git','push'] or c[:3]==['gh','workflow','run'] for c in self.calls()))
        self.assertEqual(self.keep.read_text(),'keep user data')

    def test_upgrade_push_triggers_only_the_original_identity_workflow(self):
        r=self.run_driver('upgrade',UPGRADE='1');self.assertEqual(r.returncode,0,r.stderr)
        calls=self.calls();push=next(c for c in calls if 'push' in c)
        self.assertEqual(push[-1],'HEAD:refs/heads/codex/v9r2-inplace-upgrade-'+HEAD[:12])
        self.assertFalse(any(c[:3]==['gh','workflow','run'] for c in calls))
        listing=next(c for c in calls if c[:3]==['gh','run','list'])
        self.assertEqual(listing[listing.index('--event')+1],'push')
        self.assertEqual(len(list(self.work.glob('session-*/artifacts/*isolated3-upgrade.apk'))),1)
        self.assertIn('Güncelleme kimliği doğrulandı',r.stdout)

    def test_upgrade_rejects_standard_workflow_even_when_successful(self):
        r=self.run_driver('upgrade',UPGRADE='1',WORKFLOW='AppForge Android Debug')
        self.assertNotEqual(r.returncode,0)
        self.assertFalse(any(c[:3]==['gh','run','download'] for c in self.calls()))

    def test_upgrade_rejects_missing_or_wrong_identity_evidence(self):
        for flag in ['MISSING_UPGRADE_EVIDENCE','BAD_UPGRADE_CERT','BAD_UPGRADE_PACKAGE','BAD_UPGRADE_APK_HASH']:
            with self.subTest(flag=flag):
                r=self.run_driver('upgrade',UPGRADE='1',**{flag:'1'});self.assertNotEqual(r.returncode,0)
                self.assertNotIn('Güncelleme kimliği doğrulandı',r.stdout)

    def test_upgrade_shared_copy_has_a_unique_name_and_preserves_old_files(self):
        shared=self.root/'downloads';shared.mkdir();old=shared/'AppForgeStudio-guncel.apk';old.write_bytes(b'old user APK')
        for _ in range(2):
            r=self.run_driver('upgrade',UPGRADE='1',APPFORGE_DOWNLOAD_ROOT=str(shared));self.assertEqual(r.returncode,0,r.stderr)
        self.assertEqual(old.read_bytes(),b'old user APK')
        self.assertEqual(len(list(shared.glob('AppForge-v9r2isolated3-v535-*.apk'))),2)

    def test_default_download_rejects_the_previously_wrong_standard_apk(self):
        r=self.run_driver('download');self.assertNotEqual(r.returncode,0)
        self.assertFalse(any(c[:3]==['gh','run','download'] for c in self.calls()))

    def test_default_download_can_resume_a_verified_upgrade_without_push_access(self):
        r=self.run_driver('download',UPGRADE='1',NO_PUSH='1');self.assertEqual(r.returncode,0,r.stderr)
        self.assertTrue(any(c[:3]==['gh','run','download'] for c in self.calls()))

if __name__=='__main__':unittest.main()
