// R3 diagnostic orchestration; no public endpoint, no real credentials or signing keys.
// Only the caller's private Durable Object binding is used.
export const R3_LIVE_MARKER = 'V9_STAGE5D_R3_LIVE=PASS;OIDC=PASS;ENROLL=PASS;APPROVE=PASS;REVOKE=PASS;RESTART=PASS;PERSIST=PASS;DENY=PASS';
const INITIAL_MARKER = 'V9_STAGE5D_R3_INITIAL=PASS;ENROLL=PASS;OIDC=PASS;APPROVE=PASS;CHALLENGE=PASS;REVOKE=PASS';
const exactCron = '* * * * *';
const emit = (logger, message) => logger(message);
const fail = (tag) => { throw new Error('R3_BLOCKED_' + tag); };

export async function runR3Scheduled(event, env, logger = console.log) {
  let stage = 'mode-gate';
  try {
    if (event?.cron !== exactCron || env?.STAGE5D_TEST_MODE !== 'SYNTHETIC_ONLY' || env?.STAGE5D_R3_DIAGNOSTIC !== 'ENABLED')
      fail('SCHEDULE_OR_MODE');
    emit(logger, 'R3_SCHEDULED_RECEIVED=YES;MODE=SYNTHETIC_ONLY');
    stage = 'bind-do';
    const ns = env.V9_STAGE5D_R3_PROBE;
    if (!ns || typeof ns.idFromName !== 'function' || typeof ns.get !== 'function') fail('MISSING_PRIVATE_DO');
    const stub = ns.get(ns.idFromName('stage5d-r3-disposable-private-do-20261029'));
    stage = 'read-state';
    const phase = await stub.getPhase();
    if (phase === 'empty') {
      stage = 'exercise';
      await stub.exercise();
      emit(logger, INITIAL_MARKER);
      stage = 'request-restart';
      let abortRejected = false;
      try { await stub.abortInstance(); }
      catch { abortRejected = true; }
      if (!abortRejected) fail('ABORT_RETURNED_WITHOUT_RESTART_SIGNAL');
      emit(logger, 'R3_RESTART_REQUESTED=YES;CONFIRMATION=PENDING_SECOND_INVOCATION');
      return {phase: 'restart-requested', live: false};
    }
    if (phase !== 'revoked') fail('UNEXPECTED_STATE');
    stage = 'inspect-restart';
    const report = await stub.inspectRestart();
    if (report?.pass !== true) fail('RESTART_PERSISTENCE_NOT_VERIFIED');
    emit(logger, 'R3_RESTART_PERSISTENCE=PASS');
    emit(logger, R3_LIVE_MARKER);
    return {phase: 'verified', live: true};
  } catch (error) {
    // Do not log exception message, stack, object IDs, device IDs, tokens or response bodies.
    const safe = /^[a-z-]+$/.test(stage) ? stage : 'unknown';
    emit(logger, 'R3_SCHEDULED_FAILURE=YES;STAGE=' + safe + ';ERROR_CLASS=' +
      (error instanceof TypeError ? 'TypeError' : 'Error'));
    // Sanitize the propagated error too: platform-generated exception logs can be visible.
    throw new Error('R3_SCHEDULED_BLOCKED_AT_' + safe);
  }
}
