import test from 'node:test';
import assert from 'node:assert/strict';
import {createV9PrivateAdminGateway} from './stage5e4-admin-gateway.mjs';
const NOW=1800000000000, A='a'.repeat(64), B='b'.repeat(64),DEVICE='d'.repeat(64);
const JWT='x'.repeat(30)+'.'+'y'.repeat(40)+'.'+'z'.repeat(80);
const BEGIN='/v9/private/admin/session/begin',DECIDE='/v9/private/admin/device/decide';
const db=()=>{const m=new Map();return {kv:{get:k=>structuredClone(m.get(k)),put:(k,v)=>m.set(k,structuredClone(v))},transactionSync:f=>f()}};
let serial=1;
function fixture(opts={}){
  let now=NOW,starts=0,decisions=0,allowed=true,authenticated=true;
  const storage=db();
  const service={beginAdminSession:()=>({sessionId:(++starts).toString(16).padStart(32,'0'),nonce:'N'.repeat(43),expiresAt:now+150000}),
    decideDevice:async x=>{decisions++;if(opts.failDecision)throw Error('Google denied');return {deviceId:x.deviceId,status:{approve:'active',reject:'rejected',revoke:'revoked'}[x.action]}}};
  const gate=createV9PrivateAdminGateway({service,storage,clock:()=>now,expectedOrigin:'https://v9.invalid',
    authenticate:async({request})=>authenticated?{verified:true,principalId:request.headers.get('x-test-sub')||A,
      proofId:request.headers.get('x-test-proof'),issuedAt:Number(request.headers.get('x-test-time'))}:null,
    rateLimit:async()=>allowed});
  async function send(path,body={},extra={}){
    const id=String(serial++).padStart(32,'0');
    const req=new Request('https://v9.invalid'+path,{method:extra.method||'POST',headers:{'content-type':'application/json',
      'x-test-sub':extra.principal||A,'x-test-proof':extra.proof||id,'x-test-time':String(extra.time??now),...extra.headers},
      body:JSON.stringify(body)});
    const res=await gate(req);return {status:res.status,body:await res.json(),headers:res.headers};
  }
  return {send,service,storage,gate,get starts(){return starts},get decisions(){return decisions},setNow:n=>now=n,
    denyRate:()=>allowed=false,denyAuth:()=>authenticated=false};
}
const body=(sid,action='approve')=>({sessionId:sid,deviceId:DEVICE,action,idToken:JWT});
const denied=async p=>assert.equal((await p).status,403);
test('begin issues only server session and no-cache',async()=>{const f=fixture(),r=await f.send(BEGIN);assert.equal(r.status,200);assert.equal(r.body.nonce,'N'.repeat(43));assert.equal(r.headers.get('Cache-Control'),'no-store, private')});
test('decision is server-derived, never returns ID token',async()=>{const f=fixture(),s=(await f.send(BEGIN)).body,r=await f.send(DECIDE,body(s.sessionId));assert.equal(r.status,200);assert.deepEqual(r.body,{deviceId:DEVICE,status:'active'});assert.equal(JSON.stringify(r.body).includes(JWT),false)});
test('cross-principal session theft rejected',async()=>{const f=fixture(),s=(await f.send(BEGIN)).body;await denied(f.send(DECIDE,body(s.sessionId),{principal:B}));assert.equal(f.decisions,0)});
test('decision replay denied',async()=>{const f=fixture(),s=(await f.send(BEGIN)).body;assert.equal((await f.send(DECIDE,body(s.sessionId))).status,200);await denied(f.send(DECIDE,body(s.sessionId)));assert.equal(f.decisions,1)});
test('failed Google decision consumes binding',async()=>{const f=fixture({failDecision:true}),s=(await f.send(BEGIN)).body;await denied(f.send(DECIDE,body(s.sessionId)));await denied(f.send(DECIDE,body(s.sessionId)));assert.equal(f.decisions,1)});
test('expired session denied',async()=>{const f=fixture(),s=(await f.send(BEGIN)).body;f.setNow(NOW+150001);await denied(f.send(DECIDE,body(s.sessionId)));assert.equal(f.decisions,0)});
test('reused request proof rejected',async()=>{const f=fixture();assert.equal((await f.send(BEGIN,{}, {proof:'a'.repeat(32)})).status,200);await denied(f.send(BEGIN,{}, {proof:'a'.repeat(32)}));assert.equal(f.starts,1)});
test('old and future proof denied',async()=>{const f=fixture();await denied(f.send(BEGIN,{}, {time:NOW-30001}));await denied(f.send(BEGIN,{}, {time:NOW+30001}))});
test('missing verifier result denied',async()=>{const f=fixture();f.denyAuth();await denied(f.send(BEGIN))});
test('rate limiter fail-closed',async()=>{const f=fixture();f.denyRate();await denied(f.send(BEGIN))});
test('invalid device rejected before decision',async()=>{const f=fixture(),s=(await f.send(BEGIN)).body;await denied(f.send(DECIDE,{...body(s.sessionId),deviceId:'bad'}));assert.equal(f.decisions,0)});
test('invalid action rejected',async()=>{const f=fixture(),s=(await f.send(BEGIN)).body;await denied(f.send(DECIDE,body(s.sessionId,'grantOwner')))});
test('unexpected client state and JWT-injected keys rejected',async()=>{const f=fixture();await denied(f.send(BEGIN,{isAdmin:true}));const s=(await f.send(BEGIN)).body;await denied(f.send(DECIDE,{...body(s.sessionId),isAdmin:true}))});
test('bogus token structure rejected',async()=>{const f=fixture(),s=(await f.send(BEGIN)).body;await denied(f.send(DECIDE,{...body(s.sessionId),idToken:'evil'}))});
test('unrecognized route rejected',async()=>{const f=fixture();await denied(f.send('/v9/private/admin/debug'))});
test('query parameter blocked',async()=>{const f=fixture();await denied(f.send(BEGIN+'?admin=true'))});
test('browser origin and cookie blocked',async()=>{const f=fixture();await denied(f.send(BEGIN,{}, {headers:{Origin:'https://evil.invalid'}}));await denied(f.send(BEGIN,{}, {headers:{Cookie:'session=fake'}}))});
test('bad Content-Type blocked',async()=>{const f=fixture();await denied(f.send(BEGIN,{}, {headers:{'content-type':'text/plain'}}))});
test('no credentials or signer allowed at constructor',()=>{for(const opt of [{},{service:{beginAdminSession(){},decideDevice(){}},storage:db(),rateLimit:()=>true}])assert.throws(()=>createV9PrivateAdminGateway(opt),/BLOCKED/)});
test('reject and revoke status are server enforced',async()=>{const f=fixture();for(const action of ['reject','revoke']){const s=(await f.send(BEGIN)).body;const r=await f.send(DECIDE,body(s.sessionId,action));assert.equal(r.status,200);assert.equal(r.body.status,action==='reject'?'rejected':'revoked')}});
