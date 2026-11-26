// V9 Stage 5E.5: source-only trusted P-256 proof verifier. NOT a fetch handler.
// Only a separately authorized issuer can populate private admin-device records.
// Never authorize an unknown public key, principal header, Google claim or email.
const HEX64 = /^[0-9a-f]{64}$/;
const HEX32 = /^[0-9a-f]{32}$/;
const B64URL = /^[A-Za-z0-9_-]+$/;
const ORIGIN = /^https:\/\/[a-z0-9.-]+$/;
const PATHS = new Set(['/v9/private/admin/session/begin', '/v9/private/admin/device/decide']);
const text = new TextEncoder();
const shaHex = async bytes => [...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))]
  .map(v => v.toString(16).padStart(2,'0')).join('');
function demand(ok) { if (!ok) throw Error('BLOCKED'); }
function fromB64(s, limit) {
  demand(typeof s === 'string' && s.length >= 1 && s.length <= limit && B64URL.test(s));
  let out;
  try { out = Uint8Array.from(atob(s.replace(/-/g,'+').replace(/_/g,'/') + '='.repeat((4-s.length%4)%4)),c=>c.charCodeAt(0)); }
  catch { throw Error('BLOCKED'); }
  const canonical = btoa(String.fromCharCode(...out)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
  demand(canonical === s);
  return out;
}
// Android Signature("SHA256withECDSA") emits DER; WebCrypto ECDSA expects raw 64-byte P1363.
function derToRaw(der) {
  demand(der.length >= 8 && der.length <= 72 && der[0] === 0x30 && der[1] === der.length-2);
  let offset = 2;
  const numbers = [];
  for(let j=0;j<2;j++) {
    demand(der[offset++] === 0x02);
    const size = der[offset++];
    demand(size >= 1 && size <= 33 && offset+size <= der.length);
    const bytes=der.slice(offset,offset+size);offset+=size;
    demand((bytes[0] & 0x80)===0); // Signed DER INTEGER must not be negative.
    demand(size===1 || bytes[0]!==0 || (bytes[1] & 0x80)!==0); // Minimal DER.
    if(size===33) demand(bytes[0]===0 && (bytes[1]&0x80)!==0);
    const value=(size===33?bytes.slice(1):bytes);
    demand(value.length<=32 && value.some(x=>x!==0));
    const padded=new Uint8Array(32);padded.set(value,32-value.length);
    numbers.push(padded);
  }
  demand(offset===der.length);
  const result=new Uint8Array(64);result.set(numbers[0]);result.set(numbers[1],32);
  return result;
}
function canonical(method,origin,path,rawBody,proofId,issuedAt) {
  demand(method==='POST' && ORIGIN.test(origin) && PATHS.has(path));
  demand(typeof rawBody==='string' && text.encode(rawBody).length <= 12000);
  demand(HEX32.test(proofId) && Number.isSafeInteger(issuedAt) && issuedAt>0);
  return shaHex(text.encode(rawBody)).then(digest=>text.encode(
    'APPFORGE-V9-STAGE5E4-HTTP\0'+method+'\n'+origin+'\n'+path+'\n'+digest+'\n'+proofId+'\n'+issuedAt+'\n'));
}
/** Read-only access to private record; registration requires separate authenticated provisioning.
 * Record schema: {status:'active',purpose:'admin-operator',principalId,spkiDerB64Url,expiresAt}.
 * Revoked, expired, missing, wrong-purpose or mismatching SPKI records fail closed.
 */
export function createV9RegisteredAdminProofVerifier({storage,expectedOrigin,clock=Date.now}={}) {
  demand(storage?.kv && typeof storage.kv.get==='function' && typeof storage.transactionSync==='function');
  demand(typeof expectedOrigin==='string' && ORIGIN.test(expectedOrigin));
  demand(typeof clock==='function');
  return async function authenticate({request,path,rawBody}={}) {
    // Rejection is intentionally a non-descriptive exception; gateway returns constant 403.
    demand(request instanceof Request && typeof rawBody==='string');
    const url=new URL(request.url);
    demand(request.method==='POST' && url.origin===expectedOrigin && path===url.pathname && PATHS.has(path));
    demand(url.search==='' && url.hash==='');
    const headers=request.headers;
    demand(!headers.has('cookie') && !headers.has('origin'));
    const principalId=headers.get('x-appforge-v9-principal');
    const proofId=headers.get('x-appforge-v9-proof-id');
    const issuedString=headers.get('x-appforge-v9-issued-at');
    const signatureString=headers.get('x-appforge-v9-signature');
    demand(typeof principalId==='string' && HEX64.test(principalId));
    demand(typeof proofId==='string' && HEX32.test(proofId));
    demand(typeof issuedString==='string' && /^[1-9][0-9]{11,14}$/.test(issuedString));
    const issuedAt=Number(issuedString), now=clock();
    demand(Number.isSafeInteger(issuedAt) && Number.isSafeInteger(now) && now>0 && Math.abs(issuedAt-now)<=30000);
    const spkiRecord=storage.kv.get('s5e5:admin-device:'+principalId);
    demand(spkiRecord && spkiRecord.status==='active' && spkiRecord.purpose==='admin-operator' &&
      spkiRecord.principalId===principalId && Number.isSafeInteger(spkiRecord.expiresAt) &&
      spkiRecord.expiresAt>now);
    const spki=fromB64(spkiRecord.spkiDerB64Url,320);
    demand(spki.length>=75 && spki.length<=120 && await shaHex(spki)===principalId);
    const der=fromB64(signatureString,200);
    const sig=derToRaw(der);
    const bytes=await canonical('POST',expectedOrigin,path,rawBody,proofId,issuedAt);
    let key,valid;
    try {
      key=await crypto.subtle.importKey('spki',spki,{name:'ECDSA',namedCurve:'P-256'},false,['verify']);
      valid=await crypto.subtle.verify({name:'ECDSA',hash:'SHA-256'},key,sig,bytes);
    }catch {throw Error('BLOCKED');}
    demand(valid===true);
    return {verified:true,principalId,proofId,issuedAt};
  };
}
