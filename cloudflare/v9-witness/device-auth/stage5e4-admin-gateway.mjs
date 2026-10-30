// V9 Stage 5E.4: SOURCE-ONLY protected transport gateway. Not installed into a Worker.
// authenticate MUST independently verify a device-bound, fresh per-request signature;
// rateLimit MUST use trusted server-side state. Neither can come from request JSON/headers.
// No CORS, no public route, no signer exposure. A missing dependency fails closed.
const HEX64=/^[0-9a-f]{64}$/;
const HEX32=/^[0-9a-f]{32}$/;
const JWT=/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;
const START='/v9/private/admin/session/begin';
const DECIDE='/v9/private/admin/device/decide';
const LIMIT=12000;
const MAX_SESSION_MS=180000;
const PROOF_SKEW_MS=30000;
const codec=new TextDecoder('utf-8',{fatal:true});
function demand(ok){if(!ok) throw Error('BLOCKED');}
function sameKeys(value,keys){
  return value && typeof value==='object' && !Array.isArray(value) &&
    Object.keys(value).length===keys.length && keys.every(k=>Object.hasOwn(value,k));
}
const reply=(status,payload)=>new Response(JSON.stringify(payload),{
  status,headers:{'Content-Type':'application/json; charset=utf-8',
    'Cache-Control':'no-store, private','Pragma':'no-cache',
    'X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer'}
});
async function boundedJson(req){
  const size=req.headers.get('content-length');
  demand(size===null || (/^\d+$/.test(size)&&Number(size)<=LIMIT));
  const reader=req.body?.getReader();demand(reader);
  let total=0;const parts=[];
  while(true){
    const {done,value}=await reader.read();if(done)break;
    total+=value.byteLength;
    if(total>LIMIT){await reader.cancel();throw Error('BLOCKED');}
    parts.push(value);
  }
  const bytes=new Uint8Array(total);let off=0;
  for(const p of parts){bytes.set(p,off);off+=p.byteLength;}
  const raw=codec.decode(bytes);
  let obj;try{obj=JSON.parse(raw);}catch{throw Error('BLOCKED');}
  demand(obj && typeof obj==='object' && !Array.isArray(obj));
  return {obj,raw};
}
/**
 * Internal adapter; do not attach this to any deployed Worker before proof verifier,
 * durable request-replay storage, rate limiter, owner key enrollment and audit review.
 * `authenticate({request,path,rawBody})` is a TRUSTED VERIFIER, not a header parser.
 * It must verify a P-256 request signature over method, origin, path, body digest, proofId
 * and issuedAt (see V9AdminProofWire.kt) using independently pinned public keys.
 */
export function createV9PrivateAdminGateway({service,storage,authenticate,rateLimit,expectedOrigin,clock=Date.now}={}) {
  demand(typeof service?.beginAdminSession==='function' && typeof service?.decideDevice==='function');
  demand(storage?.kv && typeof storage.transactionSync==='function');
  demand(typeof authenticate==='function' && typeof rateLimit==='function');
  demand(typeof clock==='function');
  demand(typeof expectedOrigin==='string' && /^https:\/\/[a-z0-9.-]+$/.test(expectedOrigin));
  return async function handle(req){
    try {
      demand(req instanceof Request);
      const url=new URL(req.url),path=url.pathname;
      demand(req.method==='POST' && [START,DECIDE].includes(path));
      demand(url.search==='' && url.hash==='' && url.origin===expectedOrigin);
      demand(!req.headers.has('origin') && !req.headers.has('cookie'));
      demand(req.headers.get('content-type')==='application/json');
      const {obj,raw}=await boundedJson(req);
      if(path===START)demand(sameKeys(obj,[]));
      else demand(sameKeys(obj,['sessionId','deviceId','action','idToken']));
      const now=clock();demand(Number.isSafeInteger(now)&&now>0);
      const principal=await authenticate({request:req,path,rawBody:raw});
      demand(principal?.verified===true && HEX64.test(principal.principalId) &&
        HEX32.test(principal.proofId) && Number.isSafeInteger(principal.issuedAt) &&
        Math.abs(now-principal.issuedAt)<=PROOF_SKEW_MS);
      demand(await rateLimit({principalId:principal.principalId,path,now})===true);
      const proofKey='s5e4:proof:'+principal.principalId+':'+principal.proofId;
      const reserve=()=>storage.transactionSync(()=>{
        demand(!storage.kv.get(proofKey));
        storage.kv.put(proofKey,{expiresAt:now+MAX_SESSION_MS});
      });
      if(path===START){
        reserve();
        const session=service.beginAdminSession();
        demand(sameKeys(session,['sessionId','nonce','expiresAt']) &&
          HEX32.test(session.sessionId) &&
          /^[A-Za-z0-9_-]{22,128}$/.test(session.nonce) &&
          Number.isSafeInteger(session.expiresAt) && session.expiresAt>now &&
          session.expiresAt-now<=MAX_SESSION_MS);
        storage.transactionSync(()=>{
          const key='s5e4:binding:'+session.sessionId;
          demand(!storage.kv.get(key));
          storage.kv.put(key,{principalId:principal.principalId,expiresAt:session.expiresAt,spent:false});
        });
        return reply(200,session);
      }
      demand(typeof obj.sessionId==='string' && HEX32.test(obj.sessionId) &&
        typeof obj.deviceId==='string' && HEX64.test(obj.deviceId) &&
        ['approve','reject','revoke'].includes(obj.action) &&
        typeof obj.idToken==='string' && obj.idToken.length>=50 &&
        obj.idToken.length<=8192 && JWT.test(obj.idToken));
      // Reservation and binding consumption are atomic, before network JWKS/signing.
      storage.transactionSync(()=>{
        demand(!storage.kv.get(proofKey));
        const key='s5e4:binding:'+obj.sessionId;
        const state=storage.kv.get(key);
        demand(state && state.principalId===principal.principalId &&
          state.expiresAt>now && !state.spent);
        storage.kv.put(proofKey,{expiresAt:now+MAX_SESSION_MS});
        storage.kv.put(key,{...state,spent:true});
      });
      const result=await service.decideDevice({
        sessionId:obj.sessionId,deviceId:obj.deviceId,
        action:obj.action,idToken:obj.idToken
      });
      demand(result && result.deviceId===obj.deviceId &&
        result.status===({approve:'active',reject:'rejected',revoke:'revoked'}[obj.action]));
      return reply(200,{deviceId:result.deviceId,status:result.status});
    }catch{
      // Never reflect token, auth failures, internal errors or session data.
      return reply(403,{error:'admin_request_denied'});
    }
  };
}
