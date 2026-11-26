// Internal-only Durable Object challenge repository. NO enrollment endpoint.
// A trusted admin process must provision and approve the device key separately.
import {demand, exact,verifyDeviceProof, OPERATIONS} from './protocol.mjs';
const ID=/^[0-9a-f]{64}$/, HEX32=/^[0-9a-f]{32}$/;
const lifetimeMs=90_000;
const registered=d=>'v9-auth:registered:'+d;
const pending=d=>'v9-auth:pending:'+d;
const bytes32=()=>Array.from(crypto.getRandomValues(new Uint8Array(16)),x=>x.toString(16).padStart(2,'0')).join('');
export class V9DeviceChallengeStore {
  constructor(storage,{clock=()=>Date.now(),random=bytes32}={}) {
    demand(storage?.kv && typeof storage.transactionSync==='function','SQLite Durable Object required');
    this.s=storage;this.clock=clock;this.random=random;
  }
  // Enrollment is NOT implemented here. Only a separately authenticated admin
  // service may write registration state after physical/out-of-band approval.
  issue({deviceId,operation}) {
    demand(typeof deviceId==='string'&&ID.test(deviceId),'device id');
    demand(OPERATIONS.includes(operation) && operation!=='enroll','operation');
    const now=this.clock();demand(Number.isSafeInteger(now)&&now>0,'clock');
    const challengeId=this.random(),nonce=this.random();
    demand(HEX32.test(challengeId)&&HEX32.test(nonce)&&challengeId!==nonce,'random challenge');
    return this.s.transactionSync(()=>{
      const reg=this.s.kv.get(registered(deviceId));
      demand(reg && reg.deviceId===deviceId && reg.spkiPin===deviceId && reg.status==='active','device not approved');
      const old=this.s.kv.get(pending(deviceId));
      demand(!old || old.expiresAt<=now || old.spent===true,'existing challenge');
      const record={deviceId,operation,challengeId,nonce,spkiPin:reg.spkiPin,issuedAt:now,expiresAt:now+lifetimeMs,spent:false};
      this.s.kv.put(pending(deviceId),record);
      return {schema:1,operation,deviceId,challengeId,nonce,expiresAt:record.expiresAt};
    });
  }
  async verifyAndConsume(proof) {
    demand(proof && typeof proof==='object','proof');
    const deviceId=proof.deviceId;
    demand(typeof deviceId==='string'&&ID.test(deviceId),'device id');
    const snapshot=this.s.kv.get(pending(deviceId));
    const reg=this.s.kv.get(registered(deviceId));
    const now=this.clock();
    demand(Number.isSafeInteger(now)&&now>0,'clock');
    demand(reg?.status==='active' && reg.deviceId===deviceId && reg.spkiPin===deviceId,'device revoked');
    demand(snapshot && !snapshot.spent && snapshot.expiresAt>now && snapshot.issuedAt<=now,'challenge expired/spent');
    const checked=await verifyDeviceProof(proof,{
      deviceId:snapshot.deviceId,operation:snapshot.operation,challengeId:snapshot.challengeId,
      nonce:snapshot.nonce,spkiPin:reg.spkiPin
    });
    return this.s.transactionSync(()=>{
      const fresh=this.s.kv.get(pending(deviceId));
      const currentReg=this.s.kv.get(registered(deviceId));
      demand(currentReg?.status==='active' && currentReg.deviceId===deviceId && currentReg.spkiPin===deviceId,'device revoked during verification');
      demand(fresh && !fresh.spent && fresh.expiresAt>this.clock() &&
        fresh.challengeId===snapshot.challengeId && fresh.nonce===snapshot.nonce &&
        fresh.operation===snapshot.operation,'challenge replay/race');
      this.s.kv.put(pending(deviceId),{...fresh,spent:true});
      return {deviceId:checked.deviceId,operation:fresh.operation,authenticated:true,challengeConsumed:true};
    });
  }
}
