import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const script = path.join(repo, 'scripts/current-state.py');
const manifest = path.join(repo, 'docs/wiki/01_Project/current_state.json');
const seed = JSON.parse(fs.readFileSync(manifest, 'utf8'));
function fixture(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'current-state-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const input = path.join(dir, 'manifest.json');
  const output = path.join(dir, 'handoff.md');
  const data = structuredClone(seed);
  function write() { fs.writeFileSync(input, JSON.stringify(data)); }
  write();
  return { data, input, output, write,
    run(command) { return spawnSync('python3', [script, command, '--manifest', input, '--handoff', output], { cwd: repo, encoding: 'utf8' }); } };
}
function reject(t, change) {
  const f = fixture(t); change(f.data); f.write();
  assert.equal(f.run('validate').status, 1);
  assert.equal(f.run('generate').status, 1);
  assert.equal(fs.existsSync(f.output), false);
}
function shown(f) { const result = f.run('show'); assert.equal(result.status, 0, result.stdout); return JSON.parse(result.stdout); }

test('valid manifest passes', t => assert.equal(fixture(t).run('validate').status, 0));
test('malformed JSON fails closed without echoing input', t => {
  const f = fixture(t); fs.writeFileSync(f.input, '{"sensitive":');
  const result = f.run('validate'); assert.equal(result.status, 1); assert.doesNotMatch(result.stdout + result.stderr, /sensitive/);
});
test('duplicate workstream IDs fail', t => reject(t, d => d.workstreams[1].id = d.workstreams[0].id));
test('invalid SHA fails', t => reject(t, d => d.workstreams[0].sourceSha = 'HEAD'));
test('UNKNOWN remains UNKNOWN with no inferred acceptance', t => {
  const f = fixture(t); const w = shown(f).manifestRecorded[0];
  assert.equal(w.stages.hostedCI.state, 'UNKNOWN'); assert.equal(w.stages.merge.state, 'NOT_RUN');
});
test('NOT_RUN preserved distinctly', t => {
  const f = fixture(t); assert.equal(shown(f).manifestRecorded[2].stages.fullQuality.state, 'NOT_RUN');
});
test('PASS requires exact-SHA evidence', t => reject(t, d => d.workstreams[2].stages.implementation = { state: 'PASS' }));
test('hosted CI PASS requires run and workflow', t => {
  for (const field of ['runId', 'workflow', 'sourceSha', 'reference']) {
    reject(t, d => delete d.workstreams[1].stages.hostedCI.evidence[field]);
  }
});
test('hosted evidence cannot transfer to another SHA', t => reject(t, d => d.workstreams[1].stages.hostedCI.evidence.sourceSha = 'a'.repeat(40)));
test('physical PASS requires exact SHA, reference and scenario', t => {
  const evidence = { sourceSha: seed.workstreams[1].sourceSha, reference: 'docs/wiki/01_Project/Current_Status.md', scenario: 'Scoped acceptance record' };
  const f = fixture(t); f.data.workstreams[1].stages.physicalAcceptance = { state: 'PASS', evidence }; f.write();
  assert.equal(f.run('validate').status, 0);
  for (const field of ['sourceSha', 'reference', 'scenario']) {
    reject(t, d => { d.workstreams[1].stages.physicalAcceptance = { state: 'PASS', evidence: { ...evidence } }; delete d.workstreams[1].stages.physicalAcceptance.evidence[field]; });
  }
  reject(t, d => d.workstreams[1].stages.physicalAcceptance = { state: 'PASS', evidence: { ...evidence, sourceSha: 'a'.repeat(40) } });
});
test('stacked relationship validates parent branch, exact base, IDs and cycles', t => {
  reject(t, d => d.workstreams[1].parent = 'missing');
  reject(t, d => d.workstreams[1].baseBranch = 'main');
  reject(t, d => d.workstreams[1].baseSha = 'a'.repeat(40));
  reject(t, d => d.workstreams[1].parent = d.workstreams[1].id);
  reject(t, d => { const a = d.workstreams[0], c = d.workstreams[2]; a.parent = c.id; a.baseBranch = c.branch; a.baseSha = c.sourceSha; });
});
test('parent merge and child CI do not imply child merge', t => {
  const f = fixture(t); const parent = f.data.workstreams[0];
  parent.stages.merge = { state: 'PASS', evidence: { sourceSha: parent.sourceSha, reference: 'https://github.com/HACKMASTER-TR/AppForge-Studio/pull/1' } }; f.write();
  assert.equal(shown(f).manifestRecorded[1].stages.merge.state, 'NOT_RUN');
});
test('merge does not imply release or deploy', t => {
  const f = fixture(t); const w = f.data.workstreams[1];
  w.stages.merge = { state: 'PASS', evidence: { sourceSha: w.sourceSha, reference: 'https://github.com/HACKMASTER-TR/AppForge-Studio/pull/66' } }; f.write();
  const stages = shown(f).manifestRecorded[1].stages;
  assert.equal(stages.release.state, 'NOT_RUN'); assert.equal(stages.deploy.state, 'NOT_RUN');
});
test('unknown and success-like values fail closed', t => {
  for (const state of ['SUCCESS', 'passed', true, 'MERGED', 'READY']) reject(t, d => d.workstreams[2].stages.merge.state = state);
});
test('deterministic render independent of workstream ordering', t => {
  const f = fixture(t); assert.equal(f.run('generate').status, 0); const first = fs.readFileSync(f.output);
  f.data.workstreams.reverse(); f.write(); assert.equal(f.run('generate').status, 0); assert.deepEqual(fs.readFileSync(f.output), first);
});
test('stale check fails read-only', t => {
  const f = fixture(t); f.run('generate'); fs.appendFileSync(f.output, 'stale\n');
  const before = fs.readFileSync(f.output); assert.equal(f.run('check').status, 1); assert.deepEqual(fs.readFileSync(f.output), before);
});
test('correct check passes read-only', t => {
  const f = fixture(t); f.run('generate'); const before = fs.readFileSync(f.output);
  assert.equal(f.run('check').status, 0); assert.deepEqual(fs.readFileSync(f.output), before);
});
test('show reports separate live Git identity, mismatch and freshness', t => {
  const f = fixture(t); f.run('generate'); const report = shown(f);
  const head = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: repo, encoding: 'utf8' }).stdout.trim();
  const branch = spawnSync('git', ['branch', '--show-current'], { cwd: repo, encoding: 'utf8' }).stdout.trim();
  assert.equal(report.liveGit.head, head); assert.equal(report.liveGit.branch, branch); assert.equal(report.handoffStale, false);
  f.data.workstreams[2].sourceSha = 'a'.repeat(40); f.write(); const changed = shown(f);
  assert.equal(changed.evidenceMismatch, true); assert.equal(changed.manifestRecorded[2].sourceSha, 'a'.repeat(40)); assert.equal(changed.handoffStale, true);
});
test('prohibited fields and credential-bearing references rejected', t => {
  reject(t, d => d.workstreams[0].token = 'placeholder');
  for (const reference of ['https://user:placeholder@example.com/record', 'https://example.com/record?token=placeholder', '/home/local/record', 'password=placeholder', '-----BEGIN PRIVATE KEY-----']) {
    reject(t, d => d.workstreams[0].stages.implementation.evidence.reference = reference);
  }
});
test('NOT_APPLICABLE distinct and requires explicit reason', t => {
  const f = fixture(t); assert.equal(shown(f).manifestRecorded[0].stages.physicalAcceptance.state, 'NOT_APPLICABLE');
  reject(t, d => delete d.workstreams[0].stages.physicalAcceptance.reason);
});
test('contradictory evidence on non-evidenced states fails', t => {
  for (const state of ['UNKNOWN', 'NOT_RUN', 'NOT_APPLICABLE']) reject(t, d => { d.workstreams[0].stages.implementation.state = state; d.workstreams[0].stages.implementation.reason = 'Scope'; });
});
test('duplicate JSON fields fail', t => {
  const f = fixture(t); fs.writeFileSync(f.input, fs.readFileSync(f.input, 'utf8').replace('"schemaVersion":1', '"schemaVersion":1,"schemaVersion":1'));
  assert.equal(f.run('validate').status, 1);
});
test('current repository manifest passes', () => assert.equal(spawnSync('python3', [script, 'validate'], { encoding: 'utf8' }).status, 0));
test('generated repository handoff matches manifest', () => assert.equal(spawnSync('python3', [script, 'check'], { encoding: 'utf8' }).status, 0));
