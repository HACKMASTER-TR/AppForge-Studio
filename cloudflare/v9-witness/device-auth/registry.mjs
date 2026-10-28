// V9 Stage 5B — PRIVATE Durable Object registry model, NOT an HTTP endpoint.
// Admin permits MUST come from a separately authenticated, audited issuer.
// This module checks the Ed25519 signature of that issuer, not its OIDC policy.
// NEVER accept a public key, isAdmin flag, bearer token or registration record
// from the device as authority. No production or existing Worker integrations.
import {demand, exact, hex, strictBase64Url, verifyDeviceProof} from './protocol.mjs';

const ID=/^[0-9a-f]{64}$/;
const HEX32=/^[0-9a-f]{32}$/;
const DOMAIN=new TextEncoder().encode('APPFORGE-V9-ADMIN-DEVICE-DECISION\0');
const TTL=90_000, PERMIT_TTL=120_000;
const AUTH_ACTIONS=Object.freeze(['approve','reject','revoke']);
const enState=id=>'v9-enroll:state:'+id;
const enChallenge=id=>'v9-enroll:challenge:'+id;
const registration=id=>'v9-auth:registered:'+id; // Stage5A consumption contract
const spent=id=>'v9-enroll:spent-admin-decision:'+id;
const random32=()=>hex(crypto.getRandomValues(new Uint8Array(16)));
const canon=o=>JSON.stringify(Object.fromEntries(Object.keys(o).sort().map(k=>[k,o[k]])))+'\n';
const nowOk=now=>Number.isSafeInteger(now)&&now>0;

export const adminPermitMessage=payload=>{
  exact(payload,['schema','action','deviceId','requestId','expectedRevision','decisionId','adminId','issuedAt','expiresAt'],'admin permit payload');
  demand(payload.schema===1 && AUTH_ACTIONS.includes(payload.action),'permit operation');
  demand(typeof payload.deviceId==='string'&&ID.test(payload.deviceId),'permit device');
  demand(typeof payload.requestId==='string'&&HEX32.test(payload.requestId),'permit request');
  demand(typeof payload.decisionId==='string'&&HEX32.test(payload.decisionId),'permit decision id');
  demand(typeof payload.adminId==='string'&&ID.test(payload.adminId),'permit administrator id');
  demand(Number.isSafeInteger(payload.expectedRevision)&&payload.expectedRevision>=0&&payload.expectedRevision<=2,'permit revision');
  demand(nowOk(payload.issuedAt)&&nowOk(payload.expiresAt),'permit timestamp');
  return new Uint8Array([...DOMAIN,...new TextEncoder().encode(canon(payload))]);
};

