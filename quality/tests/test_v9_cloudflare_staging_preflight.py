"""Regression against accidental V9 staging publication or CI deploy."""
import copy
from pathlib import Path
import sys
import tempfile
import unittest
import shutil

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'quality/tools'))
import appforge_v9_cloudflare_staging_preflight as pre

class CloudflareStagingSafetyTests(unittest.TestCase):
    def setUp(self):
        tmp=tempfile.TemporaryDirectory(prefix='v9-stage-')
        self.addCleanup(tmp.cleanup)
        self.repo=Path(tmp.name)
        for name in (pre.CONFIG,pre.WORKER,pre.HTTP,pre.WORKFLOW):
            target=self.repo/name
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(ROOT/name,target)
        pre.audit(self.repo)

    def edit(self,path,a,b):
        p=self.repo/path
        data=p.read_text()
        self.assertIn(a,data)
        p.write_text(data.replace(a,b,1))
        with self.assertRaises(ValueError): pre.audit(self.repo)

    def test_01_good_locked_configuration(self): self.assertTrue(pre.audit(self.repo))
    def test_02_public_workers_dev_denied(self):self.edit(pre.CONFIG,'workers_dev = false','workers_dev = true')
    def test_03_public_preview_denied(self):self.edit(pre.CONFIG,'preview_urls = false','preview_urls = true')
    def test_04_runtime_enabled_denied(self):self.edit(pre.CONFIG,'V9_STAGING_WITNESS_MODE = "DISABLED"','V9_STAGING_WITNESS_MODE = "OFFLINE_TEST_ONLY"')
    def test_05_binding_change_denied(self):self.edit(pre.CONFIG,'V9_STAGING_WITNESS','PACKAGE_WRITES')
    def test_06_backend_change_denied(self):self.edit(pre.CONFIG,'new_sqlite_classes','new_classes')
    def test_07_public_route_denied(self):
        with (self.repo/pre.CONFIG).open('a') as f:f.write('\nroute = "appforgecloud.com/*"\n')
        with self.assertRaises(ValueError):pre.audit(self.repo)
    def test_08_r2_binding_denied(self):
        with (self.repo/pre.CONFIG).open('a') as f:f.write('\n[[r2_buckets]]\nbinding = "STORAGE"\nbucket_name="actual"\n')
        with self.assertRaises(ValueError):pre.audit(self.repo)
    def test_09_worker_name_change_denied(self):self.edit(pre.CONFIG,'appforge-v9-witness-isolated-staging','appforge-v9-prod')
    def test_10_worker_mode_bypass_denied(self):self.edit(pre.WORKER,"env.V9_STAGING_WITNESS_MODE==='OFFLINE_TEST_ONLY'","env.V9_STAGING_WITNESS_MODE!=='DISABLED'")
    def test_11_http_guard_change_denied(self):self.edit(pre.HTTP,'status:503','status:200')
    def test_12_workflow_deploy_denied(self):self.edit(pre.WORKFLOW,'wrangler@4.153.0 deploy --dry-run','wrangler@4.153.0 deploy')
    def test_13_workflow_cloud_token_denied(self):
        with (self.repo/pre.WORKFLOW).open('a') as f:f.write('\n# CLOUDFLARE_API_TOKEN\n')
        with self.assertRaises(ValueError):pre.audit(self.repo)
    def test_14_workflow_branch_changed_denied(self):self.edit(pre.WORKFLOW,'feat/v9-global-package-distribution-20261027','main')
    def test_15_workflow_dispatch_denied(self):
        with (self.repo/pre.WORKFLOW).open('a') as f:f.write('\n  workflow_dispatch:\n')
        with self.assertRaises(ValueError):pre.audit(self.repo)
    def test_16_workflow_permission_denied(self):self.edit(pre.WORKFLOW,'contents: read','contents: write')
    def test_17_symlink_config_denied(self):
        p=self.repo/pre.CONFIG
        q=p.with_suffix('.bak');p.rename(q);p.symlink_to(q)
        with self.assertRaises(ValueError):pre.audit(self.repo)
    def test_18_config_entrypoint_denied(self):self.edit(pre.CONFIG,'main = "worker.mjs"','main = "http.mjs"')
    def test_19_workflow_public_pr_denied(self):
        with (self.repo/pre.WORKFLOW).open('a') as f:f.write('\n  pull_request:\n')
        with self.assertRaises(ValueError):pre.audit(self.repo)
    def test_20_no_unsafe_preview_v1(self):
        self.assertNotIn('preview_urls = true',(self.repo/pre.CONFIG).read_text())
        self.assertNotIn('routes =',(self.repo/pre.CONFIG).read_text())
        self.assertNotIn('R2',(self.repo/pre.CONFIG).read_text())

if __name__=='__main__':unittest.main()
