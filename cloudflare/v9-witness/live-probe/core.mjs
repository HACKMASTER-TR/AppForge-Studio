// V9 LIVE acceptance ONLY: ephemeral synthetic signer, real V9 WitnessLedger.
// Never exported as an Android service. No private key is persisted.
import {WitnessLedger} from '../ledger.mjs';
import {canonical,digest} from '../verify.mjs';
const enc=new TextEncoder();
const ROOT='APPFORGE-V9-PHASE3E-ROOT\0', RELEASE='APPFORGE-V9-PHASE3E-RELEASE\0';
const DEVICE_A='a'.repeat(64), DEVICE_B='b'.repeat(64);
const b64=x=>btoa(String.fromCharCode(...new Uint8Array(x)));
const stamp=x=>new Date(x).toISOString().replace('.000','');
export const PROBE_DEVICE_A=DEVICE_A;
export const PROBE_DEVICE_B=DEVICE_B;
function insist(v,msg){if(!v)throw Error('V9_PROBE_FAILED: '+msg)}
export async function makeFixture(now=Date.now()){
  const time=Math.floor(now/1000)*1000;
  const root=await crypto.subtle.generateKey('Ed25519',true,['sign','verify']);
  const release=await crypto.subtle.generateKey('Ed25519',true,['sign','verify']);
  const rd=await crypto.subtle.exportKey('spki',root.publicKey);
  const ld=await crypto.subtle.exportKey('spki',release.publicKey);
  const rootPin=await digest(rd), relPin=await digest(ld);
  const evidenceRaw=canonical({schema:1,stage:'OFFLINE_EVIDENCE_ONLY',snapshot:'20261018T000000Z',original_objects_verified:218,original_deb_objects_verified:214,ubuntu_inrelease_verified:4,signed_packages_indexes_verified:32,distinct_content_addressed_objects:248,distinct_content_addressed_bytes:650832480,appforge_ed25519_release_signature:'NOT_PRESENT',r2_upload_allowed:false,public_distribution_allowed:false,android_client_enabled:false,apt_ready:false,all_engines_ready:false});
  // Synthetic test evidence pin, NOT the production Phase3D evidence pin.
  const evidencePin=await digest(evidenceRaw);
  const issued_at=stamp(time-60000), expires_at=stamp(time+3600000);
  const rootPayload={schema:1,role:'appforge-v9-root',version:1,issued_at,expires_at,root_key:{keyid:rootPin,spki_der_b64:b64(rd)},release_keys:[{keyid:relPin,spki_der_b64:b64(ld)}],release_threshold:1};
  const relPayload={schema:1,role:'appforge-v9-release',root_version:1,sequence:1,snapshot:'20261018T000000Z',evidence_sha256:evidencePin,issued_at,expires_at,r2_upload_allowed:false,public_distribution_allowed:false,android_client_enabled:false};
  const sign=async (key,payload,domain)=>b64(await crypto.subtle.sign('Ed25519',key,enc.encode(domain+canonical(payload))));
  const rootRaw=canonical({payload:rootPayload,signatures:[{keyid:rootPin,sig_b64:await sign(root.privateKey,rootPayload,ROOT)}]});
  const releaseRaw=canonical({payload:relPayload,signatures:[{keyid:relPin,sig_b64:await sign(release.privateKey,relPayload,RELEASE)}]});
  return {trust:{bootstrapDer:b64(rd),bootstrapPin:rootPin,evidencePin},docs:{evidenceRaw,rootRaw,releaseRaw}};
}
export class LiveProbeCore{
  constructor(storage,boot){this.storage=storage;this.boot=boot;}
  ledger(trust){return new WitnessLedger(this.storage,trust);}
  async raceCommit({device,trust,docs}){
    insist(this.storage.kv.get('v9-probe:marker')===undefined,'probe already initialized');
    const ledger=this.ledger(trust);
    const request={device,...docs};
    const attempts=await Promise.allSettled([ledger.prepare(request),ledger.prepare(request)]);
    const winners=attempts.filter(x=>x.status==='fulfilled');
    insist(winners.length===1,'concurrent CAS failed');
    const done=ledger.finalize(device,winners[0].value.token);
    insist(done.committed?.release_sequence===1 && done.generation===2,'commit generation');
    this.storage.kv.put('v9-probe:marker',{device,trust,docs,boot:this.boot});
    return {generation:done.generation,boot:this.boot,oneWinner:true};
  }
  inspect(){
    const m=this.storage.kv.get('v9-probe:marker');
    insist(m && typeof m.boot==='string','missing persistent marker');
    const state=this.ledger(m.trust).snapshot(m.device);
    insist(state.committed?.release_sequence===1 && state.pending===null && state.generation===2,'persistent checkpoint');
    return {generation:state.generation,boot:this.boot,initialBoot:m.boot};
  }
  async verifyReplayBlocked(){
    const m=this.storage.kv.get('v9-probe:marker');
    insist(m,'missing marker');
    let rejected=false;
    try { await this.ledger(m.trust).prepare({device:m.device,...m.docs}); }
    catch(e){rejected=/replay\/rollback|signed release replay/.test(String(e));}
    insist(rejected,'old signed release accepted');
    return {replayRejected:true};
  }
}
