import test from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,createHash,sign as nodeSign,randomBytes} from 'node:crypto';
import {V9DeviceChallengeStore} from './challenges.mjs';
import {strictBase64Url,derToP1363,proofMessage,verifyDeviceProof} from './protocol.mjs';

const b64url = b=>Buffer.from(b).toString('base64url');
const hex32=()=>randomBytes(16).toString('hex');
const getKey=()=>{
  const pair=generateKeyPairSync('ec',{namedCurve:'prime256v1'});
  const spki=pair.publicKey.export({type:'spki',format:'der'});
  return {pair,spki,pin:createHash('sha256').update(spki).digest('hex')};
};
const makeProof=(key,challenge)=>{
  const p={schema:1,operation:challenge.operation,deviceId:key.pin,
    challengeId:challenge.challengeId,nonce:challenge.nonce,
    spkiB64Url:b64url(key.spki)};
  return {...p,signatureDerB64Url:b64url(nodeSign('sha256',Buffer.from(proofMessage(p)),key.pair.privateKey))};
};
const storage=()=>{
  const data=new Map();
  const kv={get:k=>structuredClone(data.get(k)),put:(k,v)=>data.set(k,structuredClone(v))};
  return {kv,transactionSync:f=>f()};
};
const register=(s,key,status='active')=>s.kv.put('v9-auth:registered:'+key.pin,
  {deviceId:key.pin,spkiPin:key.pin,status}); // privileged test setup, NEVER HTTP
async function rejects(fn){await assert.rejects(fn,/BLOCKED:/);}

test('P-256 Android-compatible DER signature verifies against server nonce', async()=>{
  const key=getKey(),c={operation:'receipt',deviceId:key.pin,challengeId:hex32(),nonce:hex32()};
  assert.equal((await verifyDeviceProof(makeProof(key,c),{...c,spkiPin:key.pin})).authenticated,true);
});
test('unregistered public key injection rejected',async()=>{
  const a=getKey(),b=getKey(),c={operation:'receipt',deviceId:a.pin,challengeId:hex32(),nonce:hex32()};
  const proof=makeProof(b,{...c,deviceId:b.pin});
  await rejects(()=>verifyDeviceProof(proof,{...c,spkiPin:a.pin}));
});
test('different operation fails',async()=>{
  const k=getKey(),c={operation:'prepare',deviceId:k.pin,challengeId:hex32(),nonce:hex32()};
  await rejects(()=>verifyDeviceProof(makeProof(k,{...c,operation:'receipt'}),{...c,spkiPin:k.pin}));
});
test('different challenge id fails',async()=>{
  const k=getKey(),c={operation:'receipt',deviceId:k.pin,challengeId:hex32(),nonce:hex32()};
  await rejects(()=>verifyDeviceProof(makeProof(k,{...c,challengeId:hex32()}),{...c,spkiPin:k.pin}));
});
test('different nonce fails',async()=>{
  const k=getKey(),c={operation:'receipt',deviceId:k.pin,challengeId:hex32(),nonce:hex32()};
  await rejects(()=>verifyDeviceProof(makeProof(k,{...c,nonce:hex32()}),{...c,spkiPin:k.pin}));
});
test('tampered signature fails',async()=>{
  const k=getKey(),c={operation:'receipt',deviceId:k.pin,challengeId:hex32(),nonce:hex32()};
  const p=makeProof(k,c),sig=Buffer.from(p.signatureDerB64Url,'base64url');sig[sig.length-1]^=1;p.signatureDerB64Url=b64url(sig);
  await rejects(()=>verifyDeviceProof(p,{...c,spkiPin:k.pin}));
});
test('extra proof fields fail closed',async()=>{
  const k=getKey(),c={operation:'receipt',deviceId:k.pin,challengeId:hex32(),nonce:hex32()};
  await rejects(()=>verifyDeviceProof({...makeProof(k,c),verified:true},{...c,spkiPin:k.pin}));
});
test('noncanonical base64url rejected',()=>{
  assert.throws(()=>strictBase64Url('YQ=='),/BLOCKED/);
  assert.throws(()=>strictBase64Url('Y'),/BLOCKED/);
});
test('DER indefinite and trailing bytes rejected',()=>{
  assert.throws(()=>derToP1363(Uint8Array.from([0x30,0x80,0x02,0x01,0x01,0x02,0x01,0x01])),/BLOCKED/);
  assert.throws(()=>derToP1363(Uint8Array.from([0x30,0x07,0x02,0x01,0x01,0x02,0x01,0x01,0xff])),/BLOCKED/);
});
test('no provisioned registration -> no challenge issued',()=>{
  const k=getKey(),s=storage(),store=new V9DeviceChallengeStore(s);
  assert.throws(()=>store.issue({deviceId:k.pin,operation:'receipt'}),/BLOCKED/);
});
test('registered device: single-use verified challenge',async()=>{
  const k=getKey(),s=storage();register(s,k);
  const store=new V9DeviceChallengeStore(s), c=store.issue({deviceId:k.pin,operation:'receipt'});
  assert.equal((await store.verifyAndConsume(makeProof(k,c))).challengeConsumed,true);
  await rejects(()=>store.verifyAndConsume(makeProof(k,c)));
});
test('new challenge blocked while pending',()=>{
  const k=getKey(),s=storage();register(s,k);const store=new V9DeviceChallengeStore(s);
  store.issue({deviceId:k.pin,operation:'receipt'});
  assert.throws(()=>store.issue({deviceId:k.pin,operation:'receipt'}),/BLOCKED/);
});
test('expired challenge rejected and may be replaced',async()=>{
  const k=getKey(),s=storage();register(s,k);let now=1_800_000_000_000;
  const store=new V9DeviceChallengeStore(s,{clock:()=>now}),old=store.issue({deviceId:k.pin,operation:'receipt'});
  now+=90_001;
  await rejects(()=>store.verifyAndConsume(makeProof(k,old)));
  const newer=store.issue({deviceId:k.pin,operation:'receipt'});
  assert.notEqual(newer.challengeId,old.challengeId);
});
test('device revoked before consumption rejected',async()=>{
  const k=getKey(),s=storage();register(s,k);const store=new V9DeviceChallengeStore(s);
  const c=store.issue({deviceId:k.pin,operation:'receipt'});
  register(s,k,'revoked');
  await rejects(()=>store.verifyAndConsume(makeProof(k,c)));
});
test('concurrent consume exactly one wins and survives store reopen',async()=>{
  const k=getKey(),s=storage();register(s,k);const a=new V9DeviceChallengeStore(s);
  const c=a.issue({deviceId:k.pin,operation:'receipt'}),p=makeProof(k,c);
  const result=await Promise.allSettled([a.verifyAndConsume(p),a.verifyAndConsume(p)]);
  assert.equal(result.filter(x=>x.status==='fulfilled').length,1);
  const b=new V9DeviceChallengeStore(s);
  await rejects(()=>b.verifyAndConsume(p));
});
test('invalid operation and device identity are rejected',()=>{
  const k=getKey(),s=storage();register(s,k);const store=new V9DeviceChallengeStore(s);
  assert.throws(()=>store.issue({deviceId:k.pin,operation:'enroll'}),/BLOCKED/);
  assert.throws(()=>store.issue({deviceId:'abc',operation:'receipt'}),/BLOCKED/);
});
test('no public-facing fetch or privileged enroll method exists',()=>{
  assert.equal(typeof V9DeviceChallengeStore.prototype.fetch,'undefined');
  assert.equal(typeof V9DeviceChallengeStore.prototype.enroll,'undefined');
});
