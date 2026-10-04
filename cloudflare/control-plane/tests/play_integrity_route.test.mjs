import test from 'node:test';
import assert from 'node:assert/strict';

import {
  computeIntegrityRequestHash,
  handlePlayIntegritySecurityRoute
} from '../src/play_integrity_route.mjs';

const NOW =
  1_800_000_000_000;

const configuredEnv = {
  PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER:
    '564043752274',

  PLAY_INTEGRITY_SERVICE_ACCOUNT_EMAIL:
    'integrity@example.iam.gserviceaccount.com',

  PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY:
    '-----BEGIN PRIVATE KEY-----\n' +
    'X'.repeat(200) +
    '\n-----END PRIVATE KEY-----',

  PLAY_INTEGRITY_SESSION_SECRET:
    's'.repeat(64)
};

test(
  'security config fails closed before secrets are configured',
  async () => {
    const response =
      await handlePlayIntegritySecurityRoute(
        new Request(
          'https://worker.test/api/security/config'
        ),
        {},
        {},
        '/api/security/config'
      );

    assert.equal(
      response.status,
      200
    );

    const data =
      await response.json();

    assert.equal(
      data.integrityEnabled,
      false
    );

    assert.equal(
      data.cloudProjectNumber,
      0
    );
  }
);

test(
  'attestation rejects missing server credentials',
  async () => {
    const response =
      await handlePlayIntegritySecurityRoute(
        new Request(
          'https://worker.test/api/security/attest',
          {
            method:
              'POST',

            body:
              '{}'
          }
        ),
        {},
        {},
        '/api/security/attest'
      );

    assert.equal(
      response.status,
      503
    );
  }
);

test(
  'attestation recomputes request binding before issuing session',
  async () => {
    const action =
      'pro_status';

    const nonce =
      'N'.repeat(32);

    const timestamp =
      NOW;

    const requestHash =
      await computeIntegrityRequestHash({
        action,
        nonce,
        timestamp
      });

    const response =
      await handlePlayIntegritySecurityRoute(
        new Request(
          'https://worker.test/api/security/attest',
          {
            method:
              'POST',

            headers: {
              'content-type':
                'application/json'
            },

            body:
              JSON.stringify({
                integrityToken:
                  'T'.repeat(300),

                requestHash,

                action,

                nonce,

                timestamp,

                localCertificateSha256:
                  'a'.repeat(64)
              })
          }
        ),
        configuredEnv,
        {
          nowMs:
            () => NOW,

          decodeAndEvaluatePlayIntegrity:
            async ({
              expectedRequestHash
            }) => {
              assert.equal(
                expectedRequestHash,
                requestHash
              );

              return {
                requestVerified:
                  true,

                playRecognized:
                  true,

                meetsDeviceIntegrity:
                  true,

                optionalVerdictsReady:
                  true,

                appAccessRisk:
                  false,

                playProtectVerdict:
                  'NO_ISSUES',

                activityLevel:
                  'LEVEL_1',

                sdkVersion:
                  37,

                deviceRecall:
                  null,

                criticalActionAllowed:
                  true
              };
            },

          issueIntegritySession:
            async () =>
              'v1.mock.signed'
        },
        '/api/security/attest'
      );

    assert.equal(
      response.status,
      200
    );

    const data =
      await response.json();

    assert.equal(
      data.verified,
      true
    );

    assert.equal(
      data.integritySession,
      'v1.mock.signed'
    );
  }
);
