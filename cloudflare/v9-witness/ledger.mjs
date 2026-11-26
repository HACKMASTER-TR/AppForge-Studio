import {verifyTransition,block,canonical,digest} from './verify.mjs';

// Storage is a SQLite-backed Durable Object's synchronous kv API. No network entrypoints.
// This is staging infrastructure, not a production trust service or Android receipt issuer.
const DEVICE=/^[a-f0-9]{64}$/;
const TOKEN=/^[a-f0-9]{32}$/;
const MAX_GENERATION=Number.MAX_SAFE_INTEGER-2;
const key=d=>'device:'+d;
const spent=(device,s)=>'spent:'+device+':'+s;
const blank=()=>({generation:0,committed:null,pending:null});
function checkDevice(d){block(typeof d==='string'&&DEVICE.test(d),'device identity');}
function checkToken(t){block(typeof t==='string'&&TOKEN.test(t),'transaction token');}
function safeState(s){
  block(s&&Number.isSafeInteger(s.generation)&&s.generation>=0&&s.generation<=MAX_GENERATION,'generation');
  block(s.committed===null || s.committed && typeof s.committed==='object','committed state');
  block(s.pending===null || s.pending && typeof s.pending==='object','pending state');
}
export class WitnessLedger {
  constructor(storage,{bootstrapDer,bootstrapPin,evidencePin,clock=()=>Date.now(),token=()=>crypto.randomUUID().replaceAll('-','')}={}) {
    block(storage&&storage.kv&&typeof storage.transactionSync==='function','SQLite DO storage required');
    this.s=storage;this.trust={bootstrapDer,bootstrapPin,evidencePin};this.clock=clock;this.token=token;
  }
  snapshot(device){checkDevice(device);const s=this.s.kv.get(key(device))??blank();safeState(s);return structuredClone(s);}
  async prepare({device,evidenceRaw,rootRaw,releaseRaw}){
    checkDevice(device);
    const s=this.snapshot(device);
    block(s.pending===null,'pending transaction exists');
    block(s.committed!==null||s.generation===0,'bootstrap replay forbidden');
    // Verify the canonical root and release ourselves. Never trust a caller's 'verified' assertion.
    const candidate=await verifyTransition({evidenceRaw,rootRaw,releaseRaw,old:s.committed, ...this.trust,now:this.clock()});
    const canonicalCandidate=canonical(candidate);
    const releaseHash=await digest(releaseRaw);
    const transactionToken=this.token();checkToken(transactionToken);
    return this.s.transactionSync(()=>{
      const current=this.s.kv.get(key(device))??blank();safeState(current);
      block(current.generation===s.generation && canonical(current.committed)===canonical(s.committed),'compare-and-swap conflict');
      block(current.pending===null,'pending conflict');
      block(current.generation<MAX_GENERATION,'generation overflow');
      block(this.s.kv.get(spent(device,releaseHash))===undefined,'signed release replay');
      // One signed release is unique per enrolled device. No shared per-release floor.
      this.s.kv.put(spent(device,releaseHash),{device,generation:current.generation+1});
      this.s.kv.put(key(device),{generation:current.generation+1,committed:current.committed,
        pending:{token:transactionToken,prior:current.committed,next:JSON.parse(canonicalCandidate),releaseHash}});
      return {token:transactionToken,generation:current.generation+1,verified:true,
        // This is NOT a signed witness receipt and must not be trusted by Android.
        signedReceipt:false,distributionEnabled:false};
    });
  }
  settle(device,token,action){
    checkDevice(device);checkToken(token);block(['finalize','abort'].includes(action),'action');
    return this.s.transactionSync(()=>{
      const s=this.s.kv.get(key(device))??blank();safeState(s);
      block(s.pending!==null&&s.pending.token===token,'missing/stale pending transaction');
      block(s.generation<MAX_GENERATION,'generation overflow');
      const next={generation:s.generation+1,committed:action==='finalize'?s.pending.next:s.committed,pending:null};
      this.s.kv.put(key(device),next);
      return {generation:next.generation,committed:structuredClone(next.committed),signedReceipt:false};
    });
  }
  finalize(device,token){return this.settle(device,token,'finalize');}
  abort(device,token){return this.settle(device,token,'abort');}
}
