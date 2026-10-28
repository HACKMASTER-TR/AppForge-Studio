// V9 device-bound proof verifier. INTERNAL API ONLY: no HTTP route, signup or admin bypass.
// Android uses an independent Android Keystore P-256 key; DER ECDSA is strictly decoded.
export const DEVICE_DOMAIN = 'APPFORGE-V9-DEVICE-PROOF\0';
const enc = new TextEncoder();
const HEX32 = /^[0-9a-f]{32}$/;
const HEX64 = /^[0-9a-f]{64}$/;
const B64URL = /^[A-Za-z0-9_-]+$/;
export const OPERATIONS = Object.freeze(['receipt','prepare','finalize','abort']);
export function demand(ok, reason) { if (!ok) throw new Error('BLOCKED: '+reason); }
export function exact(value, names, label) {
  demand(value && typeof value === 'object' && !Array.isArray(value) &&
    Object.keys(value).length === names.length && names.every(x=>Object.hasOwn(value,x)), label+' schema');
}
export function hex(bytes) { return Array.from(bytes,x=>x.toString(16).padStart(2,'0')).join(''); }
export function strictBase64Url(raw, sizeRange=[1,256]) {
  demand(typeof raw==='string' && raw.length <= 512 && B64URL.test(raw), 'base64url syntax');
  const padded = raw.replaceAll('-','+').replaceAll('_','/')+'='.repeat((4-raw.length%4)%4);
  let result;
  try { result=Uint8Array.from(atob(padded),ch=>ch.charCodeAt(0)); }
  catch { throw new Error('BLOCKED: malformed base64url'); }
  demand(result.length>=sizeRange[0] && result.length<=sizeRange[1], 'base64url length');
  const recon=btoa(String.fromCharCode(...result)).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,'');
  demand(recon===raw,'noncanonical base64url');
  return result;
}
function derInt(bytes,start,limit) {
  demand(start+2<=limit && bytes[start]===2,'ECDSA integer tag');
  const n=bytes[start+1], pos=start+2;
  demand(n>=1 && n<=33 && pos+n<=limit,'ECDSA integer length');
  const raw=bytes.slice(pos,pos+n);
  demand((raw[0]&0x80)===0,'negative ECDSA integer');
  demand(!(n>1 && raw[0]===0 && (raw[1]&0x80)===0),'nonminimal ECDSA integer');
  const stripped=raw[0]===0?raw.slice(1):raw;
  demand(stripped.length<=32 && stripped.some(x=>x!==0),'out-of-range ECDSA integer');
  const padded=new Uint8Array(32);padded.set(stripped,32-stripped.length);
  return [padded,pos+n];
}
export function derToP1363(der) {
  demand(der instanceof Uint8Array && der.length>=8 && der.length<=72, 'ECDSA DER size');
  demand(der[0]===0x30 && der[1]===der.length-2,'ECDSA DER sequence');
  const [r,pos]=derInt(der,2,der.length);
  const [s,end]=derInt(der,pos,der.length);
  demand(end===der.length,'ECDSA DER trailing content');
  const out=new Uint8Array(64);out.set(r);out.set(s,32);return out;
}
export function proofMessage({operation,deviceId,challengeId,nonce}) {
  demand(OPERATIONS.includes(operation), 'operation');
  demand(typeof deviceId==='string'&&HEX64.test(deviceId),'device id');
  demand(typeof challengeId==='string'&&HEX32.test(challengeId),'challenge id');
  demand(typeof nonce==='string'&&HEX32.test(nonce),'server nonce');
  return enc.encode(DEVICE_DOMAIN+[operation,deviceId,challengeId,nonce].join('\n')+'\n');
}
// The expected challenge, key pin, and device registration come exclusively from
// server-side DO state, never from a field supplied in the Android request.
export async function verifyDeviceProof(proof, trusted) {
  exact(proof,['schema','operation','deviceId','challengeId','nonce','spkiB64Url','signatureDerB64Url'],'device proof');
  exact(trusted,['deviceId','operation','challengeId','nonce','spkiPin'],'trusted challenge');
  demand(proof.schema===1,'proof version');
  demand(proof.deviceId===trusted.deviceId && HEX64.test(trusted.deviceId),'device mismatch');
  demand(proof.operation===trusted.operation && OPERATIONS.includes(proof.operation),'operation mismatch');
  demand(proof.challengeId===trusted.challengeId && HEX32.test(trusted.challengeId),'challenge mismatch');
  demand(proof.nonce===trusted.nonce && HEX32.test(trusted.nonce),'nonce mismatch');
  demand(typeof trusted.spkiPin==='string'&&HEX64.test(trusted.spkiPin),'trusted key pin');
  const der=strictBase64Url(proof.spkiB64Url,[70,110]);
  const pin=hex(new Uint8Array(await crypto.subtle.digest('SHA-256',der)));
  demand(pin===trusted.spkiPin && proof.deviceId===pin,'registered public key mismatch');
  let pub;
  try {pub=await crypto.subtle.importKey('spki',der,{name:'ECDSA',namedCurve:'P-256'},false,['verify']);}
  catch {throw new Error('BLOCKED: invalid P-256 public key');}
  const sig=derToP1363(strictBase64Url(proof.signatureDerB64Url,[8,72]));
  const ok=await crypto.subtle.verify({name:'ECDSA',hash:'SHA-256'},pub,sig,proofMessage(proof));
  demand(ok,'device signature');
  return {deviceId:pin,authenticated:true};
}
