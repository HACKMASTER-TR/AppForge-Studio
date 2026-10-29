// Synthetic offline/live integration fixture: no real Google account or production keys.
import {V9PrivateDeviceAdminService} from '../device-auth/stage5d-private.mjs';
import {proofMessage,strictBase64Url} from '../device-auth/protocol.mjs';
const enc=new TextEncoder();
const b64=x=>btoa(String.fromCharCode(...new Uint8Array(x))).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,'');
const hex=x=>Array.from(new Uint8Array(x),b=>b.toString(16).padStart(2,'0')).join('');
const insist=(v,m)=>{if(!v)throw Error('V9_STAGE5D_FAILED:'+m)};
export const ADMIN_SUB='stage5d-synthetic-google-sub';
export const CLIENT_ID='123456789012-stage5d-test.apps.googleusercontent.com';
async function fixtureJwt(key,nonce,now){
  const pub=await crypto.subtle.exportKey('jwk',key.publicKey);
  const jwk={...pub,alg:'RS256',use:'sig',kid:'stage5d-issuer-test'};
  const h=b64(enc.encode(JSON.stringify({alg:'RS256',kid:jwk.kid,typ:'JWT'})));
  const p=b64(enc.encode(JSON.stringify({iss:'https://accounts.google.com',aud:CLIENT_ID,
    sub:ADMIN_SUB,nonce,iat:Math.floor(now/1000)-1,exp:Math.floor(now/1000)+60})));
  const signature=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key.privateKey,enc.encode(h+'.'+p));
  return {token:h+'.'+p+'.'+b64(signature), jwks:{keys:[jwk]}};
}
const deviceKey=()=>crypto.subtle.generateKey({name:'ECDSA',namedCurve:'P-256'},true,['sign','verify']);
const googleKey=()=>crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
const adminKey=()=>crypto.subtle.generateKey({name:'Ed25519'},false,['sign','verify']);
async function makeDeviceProof(pair,challenge,spki){
  const msg=proofMessage(challenge);
  // WebCrypto ECDSA output is raw P1363; Android API uses canonical DER.
  const raw=new Uint8Array(await crypto.subtle.sign({name:'ECDSA',hash:'SHA-256'},pair.privateKey,msg));
  const derInt=s=>{let i=0;while(i<s.length-1&&s[i]===0)i++;let a=s.slice(i);if(a[0]&128)a=Uint8Array.from([0,...a]);return Uint8Array.from([2,a.length,...a]);};
  const r=derInt(raw.slice(0,32)),s=derInt(raw.slice(32));const der=Uint8Array.from([48,r.length+s.length,...r,...s]);
  return {schema:1,operation:challenge.operation,deviceId:challenge.deviceId,
    challengeId:challenge.challengeId,nonce:challenge.nonce,spkiB64Url:b64(spki),signatureDerB64Url:b64(der)};
}
export class V9Stage5DProbeCore {
  constructor(storage,boot,clock=()=>Date.now()){this.storage=storage;this.boot=boot;this.clock=clock;}
  async exercise() {
    insist(!this.storage.kv.get('stage5d:checkpoint'),'probe already initialized');
    const pair=await deviceKey(),ga=await googleKey(),aa=await adminKey();
    const spki=new Uint8Array(await crypto.subtle.exportKey('spki',pair.publicKey));
    const adminSpki=new Uint8Array(await crypto.subtle.exportKey('spki',aa.publicKey));
    const adminPin=hex(await crypto.subtle.digest('SHA-256',adminSpki));
    const pubFixture=await fixtureJwt(ga,'placeholder',this.clock());
    const service=new V9PrivateDeviceAdminService(this.storage,{adminSpkiDer:adminSpki,adminSpkiPin:adminPin,
      signingKey:aa.privateKey,oauthClientId:CLIENT_ID,allowedSubjects:new Set([ADMIN_SUB]),
      getGoogleKeys:async()=>pubFixture.jwks,clock:this.clock});
    const enrollment=await service.beginDeviceEnrollment({spkiB64Url:b64(spki)});
    const p=await makeDeviceProof(pair,enrollment,spki);
    const req=await service.submitDeviceEnrollment(p);
    insist(req.status==='pending'&&service.deviceStatus({deviceId:req.deviceId}).status==='pending','no pending state');
    const session=service.beginAdminSession(),jwt=await fixtureJwt(ga,session.nonce,this.clock());
    const approved=await service.decideDevice({idToken:jwt.token,sessionId:session.sessionId,deviceId:req.deviceId,action:'approve'});
    insist(approved.status==='active','not approved');
    const actionChallenge=service.beginAuthorizedChallenge({deviceId:req.deviceId,operation:'receipt'});
    const actionProof=await makeDeviceProof(pair,actionChallenge,spki);
    const accepted=await service.verifyAuthorizedProof(actionProof);
    insist(accepted.authenticated===true,'authorized device proof failed');
    let replayDenied=false;try{await service.verifyAuthorizedProof(actionProof);}catch{replayDenied=true;}
    insist(replayDenied,'consumed challenge replay accepted');
    const revokedSession=service.beginAdminSession(),revJwt=await fixtureJwt(ga,revokedSession.nonce,this.clock());
    const revoked=await service.decideDevice({idToken:revJwt.token,sessionId:revokedSession.sessionId,deviceId:req.deviceId,action:'revoke'});
    insist(revoked.status==='revoked','revocation failed');
    let blocked=false;try{service.beginAuthorizedChallenge({deviceId:req.deviceId,operation:'receipt'});}catch{blocked=true;}
    insist(blocked,'revoked device challenge issued');
    this.storage.kv.put('stage5d:checkpoint',{deviceId:req.deviceId,boot:this.boot,
      adminSpkiB64:b64(adminSpki),adminPin,phase:'revoked',expectedRevision:2,nonceSession:revokedSession.sessionId});
    return {phase:'revoked',deviceId:req.deviceId};
  }
  async inspectRestart() {
    const old=this.storage.kv.get('stage5d:checkpoint');insist(old && old.phase==='revoked','lost checkpoint');
    insist(old.boot!==this.boot,'same DO isolate: restart not observed');
    // Verify persisted state using the independently pinned synthetic public authority.
    const aa=new Uint8Array(strictBase64Url(old.adminSpkiB64,[40,120]));
    const {V9DeviceEnrollmentRegistry}=await import('../device-auth/registry.mjs');
    const registry=new V9DeviceEnrollmentRegistry(this.storage,{adminSpkiDer:aa,adminSpkiPin:old.adminPin,clock:this.clock});
    const snap=registry.snapshot({deviceId:old.deviceId});
    insist(snap?.status==='revoked' && snap.revision===2,'revoked state lost');
    const {V9DeviceChallengeStore}=await import('../device-auth/challenges.mjs');
    let blocked=false;try{new V9DeviceChallengeStore(this.storage,{clock:this.clock}).issue({deviceId:old.deviceId,operation:'receipt'});}catch{blocked=true;}
    insist(blocked,'revocation lost after restart');
    return {pass:true,boot:this.boot,previousBoot:old.boot};
  }
}
