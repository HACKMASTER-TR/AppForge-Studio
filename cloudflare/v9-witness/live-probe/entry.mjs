// Private, ephemeral V9 live storage acceptance. No routes, no web endpoints.
import {DurableObject} from 'cloudflare:workers';
import {LiveProbeCore,makeFixture,PROBE_DEVICE_A} from './core.mjs';
const TARGET='v9-live-do-acceptance-oct28-2026';
const CRON='* * * * *';
function insist(v,m){if(!v)throw Error('V9_PROBE_FAILED: '+m)}
export class V9LiveProbeDO extends DurableObject{
  constructor(ctx,env){super(ctx,env);this.core=new LiveProbeCore(ctx.storage,crypto.randomUUID());}
  async raceCommit(data){return this.core.raceCommit(data);}
  inspect(){return this.core.inspect();}
  async replayBlocked(){return this.core.verifyReplayBlocked();}
  resetInstance(){this.ctx.abort('V9_LIVE_DO_EXPECTED_RESTART',{retryAlarm:false});}
}
export default {
  fetch(){return new Response('V9 live probe has no public API',{status:503,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});},
  async scheduled(event,env){
    insist(event.cron===CRON,'unexpected cron');
    insist(env.V9_PROBE_MODE==='LIVE_ACCEPTANCE_ONLY','mode disabled');
    const ns=env.V9_LIVE_PROBE;
    const stub=ns.get(ns.idFromName(TARGET));
    const current=await stub.inspect().catch(()=>null);
    if(current){
      insist(current.generation===2,'persistent checkpoint missing');
      insist(current.boot!==current.initialBoot,'forced restart not observed');
      const oldRejected=await stub.replayBlocked();
      insist(oldRejected.replayRejected===true,'replay accepted');
      console.log('V9_LIVE_DO_ACCEPTANCE=PASS;CAS=PASS;RESTART=PASS;PERSISTENCE=PASS;REPLAY=PASS;GENERATION=2');
      return;
    }
    const fixture=await makeFixture();
    const result=await stub.raceCommit({device:PROBE_DEVICE_A,...fixture});
    insist(result.oneWinner && result.generation===2,'race commit');
    console.log('V9_LIVE_DO_INIT=PASS;CAS=PASS;GENERATION=2');
    // The following call is intentionally expected to throw. This forces an
    // actual DO isolate reset; the next Cron invocation checks durable state.
    try {await stub.resetInstance();}catch(e){console.log('V9_LIVE_DO_RESET_REQUEST=ACKNOWLEDGED');}
  }
};
