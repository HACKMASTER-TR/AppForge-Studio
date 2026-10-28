// Stage 5C: offline-reviewable Google ID-token verifier. NO public endpoint,
// privileged device operation, access token, Google OAuth client secret, or issuer key.
// trustedJwks MUST come only from the hardcoded Google HTTPS endpoint below.
import {demand} from './protocol.mjs';
const GOOGLE_KEYS_URL='https://www.googleapis.com/oauth2/v3/certs';
const EXPECTED_ISSUERS=['https://accounts.google.com','accounts.google.com'];
const ascii=/^[A-Za-z0-9_-]+$/;
const kidRe=/^[a-zA-Z0-9_-]{4,128}$/;
const enc=new TextEncoder();
function decodePart(s,max) {
  demand(typeof s==='string' && s.length>0 && s.length<=max && ascii.test(s),'JWT base64url syntax');
  let b;
  try {b=Uint8Array.from(atob(s.replace(/-/g,'+').replace(/_/g,'/')+'='.repeat((4-s.length%4)%4)),c=>c.charCodeAt(0));}
  catch {throw Error('BLOCKED: invalid JWT base64url');}
  const v=btoa(String.fromCharCode(...b)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
  demand(v===s,'noncanonical JWT encoding');
  return b;
}
function jsonPart(s,max) {
  let v;
  try {v=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(decodePart(s,max)));}
  catch {throw Error('BLOCKED: invalid JWT JSON');}
  demand(v && typeof v==='object' && !Array.isArray(v),'JWT JSON object');
  return v;
}
function keyShape(jwk){
  return jwk && typeof jwk==='object' && !Array.isArray(jwk) &&
    jwk.kty==='RSA' && jwk.alg==='RS256' && jwk.use==='sig' &&
    typeof jwk.kid==='string' && kidRe.test(jwk.kid) && jwk.e==='AQAB' &&
    typeof jwk.n==='string' && decodePart(jwk.n,800).length>=256 &&
    decodePart(jwk.n,800).length<=512 && !('d' in jwk);
}
// Do not pass caller-supplied URLs, TLS bypass or stale/partial JWKS values.
export async function fetchGooglePublicKeys(fetchImpl=fetch) {
  const res=await fetchImpl(GOOGLE_KEYS_URL,{method:'GET',redirect:'error',headers:{Accept:'application/json'},signal:AbortSignal.timeout(10000)});
  demand(res?.ok && res.status===200,'Google key endpoint unavailable');
  const size=Number(res.headers?.get('content-length'));
  demand(!Number.isFinite(size) || size<=60_000,'JWKS too large');
  const body=await res.text();demand(body.length<=60_000,'JWKS size');
  let jwks;try{jwks=JSON.parse(body);}catch{throw Error('BLOCKED: invalid Google keys');}
  demand(jwks && Array.isArray(jwks.keys) && jwks.keys.length>0 && jwks.keys.length<=20,'Google keys unavailable');
  // Use the provider's independently trusted HTTPS response; never a JWT header URL.
  return jwks;
}
export async function verifyGoogleAdminIdToken(token,{oauthClientId,allowedSubjects,nonce,now=Date.now(),trustedJwks}){
  demand(typeof oauthClientId==='string' && /^[0-9]+-[a-z0-9-]+\.apps\.googleusercontent\.com$/.test(oauthClientId),'trusted OIDC audience');
  demand(allowedSubjects instanceof Set && allowedSubjects.size>0 && [...allowedSubjects].every(x=>typeof x==='string'&&/^[A-Za-z0-9._-]{5,255}$/.test(x)),'trusted admin subject allowlist');
  demand(typeof nonce==='string' && ascii.test(nonce) && nonce.length>=22 && nonce.length<=128,'expected server session nonce');
  demand(Number.isSafeInteger(now)&&now>0,'trusted server clock');
  demand(typeof token==='string'&&token.length<=8192,'JWT size');
  const parts=token.split('.');demand(parts.length===3,'JWT compact structure');
  const header=jsonPart(parts[0],1024),claims=jsonPart(parts[1],8192);
  demand(header.alg==='RS256' && typeof header.kid==='string' && kidRe.test(header.kid),'JWT algorithm/key ID');
  demand(header.typ===undefined||header.typ==='JWT','JWT token type');
  demand(Object.keys(header).every(k=>['alg','typ','kid'].includes(k)),'JWT unsafe header extension');
  demand(trustedJwks && Array.isArray(trustedJwks.keys) && trustedJwks.keys.length>0 && trustedJwks.keys.length<=20,'trusted Google JWKS unavailable');
  const keys=trustedJwks.keys.filter(k=>k?.kid===header.kid);
  demand(keys.length===1 && keyShape(keys[0]),'Google signing key untrusted');
  const jwk=keys[0];
  let key;try {key=await crypto.subtle.importKey('jwk',jwk,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['verify']);}
  catch {throw Error('BLOCKED: Google key import failure');}
  const ok=await crypto.subtle.verify({name:'RSASSA-PKCS1-v1_5'},key,decodePart(parts[2],1024),enc.encode(parts[0]+'.'+parts[1]));
  demand(ok,'Google ID-token signature');
  const s=Math.floor(now/1000);
  demand(EXPECTED_ISSUERS.includes(claims.iss),'Google issuer');
  demand(claims.aud===oauthClientId && (claims.azp===undefined||claims.azp===oauthClientId),'OIDC audience/authorized presenter');
  demand(typeof claims.sub==='string' && allowedSubjects.has(claims.sub),'administrator subject not allowlisted');
  demand(claims.nonce===nonce,'OIDC nonce mismatch');
  demand(Number.isSafeInteger(claims.iat) && Number.isSafeInteger(claims.exp) && claims.iat<=s+10 &&
    claims.exp>s && claims.exp>claims.iat && claims.exp-claims.iat<=3600,'OIDC issued/expiry time');
  demand(claims.nbf===undefined||(Number.isSafeInteger(claims.nbf)&&claims.nbf<=s),'OIDC not-before time');
  return {subject:claims.sub,issuedAt:claims.iat,expiresAt:claims.exp};
}
