import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildServiceAccountClaims
} from '../src/google_service_account_oauth.mjs';

test(
  'Play Integrity service-account claims use exact Google scope and one-hour lifetime',
  () => {
    const now =
      1_700_000_000;

    const claims =
      buildServiceAccountClaims(
        'integrity@example.iam.gserviceaccount.com',
        now
      );

    assert.equal(
      claims.scope,
      'https://www.googleapis.com/auth/playintegrity'
    );

    assert.equal(
      claims.aud,
      'https://oauth2.googleapis.com/token'
    );

    assert.equal(
      claims.iat,
      now
    );

    assert.equal(
      claims.exp,
      now + 3600
    );
  }
);
