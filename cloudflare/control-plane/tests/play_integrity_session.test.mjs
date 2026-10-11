import test from 'node:test';
import assert from 'node:assert/strict';

import {
  issueIntegritySession,
  verifyIntegritySession
} from '../src/play_integrity_session.mjs';

const env = {
  PLAY_INTEGRITY_SESSION_SECRET:
    's'.repeat(64)
};

test(
  'integrity session is signed, action-bound and expires',
  async () => {
    const now =
      1_800_000_000_000;

    const session =
      await issueIntegritySession({
        env,

        action:
          'pro_status',

        requestHash:
          'A'.repeat(43),

        evaluation: {
          criticalActionAllowed:
            true
        },

        nowMs:
          now
      });

    const verified =
      await verifyIntegritySession({
        env,

        session,

        expectedAction:
          'pro_status',

        nowMs:
          now + 1_000
      });

    assert.equal(
      verified?.action,
      'pro_status'
    );

    assert.equal(
      await verifyIntegritySession({
        env,

        session,

        expectedAction:
          'pro_activate',

        nowMs:
          now + 1_000
      }),
      null
    );

    assert.equal(
      await verifyIntegritySession({
        env,

        session,

        expectedAction:
          'pro_status',

        nowMs:
          now + 120_001
      }),
      null
    );
  }
);
