#!/usr/bin/env python3
"""Static, fail-closed audit of non-public V9 CF staging bundle. Does not deploy."""
import argparse
from pathlib import Path
import tomllib
import re

CONFIG = 'cloudflare/v9-witness/wrangler.staging.toml'
WORKFLOW = '.github/workflows/v9-cloudflare-staging-preflight.yml'
WORKER = 'cloudflare/v9-witness/worker.mjs'
HTTP = 'cloudflare/v9-witness/http.mjs'

def require(cond, message):
    if not cond: raise ValueError('BLOCKED: '+message)

def audit(repo):
    repo=Path(repo)
    p=repo/CONFIG
    require(p.is_file() and not p.is_symlink(), 'missing staging config')
    doc=tomllib.loads(p.read_text(encoding='utf-8'))
    require(set(doc)=={'name','main','compatibility_date','workers_dev','preview_urls','vars','durable_objects','migrations'}, 'unexpected staging configuration keys')
    require(doc['name']=='appforge-v9-witness-isolated-staging', 'staging worker name changed')
    require(doc['main']=='worker.mjs' and doc['compatibility_date']=='2026-10-27', 'staging entrypoint or compatibility changed')
    require(doc['workers_dev'] is False and doc['preview_urls'] is False, 'public URLs enabled')
    require(doc['vars']=={'V9_STAGING_WITNESS_MODE':'DISABLED'}, 'dangerous staging vars')
    require(doc['durable_objects']=={'bindings':[{'name':'V9_STAGING_WITNESS','class_name':'V9StagingWitness'}]}, 'DO binding changed')
    require(doc['migrations']==[{'tag':'v1','new_sqlite_classes':['V9StagingWitness']}], 'SQLite migration changed')
    for path in (WORKER,HTTP,WORKFLOW):
        require((repo/path).is_file() and not (repo/path).is_symlink(), 'missing or symlinked '+path)
    worker=(repo/WORKER).read_text(encoding='utf-8')
    http=(repo/HTTP).read_text(encoding='utf-8')
    flow=(repo/WORKFLOW).read_text(encoding='utf-8')
    require("env.V9_STAGING_WITNESS_MODE==='OFFLINE_TEST_ONLY'" in worker, 'runtime disabled switch missing')
    require('this.enabled?new WitnessLedger' in worker, 'runtime ledger guard missing')
    require('status:503' in http and 'handleHttp' in http, 'HTTP disable guard missing')
    require('workflow_dispatch:' not in flow, 'manual run not authorized from feature branch')
    require('pull_request:' not in flow, 'unreviewed PR auto build forbidden')
    require('branches:' in flow and 'feat/v9-global-package-distribution-20261027' in flow, 'dev branch pin missing')
    require('permissions:' in flow and 'contents: read' in flow, 'read-only github token missing')
    require('wrangler@4.153.0 deploy --dry-run' in flow, 'pinned dry-run compile missing')
    require('--config cloudflare/v9-witness/wrangler.staging.toml' in flow, 'safe staging config not used')
    require('CLOUDFLARE_API_TOKEN' not in flow and 'CLOUDFLARE_ACCOUNT_ID' not in flow, 'cloud credentials used in preflight')
    # No non-dry-run Wrangler call anywhere in the CI workflow.
    for line in flow.splitlines():
        if re.search(r'\bwrangler(?:@\S+)?\s+deploy\b', line):
            require('--dry-run' in line, 'real deploy in CI')
    return True

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--repo',type=Path,default=Path.cwd())
    args=p.parse_args()
    try:
        audit(args.repo)
    except (OSError,ValueError,tomllib.TOMLDecodeError) as e:
        print('V9_STAGING_PREFLIGHT=BLOCKED')
        print('REASON='+str(e)[:200]);return 1
    print('V9_STAGING_PREFLIGHT=PASS')
    print('GITHUB_DRY_RUN=REQUIRES_REMOTE_CI')
    print('CLOUDFLARE_DEPLOYED=NO')
    print('PUBLIC_HTTP=DISABLED')
    print('ANDROID_CLIENT_ENABLED=NO')
    print('V9_PRODUCTION_READINESS=BLOCKED')
    return 0

if __name__=='__main__':raise SystemExit(main())
