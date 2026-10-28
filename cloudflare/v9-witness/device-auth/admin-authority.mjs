// Stage 5C: internal OIDC -> signed Stage 5B admin permit adapter.
// NO HTTP, no auto-enrollment, no signing key provisioning and no production deploy.
import {demand,hex} from './protocol.mjs';
import {adminPermitMessage} from './registry.mjs';
import {verifyGoogleAdminIdToken} from './admin-oidc.mjs';
const ID=/^[a-f0-9]{64}$/;
const SESSION=/^[a-f0-9]{32}$/;
const b64url=b=>btoa(String.fromCharCode(...new Uint8Array(b))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
const rand=()=>hex(crypto.getRandomValues(new Uint8Array(16)));
const nonceKey=s=>'v9-admin:nonce:'+s;
export class V9AdminNonceStore {
  constructor(storage,{clock=()=>Date.now(),random=rand}={}) {
    demand(storage?.kv && typeof storage.transactionSync==='function','private SQLite Durable Object required');
    this.s=storage;this.clock=clock;this.random=random;
  }
  begin() {
    const sessionId=this.random(),nonce=b64url(crypto.getRandomValues(new Uint8Array(32))),now=this.clock();
    demand(SESSION.test(sessionId)&&Number.isSafeInteger(now)&&now>0,'admin session');
    return this.s.transactionSync(()=>{
      demand(!this.s.kv.get(nonceKey(sessionId)),'admin session collision');
      this.s.kv.put(nonceKey(sessionId),{nonce,expiresAt:now+180_000,spent:false});
      return {sessionId,nonce,expiresAt:now+180_000};
    });
  }
  inspect(sessionId) {
    demand(typeof sessionId==='string'&&SESSION.test(sessionId),'admin session');
    const v=this.s.kv.get(nonceKey(sessionId));
    demand(v&&!v.spent&&v.expiresAt>this.clock(),'admin nonce missing/expired');
    return v.nonce;
  }
  consume(sessionId,nonce) {
    demand(typeof sessionId==='string'&&SESSION.test(sessionId),'admin session');
    return this.s.transactionSync(()=>{
      const v=this.s.kv.get(nonceKey(sessionId));
      demand(v&&!v.spent&&v.expiresAt>this.clock()&&v.nonce===nonce,'admin nonce replay/mismatch');
      this.s.kv.put(nonceKey(sessionId),{...v,spent:true});
      return true;
    });
  }
}
export class V9GoogleAdminDecisionAuthority {
  #signingKey; #registry; #nonces; #settings; #getGoogleKeys; #clock;
  constructor({signingKey,registry,nonces,oauthClientId,allowedSubjects,getGoogleKeys,clock=()=>Date.now()}){
    demand(signingKey?.type==='private' && signingKey.algorithm?.name==='Ed25519' && signingKey.extractable===false,'nonexportable admin issuer signing key');
    demand(typeof registry?.snapshot==='function' && nonces instanceof V9AdminNonceStore,'trusted registry/nonce store');
    demand(typeof getGoogleKeys==='function','trusted Google key source');
    this.#signingKey=signingKey;this.#registry=registry;this.#nonces=nonces;
    this.#settings={oauthClientId,allowedSubjects:allowedSubjects instanceof Set ? new Set(allowedSubjects) : allowedSubjects};this.#getGoogleKeys=getGoogleKeys;this.#clock=clock;
  }
  // Only internal trusted service can call this; no public route or client access.
  async authorizeAndSign({idToken,sessionId,deviceId,action}){
    demand(typeof deviceId==='string' && ID.test(deviceId),'device');
    demand(['approve','reject','revoke'].includes(action),'administrator action');
    const nonce=this.#nonces.inspect(sessionId);
    const now=this.#clock(),trustedJwks=await this.#getGoogleKeys();
    const admin=await verifyGoogleAdminIdToken(idToken,{...this.#settings,nonce,now,trustedJwks});
    this.#nonces.consume(sessionId,nonce); // one-use *before* signing, even if later state is stale
    const state=this.#registry.snapshot({deviceId});
    demand(state && (action==='revoke'?state.status==='active':state.status==='pending'),'device not eligible');
    demand(state.revision===(action==='revoke'?1:0),'device state revision');
    const adminId=hex(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode('APPFORGE-V9-ADMIN-SUB\0'+admin.subject))));
    const payload={schema:1,action,deviceId,requestId:state.requestId,expectedRevision:state.revision,
      decisionId:rand(),adminId,issuedAt:now,expiresAt:now+60_000};
    const signed=await crypto.subtle.sign('Ed25519',this.#signingKey,adminPermitMessage(payload));
    return {payload,sigB64Url:b64url(signed)};
  }
}