// Pinned public key is trusted at construction from an independent configuration,
// never copied from an untrusted permit. No private admin signing key is present.
export class V9DeviceEnrollmentRegistry {
  constructor(storage,{adminSpkiDer,adminSpkiPin,clock=()=>Date.now()}={}) {
    demand(storage?.kv&&typeof storage.transactionSync==='function','SQLite Durable Object required');
    demand(adminSpkiDer instanceof Uint8Array&&adminSpkiDer.length>=40&&adminSpkiDer.length<=120,'pinned admin public key required');
    demand(typeof adminSpkiPin==='string'&&ID.test(adminSpkiPin),'pinned admin key digest required');
    demand(typeof clock==='function','server clock required');
    this.s=storage;this.adminSpkiDer=Uint8Array.from(adminSpkiDer);this.adminSpkiPin=adminSpkiPin;
    this.clock=clock;this.random=random32;
  }
  // Untrusted device supplies only a public key. Public access/rate limiting are
  // deliberately out of scope; callers must be private, authorized services.
  async issueEnrollmentChallenge({spkiB64Url}) {
    const der=strictBase64Url(spkiB64Url,[70,110]);
    let pub;
    try { pub=await crypto.subtle.importKey('spki',der,{name:'ECDSA',namedCurve:'P-256'},false,['verify']); }
    catch { throw new Error('BLOCKED: invalid enrollment public key'); }
    demand(pub?.type==='public','enrollment public key type');
    const deviceId=hex(new Uint8Array(await crypto.subtle.digest('SHA-256',der)));
    const now=this.clock();demand(nowOk(now),'server time');
    const challengeId=this.random(),nonce=this.random();
    demand(HEX32.test(challengeId)&&HEX32.test(nonce)&&challengeId!==nonce,'random challenge');
    return this.s.transactionSync(()=>{
      demand(!this.s.kv.get(enState(deviceId))&&!this.s.kv.get(registration(deviceId)),'device previously enrolled or retired');
      const old=this.s.kv.get(enChallenge(deviceId));
      demand(!old||old.expiresAt<=now,'enrollment challenge already pending');
      this.s.kv.put(enChallenge(deviceId),{deviceId,spkiPin:deviceId,challengeId,nonce,issuedAt:now,expiresAt:now+TTL,spent:false});
      return {schema:1,operation:'enroll',deviceId,challengeId,nonce,expiresAt:now+TTL};
    });
  }
  async requestEnrollment(proof) {
    demand(proof&&typeof proof==='object'&&typeof proof.deviceId==='string'&&ID.test(proof.deviceId),'enrollment proof/device');
    const c=this.s.kv.get(enChallenge(proof.deviceId));
    const now=this.clock();demand(nowOk(now),'server time');
    demand(c&&!c.spent&&c.expiresAt>now&&c.issuedAt<=now,'enrollment challenge expired/spent');
    const verified=await verifyDeviceProof(proof,{deviceId:c.deviceId,operation:'enroll',challengeId:c.challengeId,nonce:c.nonce,spkiPin:c.spkiPin});
    const requestId=this.random();demand(HEX32.test(requestId),'request identifier');
    return this.s.transactionSync(()=>{
      const latest=this.s.kv.get(enChallenge(verified.deviceId));
      demand(latest&&!latest.spent&&latest.expiresAt>this.clock()&&latest.issuedAt<=this.clock()&&
        latest.challengeId===c.challengeId&&latest.nonce===c.nonce,'enrollment race/replay');
      demand(!this.s.kv.get(enState(verified.deviceId))&&!this.s.kv.get(registration(verified.deviceId)),'device registration already exists');
      this.s.kv.put(enChallenge(verified.deviceId),{...latest,spent:true});
      this.s.kv.put(enState(verified.deviceId),{deviceId:verified.deviceId,spkiPin:verified.deviceId,
        status:'pending',requestId,revision:0,requestedAt:this.clock()});
      return {deviceId:verified.deviceId,requestId,status:'pending',revision:0,approved:false};
    });
  }
  async #verifyPermit(permit) {
    exact(permit,['payload','sigB64Url'],'signed admin permit');
    const p=permit.payload, message=adminPermitMessage(p);
    const now=this.clock();demand(nowOk(now),'server time');
    demand(p.issuedAt<=now+10_000&&p.expiresAt>now&&p.expiresAt>p.issuedAt&&
      p.expiresAt-p.issuedAt<=PERMIT_TTL,'admin permit expired/future');
    const expectedPin=hex(new Uint8Array(await crypto.subtle.digest('SHA-256',this.adminSpkiDer)));
    demand(expectedPin===this.adminSpkiPin,'admin authority pin mismatch');
    let key;
    try {key=await crypto.subtle.importKey('spki',this.adminSpkiDer,{name:'Ed25519'},false,['verify']);}
    catch {throw new Error('BLOCKED: invalid pinned administrator authority');}
    const sig=strictBase64Url(permit.sigB64Url,[64,64]);
    const valid=await crypto.subtle.verify('Ed25519',key,sig,message);
    demand(valid,'administrator authorization signature');
    return p;
  }
  // This is not an admin HTTP/RPC endpoint. The signing key and its admin OIDC
  // authorization policy MUST be isolated from untrusted device callers.
  async applyAdminPermit(permit) {
    const p=await this.#verifyPermit(permit);
    return this.s.transactionSync(()=>{
      demand(!this.s.kv.get(spent(p.decisionId)),'admin decision replay');
      const old=this.s.kv.get(enState(p.deviceId));
      demand(old&&old.deviceId===p.deviceId&&old.spkiPin===p.deviceId&&
        old.requestId===p.requestId&&old.revision===p.expectedRevision,'stale/mismatched device request');
      if(p.action==='approve'||p.action==='reject') {
        demand(old.status==='pending'&&old.revision===0,'not pending');
        demand(!this.s.kv.get(registration(p.deviceId)),'previous registration exists');
      } else {
        const reg=this.s.kv.get(registration(p.deviceId));
        demand(old.status==='active'&&old.revision===1&&reg?.status==='active'&&
          reg.deviceId===p.deviceId&&reg.spkiPin===p.deviceId,'not an active device');
      }
      const nextStatus={approve:'active',reject:'rejected',revoke:'revoked'}[p.action];
      const next={...old,status:nextStatus,revision:old.revision+1,decidedBy:p.adminId,decisionId:p.decisionId,decidedAt:this.clock()};
      this.s.kv.put(enState(p.deviceId),next);
      if(p.action==='approve') this.s.kv.put(registration(p.deviceId),{deviceId:p.deviceId,spkiPin:p.deviceId,status:'active',revision:1});
      if(p.action==='revoke') this.s.kv.put(registration(p.deviceId),{deviceId:p.deviceId,spkiPin:p.deviceId,status:'revoked',revision:2});
      this.s.kv.put(spent(p.decisionId),{deviceId:p.deviceId,action:p.action});
      return {deviceId:p.deviceId,status:nextStatus,revision:next.revision,approved:nextStatus==='active'};
    });
  }
  // Minimal unprivileged view; audit information lives in private DO state.
  snapshot({deviceId}) {
    demand(typeof deviceId==='string'&&ID.test(deviceId),'device identifier');
    const s=this.s.kv.get(enState(deviceId));
    return s?{deviceId,status:s.status,requestId:s.requestId,revision:s.revision}:null;
  }
}
