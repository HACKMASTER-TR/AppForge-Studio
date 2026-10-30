import test from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,createHash,sign as signDer} from 'node:crypto';
import {createV9RegisteredAdminProofVerifier} from './stage5e5-admin-proof.mjs';
import {createV9PrivateAdminGateway} from './stage5e4-admin-gateway.mjs';
const NOW=1_800_000_000_000;
const ORIGIN='https://v9.invalid';
const BEGIN='/v9/private/admin/session/begin';
const DECIDE='/v9/private/admin/device/decide';
const JWT='a'.repeat(40)+'.'+'b'.repeat(50)+'.'+'c'.repeat(85);
const DEVICE='d'.repeat(64);
const encode=v=>Buffer.from(v).toString('base64url');
const hex=v=>createHash('sha256').update(v).digest('hex');
const memory=()=>{const m=new Map();return {kv:{get:k=>structuredClone(m.get(k)),put:(k,v)=>m.set(k,structuredClone(v))},transactionSync:f=>f()}};
const pair=()=>{const {publicKey,privateKey}=generateKeyPairSync('ec',{namedCurve:'prime256v1'});const spki=publicKey.export({format:'der',type:'spki'});return {privateKey,spki,principalId:hex(spki)}};
function fixture(options={}) {
  let now=NOW,count=0;
  const db=memory(),key=pair(),other=pair();
  const register=(k=key,overrides={})=>db.kv.put('s5e5:admin-device:'+k.principalId,{
    status:'active',purpose:'admin-operator',principalId:k.principalId,
    spkiDerB64Url:encode(k.spki),expiresAt:NOW+600000,...overrides});
  if(options.register!==false)register();
  const verifier=createV9RegisteredAdminProofVerifier({storage:db,expectedOrigin:ORIGIN,clock:()=>now});
  function request(path=BEGIN,body='{}',config={}) {
    const signer=config.key||key;
    const id=config.proofId||(++count).toString(16).padStart(32,'0');
    const issued=config.issuedAt??now;
    const method=config.method||'POST';
    const origin=config.origin||ORIGIN;
    const digest=hex(Buffer.from(body));
    const message=Buffer.from('APPFORGE-V9-STAGE5E4-HTTP\0'+method+'\n'+origin+'\n'+path+'\n'+digest+'\n'+id+'\n'+issued+'\n');
    const sig=encode(signDer('sha256',message,signer.privateKey));
    const headers={
      'Content-Type':'application/json',
      'X-AppForge-V9-Principal':config.principalId||signer.principalId,
      'X-AppForge-V9-Proof-Id':id,
      'X-AppForge-V9-Issued-At':String(issued),
      'X-AppForge-V9-Signature':sig,...config.headers
    };
    const req=new Request(origin+path,{method,headers,body});
    return {req,rawBody:body,path};
  }
  const authenticate=async args=>verifier(args);
  return {db,key,other,register,request,authenticate,setTime:t=>now=t,now:()=>now};
}
const deny=async p=>assert.rejects(p,/BLOCKED/);
test('valid registered P-256 SHA256withECDSA DER signature accepted',async()=>{const f=fixture(),a=f.request();const p=await f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody});assert.equal(p.verified,true);assert.equal(p.principalId,f.key.principalId)});
test('unregistered key rejected',async()=>{const f=fixture({register:false}),a=f.request();await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('revoked administrator device rejected',async()=>{const f=fixture();f.register(f.key,{status:'revoked'});const a=f.request();await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('pending administrator device rejected',async()=>{const f=fixture();f.register(f.key,{status:'pending'});const a=f.request();await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('non administrator device rejected',async()=>{const f=fixture();f.register(f.key,{purpose:'regular-device'});const a=f.request();await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('expired registered key rejected',async()=>{const f=fixture();f.register(f.key,{expiresAt:NOW-1});const a=f.request();await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('principal header cannot impersonate pinned key',async()=>{const f=fixture(),a=f.request(BEGIN,'{}',{key:f.other,principalId:f.key.principalId});await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('a stored different SPKI cannot match principal fingerprint',async()=>{const f=fixture();f.register(f.key,{spkiDerB64Url:encode(f.other.spki)});const a=f.request();await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('client-provided public key header cannot self enroll',async()=>{const f=fixture({register:false}),a=f.request(BEGIN,'{}',{headers:{'X-AppForge-V9-Key':encode(f.key.spki)}});await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('altered body after signature rejected',async()=>{const f=fixture(),a=f.request(BEGIN,'{}');await deny(f.authenticate({request:a.req,path:a.path,rawBody:'{"x":1}'}))});
test('altered method signature rejected',async()=>{const f=fixture(),a=f.request(BEGIN,'{}',{method:'POST'});await deny(f.authenticate({request:a.req,path:DECIDE,rawBody:a.rawBody}))});
test('altered path signed bytes rejected',async()=>{const f=fixture(),a=f.request(BEGIN,'{}');await deny(f.authenticate({request:a.req,path:a.path,rawBody:'{ }'}))});
test('old timestamp rejected',async()=>{const f=fixture(),a=f.request(BEGIN,'{}',{issuedAt:NOW-30001});await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('future timestamp rejected',async()=>{const f=fixture(),a=f.request(BEGIN,'{}',{issuedAt:NOW+30001});await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('missing signature rejected',async()=>{const f=fixture(),a=f.request(BEGIN,'{}',{headers:{'X-AppForge-V9-Signature':''}});await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('malformed DER signature rejected',async()=>{const f=fixture(),a=f.request(BEGIN,'{}',{headers:{'X-AppForge-V9-Signature':encode(Buffer.from([0x30,0x03,0x02,0x01,0x01]))}});await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('noncanonical base64url signature rejected',async()=>{const f=fixture(),a=f.request(BEGIN,'{}',{headers:{'X-AppForge-V9-Signature':'a==='}});await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('wrong principal ID format rejected',async()=>{const f=fixture(),a=f.request(BEGIN,'{}',{principalId:'owner@example.com'});await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('no issuer or untrusted origin rejected',async()=>{const f=fixture(),a=f.request(BEGIN,'{}',{origin:'https://evil.invalid'});await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('unrecognized route denied',async()=>{const f=fixture(),a=f.request('/v9/private/admin/debug','{}');await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))});
test('browser cookie and origin headers denied',async()=>{const f=fixture();for(const headers of [{Cookie:'session=x'},{Origin:'https://evil.invalid'}]){const a=f.request(BEGIN,'{}',{headers});await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))}});
test('empty missing trusted registry denied at construction',()=>{assert.throws(()=>createV9RegisteredAdminProofVerifier({expectedOrigin:ORIGIN}),/BLOCKED/)});
test('non P-256 RSA and P-384 stored keys rejected',async()=>{const f=fixture();for(const opts of [{modulusLength:2048}]){const rsa=generateKeyPairSync('rsa',opts);f.register(f.key,{spkiDerB64Url:encode(rsa.publicKey.export({type:'spki',format:'der'}))});const a=f.request();await deny(f.authenticate({request:a.req,path:a.path,rawBody:a.rawBody}))}});
test('valid signed session and decision work through source-only gateway, then replay denied',async()=>{
  const f=fixture();let called=0,nonceCount=0;
  const service={beginAdminSession(){return {sessionId:(++nonceCount).toString(16).padStart(32,'0'),nonce:'N'.repeat(43),expiresAt:NOW+150000}},async decideDevice({deviceId,action}){called++;return {deviceId,status:action==='approve'?'active':'revoked'}}};
  const gateway=createV9PrivateAdminGateway({service,storage:f.db,authenticate:f.authenticate,rateLimit:async()=>true,expectedOrigin:ORIGIN,clock:f.now});
  const begin=f.request(BEGIN);const challengeResponse=await gateway(begin.req);assert.equal(challengeResponse.status,200);const session=await challengeResponse.json();
  const decision=f.request(DECIDE,JSON.stringify({sessionId:session.sessionId,deviceId:DEVICE,action:'approve',idToken:JWT}));
  assert.equal((await gateway(decision.req)).status,200);assert.equal(called,1);
  const replay=f.request(DECIDE,JSON.stringify({sessionId:session.sessionId,deviceId:DEVICE,action:'approve',idToken:JWT}));assert.equal((await gateway(replay.req)).status,403);assert.equal(called,1);
});
test('same signed proof cannot be reused for a new session',async()=>{
  const f=fixture(),service={beginAdminSession:()=>({sessionId:'b'.repeat(32),nonce:'N'.repeat(43),expiresAt:NOW+90000}),decideDevice:async()=>{throw Error('No')}};
  const gateway=createV9PrivateAdminGateway({service,storage:f.db,authenticate:f.authenticate,rateLimit:async()=>true,expectedOrigin:ORIGIN,clock:f.now});
  const a=f.request(BEGIN,'{}',{proofId:'a'.repeat(32)});
  assert.equal((await gateway(a.req.clone())).status,200);
  assert.equal((await gateway(a.req.clone())).status,403);
});
test('revocation between session begin and decision prevents use',async()=>{
  const f=fixture(),service={beginAdminSession:()=>({sessionId:'f'.repeat(32),nonce:'N'.repeat(43),expiresAt:NOW+120000}),decideDevice:async()=>{throw Error('should not be called')}};
  const gate=createV9PrivateAdminGateway({service,storage:f.db,authenticate:f.authenticate,rateLimit:async()=>true,expectedOrigin:ORIGIN,clock:f.now});
  assert.equal((await gate(f.request(BEGIN).req)).status,200);
  f.register(f.key,{status:'revoked'});
  const x=f.request(DECIDE,JSON.stringify({sessionId:'f'.repeat(32),deviceId:DEVICE,action:'approve',idToken:JWT}));
  assert.equal((await gate(x.req)).status,403);
});
test('cross-language canonical byte vector matches Kotlin V9AdminProofWire',()=>{
  const id='a'.repeat(32),body='{}',stamp=NOW;
  const digest=hex(Buffer.from(body));
  const c='APPFORGE-V9-STAGE5E4-HTTP\0POST\n'+ORIGIN+'\n'+BEGIN+'\n'+digest+'\n'+id+'\n'+stamp+'\n';
  assert.equal(hex(Buffer.from(c)), '3e94e6a7b120f6d7776b9b79e9490b6f3f358d7b4f2a13d43a8c4f0a9731b51b');
});
