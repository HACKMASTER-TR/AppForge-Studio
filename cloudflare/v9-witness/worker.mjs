// Isolated, OFF by default: no HTTP RPC, public route, Android API or signing.
// Cloudflare Durable Object storage supplies atomic commit for the STAGING ledger.
import { DurableObject } from 'cloudflare:workers';
import { WitnessLedger } from './ledger.mjs';
import { handleHttp } from './http.mjs';

export class V9StagingWitness extends DurableObject {
  constructor(ctx,env){super(ctx,env);
    // Fail closed when a staging configuration is missing. Never read any signer private key.
    this.enabled=env.V9_STAGING_WITNESS_MODE==='OFFLINE_TEST_ONLY';
    this.store=this.enabled?new WitnessLedger(ctx.storage,{
      bootstrapDer:env.V9_STAGING_ROOT_SPKI_DER_B64,
      bootstrapPin:env.V9_STAGING_ROOT_PIN,
      // This is the fixed Phase3D digest, not caller-selectable.
    }):null;
  }
  async prepare(request){if(!this.enabled)throw new Error('BLOCKED: witness disabled');return this.store.prepare(request);}
  snapshot(device){if(!this.enabled)throw new Error('BLOCKED: witness disabled');return this.store.snapshot(device);}
  finalize(device,token){if(!this.enabled)throw new Error('BLOCKED: witness disabled');return this.store.finalize(device,token);}
  abort(device,token){if(!this.enabled)throw new Error('BLOCKED: witness disabled');return this.store.abort(device,token);}
}
export default {fetch:handleHttp};
