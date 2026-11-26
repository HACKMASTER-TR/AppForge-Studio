// AppForge V9 test-only server-side Phase 3E/3F-compatible Ed25519 verifier.
// No client-authorized trusted roots or evidence pins; the hosting Worker owns them.
export const EVIDENCE_PIN = 'b2341fb22a758774f5cf07c22792539d15342fd931b40f29848db097a9abf439';
export const SNAPSHOT = '20261018T000000Z';
const ROOT_DOMAIN = 'APPFORGE-V9-PHASE3E-ROOT\0';
const RELEASE_DOMAIN = 'APPFORGE-V9-PHASE3E-RELEASE\0';
const SPKI_PREFIX = '302a300506032b6570032100';
const encoder = new TextEncoder();
const H = /^[0-9a-f]{64}$/;
const ISO = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/;
export function block(ok, msg) { if (!ok) throw new Error('BLOCKED: ' + msg); }
export function record(x, names, label) {
  block(x !== null && typeof x === 'object' && !Array.isArray(x) &&
    Object.keys(x).length === names.length && names.every(k => Object.hasOwn(x,k)), label + ' schema');
}
function normalize(x) {
  if (x === null || x === true || x === false) return x;
  if (typeof x === 'string') { block(/^[\x00-\x7f]*$/.test(x), 'non-ASCII field'); return x; }
  if (typeof x === 'number') { block(Number.isSafeInteger(x), 'noninteger JSON'); return x; }
  if (Array.isArray(x)) return x.map(normalize);
  block(typeof x === 'object', 'unsupported JSON type');
  const out = {};
  for (const k of Object.keys(x).sort()) { block(/^[\x00-\x7f]*$/.test(k), 'non-ASCII key'); out[k]=normalize(x[k]); }
  return out;
}
export function canonical(x) { return JSON.stringify(normalize(x))+'\n'; }
export function parseCanonical(raw, cap = 131072) {
  block(typeof raw === 'string' && raw.length > 0 && raw.length <= cap, 'canonical JSON byte length');
  block(/^[\x00-\x7f]*$/.test(raw), 'non-ASCII JSON');
  let x;
  try { x=JSON.parse(raw); } catch { throw new Error('BLOCKED: invalid JSON'); }
  block(canonical(x)===raw, 'noncanonical JSON / duplicate keys');
  return x;
}
export function bytesB64(s, size) {
  block(typeof s === 'string' && s.length < 256 && /^[A-Za-z0-9+/]*={0,2}$/.test(s), 'base64 shape');
  let bytes;
  try { bytes=Uint8Array.from(atob(s), c => c.charCodeAt(0)); } catch { throw new Error('BLOCKED: base64 decode'); }
  block(bytes.length === size && btoa(String.fromCharCode(...bytes)) === s, 'base64 canonical');
  return bytes;
}
export function hex(bytes) { return Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2,'0')).join(''); }
export async function digest(raw) { return hex(await crypto.subtle.digest('SHA-256',typeof raw==='string'?encoder.encode(raw):raw)); }
export async function pubkey(der) {
  block(der.length===44 && hex(der.slice(0,12))===SPKI_PREFIX, 'Ed25519 SPKI DER');
  const pin=await digest(der);
  return {pin,key:await crypto.subtle.importKey('spki',der,{name:'Ed25519'},false,['verify'])};
}
export async function keyEntry(entry) {
  record(entry,['keyid','spki_der_b64'],'public key');
  const der=bytesB64(entry.spki_der_b64,44), key=await pubkey(der);
  block(entry.keyid===key.pin, 'SPKI hash mismatch');
  return {...key,der};
}
function validity(p,now) {
  block(typeof p.issued_at==='string' && typeof p.expires_at==='string' && ISO.test(p.issued_at) && ISO.test(p.expires_at), 'time syntax');
  const issued=Date.parse(p.issued_at), expires=Date.parse(p.expires_at);
  block(Number.isFinite(issued)&&Number.isFinite(expires) && new Date(issued).toISOString().replace('.000','')===p.issued_at && new Date(expires).toISOString().replace('.000','')===p.expires_at,'invalid time');
  block(issued<=now+600000 && expires>now && expires>issued && expires-issued<=90*86400000,'signature time window');
}
function integer(x){return Number.isSafeInteger(x)&&x>=1&&x<=2147483647;}
function envelope(doc){
  record(doc,['payload','signatures'],'signed metadata');
  block(Array.isArray(doc.signatures)&&doc.signatures.length>=1&&doc.signatures.length<=6,'signature count');
  const sigs=new Map();
  for(const x of doc.signatures){record(x,['keyid','sig_b64'],'signature');block(typeof x.keyid==='string'&&H.test(x.keyid)&&!sigs.has(x.keyid),'duplicate/invalid signer');sigs.set(x.keyid,bytesB64(x.sig_b64,64));}
  return {payload:doc.payload,sigs};
}
async function signature(key,sig,domain,payload){
  block(sig!==undefined,'missing required signature');
  block(await crypto.subtle.verify({name:'Ed25519'},key,sig,encoder.encode(domain+canonical(payload))),'signature verification failed');
}
const rootFields=['schema','role','version','issued_at','expires_at','root_key','release_keys','release_threshold'];
const releaseFields=['schema','role','root_version','sequence','snapshot','evidence_sha256','issued_at','expires_at','r2_upload_allowed','public_distribution_allowed','android_client_enabled'];
const evidenceFields={stage:'OFFLINE_EVIDENCE_ONLY',snapshot:SNAPSHOT,original_objects_verified:218,original_deb_objects_verified:214,ubuntu_inrelease_verified:4,signed_packages_indexes_verified:32,distinct_content_addressed_objects:248,distinct_content_addressed_bytes:650832480,appforge_ed25519_release_signature:'NOT_PRESENT',r2_upload_allowed:false,public_distribution_allowed:false,android_client_enabled:false,apt_ready:false,all_engines_ready:false};
export async function verifyTransition({evidenceRaw,rootRaw,releaseRaw,old=null,bootstrapDer,bootstrapPin,evidencePin=EVIDENCE_PIN,now=Date.now()}){
  block(Number.isFinite(now)&&now>=0, 'trusted clock');
  const ev=parseCanonical(evidenceRaw,262144);
  block(H.test(evidencePin)&&await digest(evidenceRaw)===evidencePin,'evidence pin');
  for(const [k,v] of Object.entries(evidenceFields))block(Object.hasOwn(ev,k) && typeof ev[k]===typeof v&&ev[k]===v,'evidence field '+k);
  const rootDoc=parseCanonical(rootRaw),releaseDoc=parseCanonical(releaseRaw), r=envelope(rootDoc), rel=envelope(releaseDoc);
  const rp=r.payload, lp=rel.payload;
  record(rp,rootFields,'root');record(lp,releaseFields,'release');
  block(rp.schema===1&&rp.role==='appforge-v9-root'&&integer(rp.version),'root identity');
  block(lp.schema===1&&lp.role==='appforge-v9-release'&&integer(lp.sequence)&&integer(lp.root_version),'release identity');
  validity(rp,now);validity(lp,now);
  block(lp.snapshot===SNAPSHOT&&lp.evidence_sha256===evidencePin,'release evidence binding');
  block(lp.root_version===rp.version,'root version binding');
  for(const k of ['r2_upload_allowed','public_distribution_allowed','android_client_enabled'])block(lp[k]===false,'distribution permission denied');
  let previous;
  if(old===null){
    const der=bytesB64(bootstrapDer,44); previous={...(await pubkey(der)),der};
    block(previous.pin===bootstrapPin && H.test(bootstrapPin),'bootstrap root pin');
    block(rp.version===1&&lp.sequence===1,'bootstrap floor');
  }else{
    record(old,['schema','stage','snapshot','evidence_sha256','root_version','root_keyid','root_spki_der_b64','root_metadata_sha256','release_sequence','release_metadata_sha256','r2_upload_allowed','public_distribution_allowed','android_client_enabled'],'prior checkpoint');
    block(old.schema===1&&old.stage==='OFFLINE_TRUST_STATE_PROTOTYPE_ONLY'&&old.snapshot===SNAPSHOT&&old.evidence_sha256===evidencePin&&integer(old.root_version)&&integer(old.release_sequence),'prior shape');
    for(const k of ['r2_upload_allowed','public_distribution_allowed','android_client_enabled'])block(old[k]===false,'prior distribution gate');
    previous={...(await pubkey(bytesB64(old.root_spki_der_b64,44)))};
    block(previous.pin===old.root_keyid&&H.test(old.root_metadata_sha256)&&H.test(old.release_metadata_sha256),'prior root pin');
    block(rp.version===old.root_version||rp.version===old.root_version+1,'root rollback/skip');
    block(lp.sequence>old.release_sequence,'release replay/rollback');
  }
  const nextRoot=await keyEntry(rp.root_key);
  block(Array.isArray(rp.release_keys)&&rp.release_keys.length>=1&&rp.release_keys.length<=3,'release key count');
  block(integer(rp.release_threshold)&&rp.release_threshold<=rp.release_keys.length,'release threshold');
  const keys=new Map();
  for(const ent of rp.release_keys){const k=await keyEntry(ent);block(!keys.has(k.pin),'duplicate release key');keys.set(k.pin,k.key);}
  if(old===null){block(nextRoot.pin===previous.pin,'bootstrap mismatch');}
  else if(rp.version===old.root_version){block(nextRoot.pin===old.root_keyid&&await digest(rootRaw)===old.root_metadata_sha256,'same-root equivocation');}
  else block(nextRoot.pin!==old.root_keyid,'rotation must change root');
  await signature(previous.key,r.sigs.get(previous.pin),ROOT_DOMAIN,rp);
  if(nextRoot.pin!==previous.pin)await signature(nextRoot.key,r.sigs.get(nextRoot.pin),ROOT_DOMAIN,rp);
  let verified=0;
  for(const [pin,sig] of rel.sigs){block(keys.has(pin),'untrusted release signer');await signature(keys.get(pin),sig,RELEASE_DOMAIN,lp);verified++;}
  block(verified>=rp.release_threshold,'release threshold');
  return {schema:1,stage:'OFFLINE_TRUST_STATE_PROTOTYPE_ONLY',snapshot:SNAPSHOT,evidence_sha256:evidencePin,root_version:rp.version,root_keyid:nextRoot.pin,root_spki_der_b64:rp.root_key.spki_der_b64,root_metadata_sha256:await digest(rootRaw),release_sequence:lp.sequence,release_metadata_sha256:await digest(releaseRaw),r2_upload_allowed:false,public_distribution_allowed:false,android_client_enabled:false};
}
