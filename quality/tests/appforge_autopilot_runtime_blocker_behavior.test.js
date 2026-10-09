import test from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";

// Execute the real orchestration and registry checker; isolate every external
// operation and report write. No Git/GitHub/device delivery can run here.
const harness = String.raw`
import contextlib, io, json, pathlib, runpy, sys, tempfile, types
m = runpy.run_path('scripts/appforge', run_name='sb12_test')
g = m['autopilot'].__globals__
scenario = sys.argv[1]
trace = []
with tempfile.TemporaryDirectory() as directory:
    registry = pathlib.Path(directory) / 'runtime-blockers.json'
    g['RUNTIME_BLOCKERS_PATH'] = registry
    def block():
        registry.write_text(json.dumps({'active': [{'id': 'SB12-TEST', 'status': 'OPEN', 'summary': 'test blocker'}]}))
    def event(name, result=None):
        trace.append(name)
        if scenario == name: block()
        return result
    if scenario == 'initial': block()
    if scenario == 'unreadable': registry.write_text('{')
    if scenario == 'invalid-list': registry.write_text('{"active":{}}')
    if scenario == 'invalid-root': registry.write_text('[]')
    if scenario == 'resolved':
        registry.write_text(json.dumps({'active': [{'id': 'fixed', 'status': 'RESOLVED'}]}))
    if scenario == 'checker-error':
        def broken_check(): raise OSError('test checker failure')
        g['active_runtime_blockers'] = broken_check
    g['Lock'] = contextlib.nullcontext
    g['state_write'] = lambda *a, **k: None
    g['generate_report'] = lambda *a, **k: ('test.json', 'test.md')
    g['git_out'] = lambda *a: 'fix/test' if a[0] == 'branch' else 'test-main-sha'
    g['delivery_preflight_result'] = lambda: event('preflight', {'versioned_release': 'ELIGIBLE', 'play_publish': 'ELIGIBLE'})
    for name in ['prepare_release_version', 'local_gate', 'safe_stage', 'safe_cached_diff_check', 'require_remote_sync']:
        g[name] = lambda *a, name=name: event(name)
    g['changed_files'] = lambda: [] if scenario == 'resume' else ['scripts/appforge']
    g['default_commit_message'] = lambda: 'test only'
    g['branch_change_files'] = lambda: ['android-app/test']
    g['classify'] = lambda *a: ['test']
    g['ensure_pr'] = lambda: event('pr', 1)
    g['ci_watch'] = lambda: event('ci')
    g['merge_pr'] = lambda *a: event('merge')
    g['wait_main_validation_only'] = lambda *a: event('main-ci', (['android-debug.yml'], {}))
    g['release_info'] = lambda: {'status': 'READY'}
    g['run_github_cleanup'] = lambda *a: event('cleanup', {'status': 'SUCCESS'})
    g['wait_production_deploy'] = lambda *a: event('deploy', {})
    g['ensure_versioned_release'] = lambda *a: event('release', {})
    g['wait_play_publish'] = lambda *a: event('publish', {})
    g['run'] = lambda cmd, **k: event(cmd[1] if cmd[0] == 'git' else 'stability', types.SimpleNamespace(returncode=0))
    output = io.StringIO()
    code = 0
    with contextlib.redirect_stdout(output):
        try: g['autopilot']()
        except SystemExit as exc: code = exc.code or 1
        except Exception: code = 1
    print(json.dumps({'trace': trace, 'code': code, 'output': output.getvalue()}))
`;
function execute(scenario) {
  const result = spawnSync("python3", ["-c", harness, scenario], { cwd: new URL("../../", import.meta.url), encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr);
  return JSON.parse(result.stdout);
}
const stages = ["commit", "push", "pr", "merge", "cleanup", "deploy", "release", "publish"];
for (const scenario of ["initial", "unreadable", "invalid-list", "invalid-root", "checker-error"]) {
  test(`SB12 ${scenario} registry stops before delivery mutations`, () => {
    const result = execute(scenario);
    assert.notEqual(result.code, 0);
    for (const stage of stages) assert.ok(!result.trace.includes(stage), stage);
    if (scenario !== "checker-error") assert.match(result.output, /runtime blocker|SB12-TEST/);
  });
}
for (const [trigger, forbidden] of [
  ["local_gate", stages], ["stability", stages],
  ["commit", stages.slice(1)], ["push", stages.slice(2)],
  ["ci", stages.slice(3)], ["main-ci", stages.slice(4)],
  ["cleanup", stages.slice(5)], ["deploy", stages.slice(6)],
  ["release", stages.slice(7)],
]) {
  test(`SB12 blocker appearing during ${trigger} stops subsequent delivery`, () => {
    const result = execute(trigger);
    assert.equal(result.code, 42);
    assert.ok(result.trace.includes(trigger));
    for (const stage of forbidden) assert.ok(!result.trace.includes(stage), stage);
    assert.match(result.output, /SB12-TEST/);
  });
}
for (const scenario of ["clear", "resolved", "resume"]) {
  test(`SB12 ${scenario} preserves valid delivery sequence`, () => {
    const result = execute(scenario);
    assert.equal(result.code, 0);
    assert.deepEqual(result.trace.filter(x => stages.includes(x)), scenario === "resume" ? stages.slice(2) : stages);
  });
}

for (const trigger of ["metadata", "merge", "clear"]) {
  test(`SB12 real merge helper rechecks after ${trigger}`, () => {
    const script = String.raw`
import contextlib, io, json, pathlib, runpy, sys, tempfile, types
m = runpy.run_path('scripts/appforge', run_name='sb12_merge_test')
g = m['merge_pr'].__globals__
trace = []
with tempfile.TemporaryDirectory() as directory:
    registry = pathlib.Path(directory) / 'registry.json'
    g['RUNTIME_BLOCKERS_PATH'] = registry
    g['github_repo'] = lambda: 'test/test'
    g['state_write'] = lambda *a, **k: None
    g['generate_report'] = lambda *a, **k: ('test.json', 'test.md')
    def run(cmd, **kwargs):
        name = 'delete' if cmd[0] == 'git' else 'merge' if 'PUT' in cmd else 'metadata'
        trace.append(name)
        if name == sys.argv[1]: registry.write_text('{"active":[{"id":"SB12-TEST"}]}')
        data = {'head': {'sha': 'test-sha', 'ref': 'fix/test'}} if name == 'metadata' else {'merged': True}
        return types.SimpleNamespace(returncode=0, stdout=json.dumps(data), stderr='')
    g['run'] = run
    code = 0
    with contextlib.redirect_stdout(io.StringIO()):
        try: g['merge_pr'](1)
        except SystemExit as exc: code = exc.code
    print(json.dumps({'code': code, 'trace': trace}))
`;
    const result = spawnSync("python3", ["-c", script, trigger], { cwd: new URL("../../", import.meta.url), encoding: "utf8" });
    assert.equal(result.status, 0, result.stderr);
    const evidence = JSON.parse(result.stdout);
    assert.equal(evidence.code, trigger === "clear" ? 0 : 42);
    assert.deepEqual(evidence.trace, trigger === "metadata" ? ["metadata"] : trigger === "merge" ? ["metadata", "merge"] : ["metadata", "merge", "delete"]);
  });
}
