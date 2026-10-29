import {DurableObject} from 'cloudflare:workers';
import {V9Stage5DProbeCore} from './core.mjs';
const TARGET='stage5d-disposable-private-do-20261028';
export class V9Stage5DLiveDO extends DurableObject {
  constructor(ctx,env){super(ctx,env);this.core=new V9Stage5DProbeCore(ctx.storage,crypto.randomUUID());}
  exercise(){return this.core.exercise();}
  inspectRestart(){return this.core.inspectRestart();}
  getPhase(){return this.ctx.storage.kv.get('stage5d:checkpoint')?.phase??'empty';}
  abortInstance(){this.ctx.abort('V9_STAGE5D_EXPECTED_RESTART',{retryAlarm:false});}
}
export default {
  fetch(){return new Response('V9 Stage5D test has no public API',{status:503,headers:{'Cache-Control':'no-store'}});},
  async scheduled(event,env){
    if(event.cron!=='* * * * *'||env.STAGE5D_TEST_MODE!=='SYNTHETIC_ONLY')throw Error('BLOCKED: test mode');
    const n=env.V9_STAGE5D_PROBE,stub=n.get(n.idFromName(TARGET));
    if(await stub.getPhase()==='empty'){
      await stub.exercise();
      console.log('V9_STAGE5D_INITIAL=PASS;ENROLL=PASS;OIDC=PASS;APPROVE=PASS;CHALLENGE=PASS;REVOKE=PASS');
      try{await stub.abortInstance();}catch{console.log('V9_STAGE5D_RESTART_REQUEST=ACKNOWLEDGED');}
      return;
    }
    await stub.inspectRestart();
    console.log('V9_STAGE5D_LIVE=PASS;OIDC=PASS;ENROLL=PASS;APPROVE=PASS;REVOKE=PASS;RESTART=PASS;PERSIST=PASS;DENY=PASS');
  }
};
