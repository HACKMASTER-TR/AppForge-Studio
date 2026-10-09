#!/usr/bin/env python3
"""Offline claim validation and deterministic recovery; no delivery authority."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'docs/wiki/01_Project/current_state.json'
HANDOFF = ROOT / 'docs/wiki/01_Project/Current_State_Handoff.md'
STAGES = ('implementation', 'targetedTests', 'fullQuality', 'secondBrain',
          'hostedCI', 'physicalAcceptance', 'merge', 'release', 'deploy')
STATES = {'UNKNOWN', 'NOT_RUN', 'PASS', 'FAIL', 'NOT_APPLICABLE'}
AUTHORITY = 'REPOSITORY_CLAIMS_SUBORDINATE_TO_GIT_AND_EVIDENCE'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def shape(value, required, optional=()):
    require(isinstance(value, dict), 'Expected object')
    require(set(required) <= value.keys() <= set(required) | set(optional),
            'Missing or unknown fields')


def string(value):
    require(isinstance(value, str) and bool(value.strip()) and len(value) <= 2000,
            'Expected nonempty bounded string')
    require(not re.search(r'[\x00-\x1f\x7f]', value), 'Control characters prohibited')
    require(not re.search(r'(?i)(?:gh[pousr]_|github_pat_|sk-|AKIA)[A-Za-z0-9_-]{12,}|-----BEGIN .*PRIVATE KEY|(?:password|token|cookie|api[_-]?key|device[_-]?code)\s*[:=]|/home/|/root/|/data/data/|[A-Z]:\\', value),
            'Credential or local path material prohibited')
    if '://' in value:
        require(re.fullmatch(r'https://[A-Za-z0-9.-]+(?:/[A-Za-z0-9._/#-]*)?', value),
                'Only credential-free HTTPS references allowed')


def sha(value):
    require(isinstance(value, str) and re.fullmatch(r'[0-9a-f]{40}', value), 'Invalid exact SHA')


def branch(value):
    string(value)
    require(re.fullmatch(r'[A-Za-z0-9_-]+(?:[./][A-Za-z0-9_-]+)*', value)
            and not value.endswith('.lock'), 'Invalid branch')


def load(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'Duplicate JSON key')
            result[key] = value
        return result
    data = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique)
    shape(data, ('schemaVersion', 'repository', 'authority', 'workstreams'))
    require(type(data['schemaVersion']) is int and data['schemaVersion'] == 1, 'Unsupported schema')
    require(data['repository'] == 'HACKMASTER-TR/AppForge-Studio', 'Unexpected repository')
    require(data['authority'] == AUTHORITY, 'Invalid authority')
    require(isinstance(data['workstreams'], list) and data['workstreams'], 'Workstreams required')
    ids = {}
    for w in data['workstreams']:
        shape(w, ('id', 'title', 'scope', 'lifecycle', 'branch', 'baseBranch', 'baseSha',
                  'sourceSha', 'stages', 'blockers', 'nextAction'), ('parent',))
        for key in ('id', 'title', 'scope', 'nextAction'):
            string(w[key])
        require(re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*', w['id']), 'Invalid stable ID')
        require(w['id'] not in ids, 'Duplicate workstream ID')
        ids[w['id']] = w
        require(w['lifecycle'] in {'PLANNED', 'ACTIVE', 'BLOCKED', 'COMPLETE', 'SUPERSEDED'}, 'Invalid lifecycle')
        for key in ('branch', 'baseBranch'):
            branch(w[key])
        for key in ('baseSha', 'sourceSha'):
            sha(w[key])
        require(isinstance(w['blockers'], list), 'Blockers must be a list')
        for b in w['blockers']:
            string(b)
        require(w['lifecycle'] != 'BLOCKED' or w['blockers'], 'Blocked requires reference/reason')
        shape(w['stages'], STAGES)
        for name, stage in w['stages'].items():
            shape(stage, ('state',), ('reason', 'evidence'))
            require(isinstance(stage['state'], str) and stage['state'] in STATES, 'Invalid stage state')
            if 'reason' in stage:
                string(stage['reason'])
            if stage['state'] == 'NOT_APPLICABLE':
                require('reason' in stage, 'Not applicable requires scope/reason')
            evidence = stage.get('evidence')
            require(stage['state'] not in {'UNKNOWN', 'NOT_RUN', 'NOT_APPLICABLE'} or evidence is None,
                    'Non-evidenced state cannot carry evidence')
            require(stage['state'] != 'PASS' or evidence is not None, 'PASS requires evidence')
            if evidence is not None:
                shape(evidence, ('sourceSha', 'reference'), ('runId', 'attempt', 'workflow', 'scenario'))
                sha(evidence['sourceSha'])
                require(evidence['sourceSha'] == w['sourceSha'], 'Evidence SHA mismatch')
                string(evidence['reference'])
                reference = evidence['reference']
                if not reference.startswith('https://'):
                    require(re.fullmatch(r'[A-Za-z0-9_-]+(?:/[A-Za-z0-9_.-]+)*', reference)
                            and all(part not in {'.', '..'} for part in reference.split('/')),
                            'Evidence reference must be repository-relative or HTTPS')
                for field in ('workflow', 'scenario'):
                    if field in evidence:
                        string(evidence[field])
                for field in ('runId', 'attempt'):
                    if field in evidence:
                        require(type(evidence[field]) is int and evidence[field] > 0, 'Invalid run metadata')
                require(name == 'hostedCI' or not {'runId', 'attempt', 'workflow'} & evidence.keys(), 'Misplaced CI metadata')
                require(name == 'physicalAcceptance' or 'scenario' not in evidence, 'Misplaced scenario')
                if name == 'hostedCI' and stage['state'] == 'PASS':
                    require({'runId', 'workflow'} <= evidence.keys(), 'CI PASS requires run/workflow')
                if name == 'physicalAcceptance' and stage['state'] == 'PASS':
                    require('scenario' in evidence, 'Physical PASS requires scenario')
    for w in ids.values():
        if 'parent' in w:
            require(w['parent'] in ids and w['parent'] != w['id'], 'Invalid parent')
            parent = ids[w['parent']]
            require(w['baseBranch'] == parent['branch'] and w['baseSha'] == parent['sourceSha'], 'Stack base mismatch')
        seen = set()
        cursor = w
        while 'parent' in cursor:
            require(cursor['id'] not in seen, 'Stack cycle')
            seen.add(cursor['id'])
            cursor = ids[cursor['parent']]
    return data


def render(data):
    lines = ['---', 'type: context', 'status: active', 'project: AppForge Studio',
             'created:', 'updated:', 'last_verified:',
             'confidence: medium', 'source_files:',
             '  - "docs/wiki/01_Project/current_state.json"',
             '  - "scripts/current-state.py"', '---', '',
             '# Current State Handoff', '',
             'Generated by `scripts/current-state.py` from `docs/wiki/01_Project/current_state.json` (schema 1). Do not edit this derivative. Frontmatter dates are intentionally unset: this view has no independent verification record.', '',
             'The human-maintained manifest is the canonical cross-workstream claim registry, subordinate to Git and actual evidence. This handoff has no independent authority and grants no merge, release or deployment authorization.', '',
             'Check live branch and HEAD separately with `python3 scripts/current-state.py show`. Recorded source SHA binds historical evidence only; later documentation commits and dirty changes do not inherit acceptance. UNKNOWN is not PASS; NOT_RUN is not FAIL; NOT_APPLICABLE requires a scope/reason. Offline validation checks structure, not remote authenticity.', '',
             'Repository: `' + data['repository'] + '`', '',
             '## Recovery and protected boundaries', '',
             'Read AGENTS.md and the ChatGPT + Codex development standard, then route through Index.md. Preserve SB12 fail-stop, F01–F25, other Second Brain findings, Zero-Setup Build, application runtime, Cloudflare, D1, AI providers and appforge-failover. Current task authorization controls delivery; physical acceptance, merge, release and deploy remain independent stages.', '',
             'The existing Second Brain snapshot remains deterministic integrity/coverage evidence, not a state database. Run validate/check before recovery; regenerate this handoff after deliberate manifest edits. Authenticate hosted runs and physical records through an independent external gate.', '']
    for w in sorted(data['workstreams'], key=lambda item: item['id']):
        lines += ['## ' + w['id'] + ' — ' + w['title'], '',
                  'Scope: ' + w['scope'], '', 'Lifecycle: **' + w['lifecycle'] + '**', '',
                  '- Branch: `' + w['branch'] + '`',
                  '- Base branch: `' + w['baseBranch'] + '`',
                  '- Base exact SHA: `' + w['baseSha'] + '`',
                  '- Manifest-recorded source SHA: `' + w['sourceSha'] + '`',
                  '- Parent workstream: ' + w.get('parent', 'none recorded'), '',
                  '| Evidence stage | State | Binding / reference or reason |',
                  '|---|---|---|']
        for name in STAGES:
            s = w['stages'][name]
            detail = s.get('reason', '')
            if 'evidence' in s:
                e = s['evidence']
                detail = '; '.join(f'{k}: {e[k]}' for k in sorted(e))
            lines.append('| ' + name + ' | ' + s['state'] + ' | ' + detail.replace('|', '\\|') + ' |')
        lines += ['', 'Blockers / references: ' + ('; '.join(w['blockers']) or 'none recorded (not proof of blocker absence)'), '',
                  'Next action: ' + w['nextAction'], '']
    return '\n'.join(lines).rstrip('\n') + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('validate', 'generate', 'check', 'show'))
    parser.add_argument('--manifest', type=Path, default=MANIFEST)
    parser.add_argument('--handoff', type=Path, default=HANDOFF)
    args = parser.parse_args()
    try:
        data = load(args.manifest)
        content = render(data).encode('utf-8')
        if args.command == 'generate':
            temporary = None
            try:
                with tempfile.NamedTemporaryFile(dir=args.handoff.parent, delete=False) as handle:
                    temporary = handle.name
                    handle.write(content)
                os.replace(temporary, args.handoff)
            finally:
                if temporary and os.path.exists(temporary):
                    os.unlink(temporary)
        elif args.command == 'check':
            require(args.handoff.read_bytes() == content, 'Handoff stale: generate required')
        elif args.command == 'show':
            def git(*options):
                return subprocess.check_output(['git', *options], cwd=ROOT, text=True).strip()
            live_branch, head = git('branch', '--show-current'), git('rev-parse', 'HEAD')
            dirty = bool(git('status', '--porcelain'))
            matches = [w for w in data['workstreams'] if w['branch'] == live_branch]
            print(json.dumps({'liveGit': {'branch': live_branch, 'head': head, 'dirty': dirty},
                              'manifestRecorded': [dict(id=w['id'], branch=w['branch'],
                                  baseBranch=w['baseBranch'], baseSha=w['baseSha'],
                                  sourceSha=w['sourceSha'], lifecycle=w['lifecycle'],
                                  parent=w.get('parent'),
                                  stages={k: {'state': v['state']} for k, v in w['stages'].items()},
                                  blockers=w['blockers'], nextAction=w['nextAction'])
                                  for w in data['workstreams']],
                              'evidenceMismatch': not matches or any(w['sourceSha'] != head for w in matches),
                              'uncommittedChangesNotAccepted': dirty,
                              'handoffStale': not args.handoff.exists() or args.handoff.read_bytes() != content}, indent=2))
        else:
            print('VALID: structural consistency only; external authenticity not verified')
    except (ValueError, OSError, subprocess.SubprocessError, TypeError, KeyError) as error:
        # Avoid echoing untrusted manifest values or parser source excerpts.
        print('NOT_PASS: invalid manifest, stale handoff, or unavailable local evidence')
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
