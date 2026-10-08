import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// STATIC_CONTRACT: protects CI wiring; backend behavior is tested by the commands.
test('global stability gate requires offline control-plane and SQLite regressions', () => {
  const workflow = readFileSync(new URL('../../.github/workflows/appforge-stability-gate.yml', import.meta.url), 'utf8');
  const job = workflow.split('  backend-regression:')[1].split('  android-regression:')[0];
  assert.match(workflow, /on:\n  pull_request:\n  push:\n    branches:\n      - main/);
  assert.match(job, /run: npm --prefix quality test/);
  assert.match(job, /node-version: "22"/);
  assert.match(job, /python-version: "3\.12"/);
  assert.match(job, /run: node --test cloudflare\/control-plane\/tests\/\*\.test\.mjs/);
  assert.match(job, /run: python3 cloudflare\/control-plane\/tests\/pro_lifecycle_sqlite\.py/);
  assert.doesNotMatch(job, /continue-on-error|if:|secrets\.|wrangler|deploy|--test-name-pattern/);
  const summary = workflow.split('  stability-summary:')[1];
  assert.match(summary, /needs:[\s\S]*?      - backend-regression/);
  assert.match(summary, /\[ "\$\{\{ needs\.backend-regression\.result \}\}" = "success" \] \|\| FAIL=/);
  assert.match(summary, /if \[ "\$FAIL" -ne 0 \]; then[\s\S]*?exit 1/);
});
