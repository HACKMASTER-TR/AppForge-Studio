import test from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,sign,webcrypto} from 'node:crypto';
import {WitnessLedger} from './ledger.mjs';
import {verifyTransition,canonical,digest} from './verify.mjs';
if (!globalThis.crypto)globalThis.crypto=webcrypto;
if (!globalThis.atob)globalThis.atob=s=>Buffer.from(s,'base64').toString('binary');
if (!globalThis.btoa)globalThis.btoa=s=>Buffer.from(s,'binary').toString('base64');
const d1='1'.repeat(64),d2='2'.repeat(64);
const mem=()=>{
 const kv=new Map();return {kv:{get:k=>structuredClone(kv.get(k)),put:(k,v)=>kv.set(k,structuredClone(v))},transactionSync(fn){const backup=structuredClone(kv);try{return fn();}catch(e){kv.clear();for(const [k,v] of backup)kv.set(k,v);throw e;}},dump:()=>structuredClone(kv)};
};
const now=Math.floor(Date.now()/1000)*1000;
const stamp=d=>new Date(d).toISOString().replace('.000','');
const issued=stamp(now-4000),expires=stamp(now+86400000);
function key(){const x=generateKeyPairSync('ed25519');return {...x,der:x.publicKey.export({type:'spki',format:'der'})};}
const keys={old:key(),next:key(),relA:key(),relB:key(),rogue:key()};
async function entry(k){const der=keys[k].der;return {keyid:await digest(der),spki_der_b64:der.toString('base64')};}
function signEnvelope(payload,who,domain){return {keyid:'',sig_b64:sign(null,Buffer.from(domain+canonical(payload)),keys[who].privateKey).toString('base64')};}
let pin,evidenceRaw,evidencePin;
async function setup(){
 pin=await digest(keys.old.der);
 evidenceRaw=canonical({schema:1,stage:'OFFLINE_EVIDENCE_ONLY',snapshot:'20261018T000000Z',original_objects_verified:218,original_deb_objects_verified:214,ubuntu_inrelease_verified:4,signed_packages_indexes_verified:32,distinct_content_addressed_objects:248,distinct_content_addressed_bytes:650832480,appforge_ed25519_release_signature:'NOT_PRESENT',r2_upload_allowed:false,public_distribution_allowed:false,android_client_enabled:false,apt_ready:false,all_engines_ready:false});
 evidencePin=await digest(evidenceRaw);
}
await setup();
async function doc(seq=1,{root=1,rotate=false,wrongRelease=false,rootSigners=null,relSigners=['relA'],expired=false,enable=false,threshold=1}={}){
 const rootPayload={schema:1,role:'appforge-v9-root',version:root,issued_at:issued,expires_at:expires,root_key:await entry(rotate?'next':'old'),release_keys:[await entry('relA'),await entry('relB')],release_threshold:threshold};
 const signers=rootSigners??(rotate?['old','next']:['old']);
 const sigs=[];for(const name of signers){const x=signEnvelope(rootPayload,name,'APPFORGE-V9-PHASE3E-ROOT\0');x.keyid=(await entry(name)).keyid;sigs.push(x);}
 const releasePayload={schema:1,role:'appforge-v9-release',root_version:root,sequence:seq,snapshot:'20261018T000000Z',evidence_sha256:evidencePin,issued_at:issued,expires_at:expired?stamp(now-3000):expires,r2_upload_allowed:enable,public_distribution_allowed:false,android_client_enabled:false};
 const releaseSignatures=[];for(const name of (wrongRelease?['rogue']:relSigners)){const x=signEnvelope(releasePayload,name,'APPFORGE-V9-PHASE3E-RELEASE\0');x.keyid=(await entry(name)).keyid;releaseSignatures.push(x);}
 return {evidenceRaw,rootRaw:canonical({payload:rootPayload,signatures:sigs}),releaseRaw:canonical({payload:releasePayload,signatures:releaseSignatures})};
}
function ledger(storage=mem()){return new WitnessLedger(storage,{bootstrapDer:keys.old.der.toString('base64'),bootstrapPin:pin,evidencePin,clock:()=>now});}
async function prep(w,device,seq,options){return w.prepare({device,...await doc(seq,options)});}
async function boot(w,device=d1){const p=await prep(w,device,1);w.finalize(device,p.token);return p;}
async function reject(fn,fragment){await assert.rejects(fn,new RegExp(fragment));}

