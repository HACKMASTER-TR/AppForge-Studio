import test from 'node:test';
import assert from 'node:assert/strict';
import {V9Stage5DProbeCore} from './core.mjs';
import {V9PrivateDeviceAdminService} from '../device-auth/stage5d-private.mjs';
const storage=()=>{const data=new Map();return {kv:{get:k=>structuredClone(data.get(k)),put:(k,v)=>data.set(k,structuredClone(v))},transactionSync:f=>f()};};
test('synthetic OIDC admin -> enrolled P-256 device -> approved -> challenge -> revoked -> physical-like restart check',async()=>{
  const s=storage(),first=new V9Stage5DProbeCore(s,'boot-1');
  const state=await first.exercise();assert.equal(state.phase,'revoked');
  const same=new V9Stage5DProbeCore(s,'boot-1');await assert.rejects(()=>same.inspectRestart(),/restart not observed/);
  const restarted=new V9Stage5DProbeCore(s,'boot-2');
  assert.deepEqual((await restarted.inspectRestart()).pass,true);
  await assert.rejects(()=>first.exercise(),/probe already initialized/);
});
test('no callable public routes or self-authority on private service',()=>{
  for(const method of ['fetch','mintPermit','approve','register','setAdminId','setTrustedJwks','getSigner','getStorage'])
    assert.equal(typeof V9PrivateDeviceAdminService.prototype[method],'undefined');
});
