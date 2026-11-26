import test from 'node:test';
import assert from 'node:assert/strict';
import {runR3Scheduled, R3_LIVE_MARKER} from './scheduled.mjs';
const event = {cron: '* * * * *'};
function setup(stub, overrides={}) {
  const log=[];
  const env={STAGE5D_TEST_MODE:'SYNTHETIC_ONLY', STAGE5D_R3_DIAGNOSTIC:'ENABLED',
    V9_STAGE5D_R3_PROBE:{idFromName(n){assert.match(n,/^stage5d-r3-disposable/);return n;},get(){return stub;}},...overrides};
  return {env,log,logger:s=>log.push(s)};
}
test('fail-closed on any unexpected cron or mode', async()=>{
  let called=false;
  const o=setup({getPhase(){called=true;return 'empty';}});
  await assert.rejects(runR3Scheduled({cron:'0 * * * *'},o.env,o.logger),/R3_SCHEDULED_BLOCKED_AT_mode-gate/);
  await assert.rejects(runR3Scheduled(event,{...o.env,STAGE5D_TEST_MODE:'PRODUCTION'},o.logger),/R3_SCHEDULED_BLOCKED_AT_mode-gate/);
  await assert.rejects(runR3Scheduled(event,{...o.env,STAGE5D_R3_DIAGNOSTIC:'DISABLED'},o.logger),/R3_SCHEDULED_BLOCKED_AT_mode-gate/);
  assert.equal(called,false);
});
test('first invocation creates synthetic fixture and only requests restart',async()=>{
  let n=0;
  const o=setup({getPhase:async()=> 'empty',exercise:async()=>{n++;},abortInstance:async()=>{throw Error('expected termination');}});
  assert.deepEqual(await runR3Scheduled(event,o.env,o.logger),{phase:'restart-requested',live:false});
  assert.equal(n,1);
  assert.ok(o.log.some(s=>s.startsWith('V9_STAGE5D_R3_INITIAL=PASS')));
  assert.ok(o.log.some(s=>s.includes('CONFIRMATION=PENDING_SECOND_INVOCATION')));
  assert.ok(!o.log.includes(R3_LIVE_MARKER));
});
test('restart abort returning normally fails closed',async()=>{
  const o=setup({getPhase:async()=> 'empty',exercise:async()=>{},abortInstance:async()=>{}});
  await assert.rejects(runR3Scheduled(event,o.env,o.logger),/R3_SCHEDULED_BLOCKED_AT_request-restart/);
  assert.ok(!o.log.includes(R3_LIVE_MARKER));
});
test('second invocation verifies persisted revoked state before acceptance',async()=>{
  let inspect=0;
  const o=setup({getPhase:async()=> 'revoked',inspectRestart:async()=>{inspect++;return {pass:true}}});
  assert.deepEqual(await runR3Scheduled(event,o.env,o.logger),{phase:'verified',live:true});
  assert.equal(inspect,1);assert.ok(o.log.includes(R3_LIVE_MARKER));
});
test('any persistence error is reported by phase, never leaks details',async()=>{
  const o=setup({getPhase:async()=> 'revoked',inspectRestart:async()=>{throw Error('PRIVATE_TOKEN_DO_NOT_PRINT');}});
  await assert.rejects(runR3Scheduled(event,o.env,o.logger),error=>!String(error).includes('PRIVATE_TOKEN_DO_NOT_PRINT'));
  assert.ok(o.log.some(s=>s==='R3_SCHEDULED_FAILURE=YES;STAGE=inspect-restart;ERROR_CLASS=Error'));
  assert.ok(o.log.every(s=>!s.includes('PRIVATE_TOKEN_DO_NOT_PRINT')));
});
test('unexpected storage phase cannot pass',async()=>{
  const o=setup({getPhase:async()=> 'half-approved'});
  await assert.rejects(runR3Scheduled(event,o.env,o.logger),/R3_SCHEDULED_BLOCKED_AT_read-state/);
  assert.ok(!o.log.includes(R3_LIVE_MARKER));
});
