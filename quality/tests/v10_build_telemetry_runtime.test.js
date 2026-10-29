import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root = fileURLToPath(new URL('../../', import.meta.url));
const init = path.join(root, 'android-app/app/src/main/assets/device-build/build-metrics.init.gradle');

test('real Gradle fixture records first, no-op, changed-input and failed task outcomes', {timeout: 240000}, () => {
  assert.ok(fs.existsSync(init), 'production Gradle telemetry init script must exist');
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'appforge-v10-gradle-'));
  try {
    fs.writeFileSync(path.join(temp, 'settings.gradle'), "rootProject.name = 'v10-fixture'\n");
    fs.writeFileSync(path.join(temp, 'build.gradle'), `
      tasks.register('compileFixture', Copy) { from('input.txt'); into('out') }
      tasks.register('assembleFixture') { dependsOn('compileFixture') }
      tasks.register('cleanFixture', Delete) { delete('out') }
      tasks.register('failFixture') { doLast { throw new GradleException('synthetic failure') } }
    `);
    fs.writeFileSync(path.join(temp, 'input.txt'), 'Unicode α 🚀 v1');
    function run(task = 'assembleFixture', graphPath = path.join(temp, 'graph.json')) {
      const args = ['-p', temp, '--offline', '--no-daemon', '--no-configuration-cache', '--console=plain', '--init-script', init,
        '-Dappforge.metrics.path=' + graphPath, task];
      let output;
      try { output = execFileSync('gradle', args, {encoding:'utf8', timeout:60000, stdio:'pipe'}); }
      catch (e) { if (task !== 'failFixture') throw e; output = e.stdout.toString(); }
      const line = output.split('\n').find(l => l.startsWith('APPFORGE_GRADLE_METRICS_V1 '));
      assert.ok(line, 'actual Gradle execution must emit metrics');
      const values = Object.fromEntries(line.split(' ').slice(1).map(x => x.split('=')));
      return {values, output, graph: graphPath === path.join(temp, 'graph.json') ? JSON.parse(fs.readFileSync(graphPath, 'utf8')) : null};
    }
    const first = run();
    assert.equal(first.values.executed, '1');
    assert.equal(first.values.uptodate, '0');
    const noop = run();
    assert.equal(noop.values.executed, '0');
    assert.equal(noop.values.uptodate, '1');
    assert.equal(noop.values.cache, '0');
    fs.writeFileSync(path.join(temp, 'input.txt'), 'Unicode α 🚀 v2');
    const changed = run();
    assert.equal(changed.values.executed, '1');
    assert.equal(changed.values.uptodate, '0');
    assert.equal(fs.readFileSync(path.join(temp, 'out/input.txt'), 'utf8'), 'Unicode α 🚀 v2');
    assert.equal(changed.graph.nodes.length, 2);
    assert.equal(changed.graph.edges.length, 1);
    assert.deepEqual(first.graph, changed.graph);
    assert.equal(changed.values.invalidation, 'UNKNOWN');
    assert.equal(run('failFixture').values.failed, '1');
    // Removing observation and a failed graph output must preserve build output.
    execFileSync('gradle', ['-p', temp, '--offline', '--no-daemon', '--no-configuration-cache', 'cleanFixture'],
      {timeout:60000, stdio:'pipe'});
    const baselineStart = performance.now();
    execFileSync('gradle', ['-p', temp, '--offline', '--no-daemon', '--no-configuration-cache', 'assembleFixture'],
      {timeout:60000, stdio:'pipe'});
    const baselineMs = performance.now() - baselineStart;
    const baseline = fs.readFileSync(path.join(temp, 'out/input.txt'));
    fs.rmSync(path.join(temp, 'out'), {recursive:true});
    const observedStart = performance.now();
    const unavailable = run('assembleFixture', temp);
    const observedMs = performance.now() - observedStart;
    assert.match(unavailable.output, /status=UNAVAILABLE reason=OUTPUT_IO/);
    assert.equal(unavailable.values.executed, '1');
    assert.deepEqual(fs.readFileSync(path.join(temp, 'out/input.txt')), baseline);
    console.log(JSON.stringify({benchmark:'original-copy-fixture', baseline_ms:Math.round(baselineMs),
      observed_ms:Math.round(observedMs), output_equal:true, speed_claim:false}));
    console.log('V10 fixture outcomes: first=executed1, noop=uptodate1, changed=executed1; no speed claim');
  } finally { fs.rmSync(temp, {recursive:true, force:true}); }
});