test('signed initial bootstrap and generation 2',async()=>{const w=ledger();const p=await prep(w,d1,1);assert.equal(p.signedReceipt,false);assert.equal(w.snapshot(d1).generation,1);assert.equal(w.finalize(d1,p.token).committed.release_sequence,1);assert.equal(w.snapshot(d1).generation,2);});
test('real signed sequence advance and CAS',async()=>{const w=ledger();await boot(w);const p=await prep(w,d1,2);assert.equal(w.finalize(d1,p.token).committed.release_sequence,2);});
test('old release never accepted after commit',async()=>{const w=ledger();await boot(w);await reject(()=>prep(w,d1,1),'replay/rollback');});
test('two separate devices can independently receive same signed release',async()=>{const w=ledger();await boot(w,d1);await boot(w,d2);assert.equal(w.snapshot(d2).committed.release_sequence,1);});
test('abort burns signed metadata and forbids re-bootstrap',async()=>{const w=ledger();const p=await prep(w,d1,1);w.abort(d1,p.token);await reject(()=>prep(w,d1,1),'bootstrap replay');});
test('update abort burns release digest but keeps prior state',async()=>{const w=ledger();await boot(w);const p=await prep(w,d1,2);w.abort(d1,p.token);assert.equal(w.snapshot(d1).committed.release_sequence,1);await reject(()=>prep(w,d1,2),'signed release replay');});
test('rogue release signer rejected',async()=>{const w=ledger();await reject(()=>prep(w,d1,1,{wrongRelease:true}),'untrusted release signer');assert.equal(w.snapshot(d1).generation,0);});
test('invalid release signature rejected',async()=>{const w=ledger(),o=await doc();let obj=JSON.parse(o.releaseRaw);obj.payload.issued_at=stamp(now-3000);await reject(()=>w.prepare({device:d1,...o,releaseRaw:canonical(obj)}),'signature verification failed');});
test('root rotation requires both old and new signatures',async()=>{const w=ledger();await boot(w);await reject(()=>prep(w,d1,2,{root:2,rotate:true,rootSigners:['old']}),'missing required signature');});
test('root rotation with both signer keys works',async()=>{const w=ledger();await boot(w);const p=await prep(w,d1,2,{root:2,rotate:true});assert.equal(w.finalize(d1,p.token).committed.root_version,2);});
test('reject bad root signer',async()=>{const w=ledger();await reject(()=>prep(w,d1,1,{rootSigners:['rogue']}),'missing required signature');});
test('signed metadata cannot turn on distribution',async()=>{const w=ledger();await reject(()=>prep(w,d1,1,{enable:true}),'distribution permission denied');});
test('expired signed release is blocked',async()=>{const w=ledger();await reject(()=>prep(w,d1,1,{expired:true}),'signature time window');});
test('release threshold is enforced',async()=>{const w=ledger();await reject(()=>prep(w,d1,1,{threshold:2}),'release threshold');});
test('threshold two real Ed25519 release signers work',async()=>{const w=ledger();const p=await prep(w,d1,1,{threshold:2,relSigners:['relA','relB']});assert.equal(w.finalize(d1,p.token).committed.release_sequence,1);});
test('duplicate signed JSON object key blocked',async()=>{const w=ledger(),o=await doc();await reject(()=>w.prepare({device:d1,...o,rootRaw:o.rootRaw.replace('"payload":','"payload":null,"payload":')}),'noncanonical');});
test('noncanonical metadata whitespace blocked',async()=>{const w=ledger(),o=await doc();await reject(()=>w.prepare({device:d1,...o,releaseRaw:o.releaseRaw+' '}),'noncanonical');});
test('untrusted evidence pin blocked before storage write',async()=>{const w=ledger(),o=await doc();await reject(()=>w.prepare({device:d1,...o,evidenceRaw:o.evidenceRaw.replace('218','219')}),'evidence pin');assert.equal(w.snapshot(d1).generation,0);});
test('wrong initial SPKI pin blocked',async()=>{const w=new WitnessLedger(mem(),{bootstrapDer:keys.old.der.toString('base64'),bootstrapPin:'a'.repeat(64),evidencePin,clock:()=>now});await reject(()=>prep(w,d1,1),'bootstrap root pin');});
test('unrecognized state is rejected',async()=>{const s=mem();s.kv.put('device:'+d1,{generation:-1,committed:null,pending:null});assert.throws(()=>ledger(s).snapshot(d1),/generation/);});
test('wrong token cannot settle pending',async()=>{const w=ledger();const p=await prep(w,d1,1);assert.throws(()=>w.finalize(d1,'0'.repeat(32)),/missing\/stale/);assert.equal(w.snapshot(d1).generation,1);w.finalize(d1,p.token);});
test('pending blocks another update',async()=>{const w=ledger();await prep(w,d1,1);await reject(()=>prep(w,d1,1),'pending transaction');});
test('no client accepted verified flag or caller-supplied signer',async()=>{const w=ledger();await reject(()=>w.prepare({device:d1,verified:true,successor:{release_sequence:77}}),'canonical JSON byte length');});
test('same release concurrent prepare admits exactly one',async()=>{const w=ledger();const request={device:d1,...await doc()};const results=await Promise.allSettled([w.prepare(request),w.prepare(request)]);assert.equal(results.filter(x=>x.status==='fulfilled').length,1);});
test('storage reopen keeps committed checkpoint',async()=>{const store=mem(),w=ledger(store);await boot(w);const restarted=ledger(store);assert.equal(restarted.snapshot(d1).committed.release_sequence,1);});
test('public Worker route stays hard-denied with no trust keys',async()=>{const {handleHttp}=await import('./http.mjs');const r=handleHttp();assert.equal(r.status,503);assert.equal(r.headers.get('Cache-Control'),'no-store');const source=(await import('node:fs/promises')).readFile;const raw=await source(new URL('./worker.mjs',import.meta.url),'utf8');assert.match(raw,/fetch:handleHttp/);assert.doesNotMatch(raw,/APPFORGE_PRODUCTION_PRIVATE_KEY/);});
test('wrangler config has no public worker route or enabled staging',async()=>{const fs=await import('node:fs/promises');const toml=await fs.readFile(new URL('./wrangler.example.toml',import.meta.url),'utf8');assert.match(toml,/workers_dev = false/);assert.match(toml,/V9_STAGING_WITNESS_MODE = "DISABLED"/);assert.doesNotMatch(toml,/^route\s*=/m);});

test('Python Phase3G and JS Phase3E/3F produce byte-equivalent candidate state',async()=>{
  const {execFileSync}=await import('node:child_process');
  const fixture=JSON.parse(execFileSync('python3',[new URL('./cross-language-fixture.py',import.meta.url).pathname],{encoding:'utf8',timeout:20000}));
  const {expected,...args}=fixture;
  assert.deepEqual(await verifyTransition(args),expected);
});
