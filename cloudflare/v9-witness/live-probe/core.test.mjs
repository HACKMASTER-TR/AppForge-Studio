import test from 'node:test';
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {LiveProbeCore,makeFixture,PROBE_DEVICE_A} from './core.mjs';
if(!globalThis.crypto)globalThis.crypto=webcrypto;
if(!globalThis.atob)globalThis.atob=s=>Buffer.from(s,'base64').toString('binary');
if(!globalThis.btoa)globalThis.btoa=s=>Buffer.from(s,'binary').toString('base64');
function mem(){const data=new Map();return {kv:{get:k=>structuredClone(data.get(k)),put:(k,v)=>data.set(k,structuredClone(v))},transactionSync(fn){const backup=structuredClone(data);try{return fn();}catch(e){data.clear();for(const [k,v] of backup)data.set(k,v);throw e;}}};}
test('synthetic signed probe CAS, persistent reopen, and replay denied',async()=>{
 const store=mem(),fixture=await makeFixture(),first=new LiveProbeCore(store,'boot-A');
 assert.deepEqual(await first.raceCommit({device:PROBE_DEVICE_A,...fixture}),{generation:2,boot:'boot-A',oneWinner:true});
 const restarted=new LiveProbeCore(store,'boot-B');
 assert.deepEqual(restarted.inspect(),{generation:2,boot:'boot-B',initialBoot:'boot-A'});
 assert.deepEqual(await restarted.verifyReplayBlocked(),{replayRejected:true});
});
