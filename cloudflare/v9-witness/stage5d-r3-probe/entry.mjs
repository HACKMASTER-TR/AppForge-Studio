import {DurableObject} from 'cloudflare:workers';
import {V9Stage5DProbeCore} from '../stage5d-probe/core.mjs';
import {runR3Scheduled} from './scheduled.mjs';

export class V9Stage5DR3DO extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.core = new V9Stage5DProbeCore(ctx.storage, crypto.randomUUID());
  }
  exercise() { return this.core.exercise(); }
  inspectRestart() { return this.core.inspectRestart(); }
  getPhase() { return this.ctx.storage.kv.get('stage5d:checkpoint')?.phase ?? 'empty'; }
  abortInstance() { this.ctx.abort('V9_STAGE5D_R3_EXPECTED_RESTART', {retryAlarm: false}); }
}

export default {
  fetch() {
    return new Response('R3 diagnostic is not public',
      {status: 503, headers: {'Cache-Control': 'no-store'}});
  },
  async scheduled(event, env) {
    return runR3Scheduled(event, env);
  },
};
