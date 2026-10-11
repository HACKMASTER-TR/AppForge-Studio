import test from 'node:test';
import assert from 'node:assert/strict';

import {
  decodeAndEvaluatePlayIntegrity,
  InvalidPlayIntegrityVerdict
} from '../src/play_integrity_decode.mjs';

const HASH =
  'A'.repeat(43);

const TOKEN =
  'T'.repeat(300);

const NOW =
  1_800_000_000_000;

function goodPayload() {
  return {
    tokenPayloadExternal: {
      requestDetails: {
        requestPackageName:
          'com.appforge.studio',

        requestHash:
          HASH,

        timestampMillis:
          String(NOW)
      },

      appIntegrity: {
        appRecognitionVerdict:
          'PLAY_RECOGNIZED',

        packageName:
          'com.appforge.studio',

        versionCode:
          '529'
      },

      deviceIntegrity: {
        deviceRecognitionVerdict: [
          'MEETS_DEVICE_INTEGRITY'
        ],

        recentDeviceActivity: {
          deviceActivityLevel:
            'LEVEL_1'
        },

        deviceAttributes: {
          sdkVersion:
            37
        },

        deviceRecall: {
          values: {
            bitFirst:
              false,

            bitSecond:
              true,

            bitThird:
              false
          }
        }
      },

      environmentDetails: {
        appAccessRiskVerdict: {
          appsDetected: []
        },

        playProtectVerdict:
          'NO_ISSUES'
      }
    }
  };
}

function deps(
  payload = goodPayload()
) {
  return {
    nowMs:
      () => NOW,

    getAccessToken:
      async () =>
        'ya29.mock-access-token',

    fetch:
      async (
        url,
        init
      ) => {
        assert.equal(
          url,
          'https://playintegrity.googleapis.com/v1/com.appforge.studio:decodeIntegrityToken'
        );

        assert.equal(
          init.method,
          'POST'
        );

        assert.match(
          init.headers.authorization,
          /^Bearer ya29\./
        );

        const body =
          JSON.parse(
            init.body
          );

        assert.equal(
          body.integrityToken,
          TOKEN
        );

        return new Response(
          JSON.stringify(
            payload
          ),
          {
            status:
              200,

            headers: {
              'content-type':
                'application/json'
            }
          }
        );
      }
  };
}

test(
  'Google-decoded recognized app and safe advanced verdicts pass',
  async () => {
    const result =
      await decodeAndEvaluatePlayIntegrity({
        integrityToken:
          TOKEN,

        expectedRequestHash:
          HASH,

        env:
          {},

        dependencies:
          deps()
      });

    assert.equal(
      result.requestVerified,
      true
    );

    assert.equal(
      result.playRecognized,
      true
    );

    assert.equal(
      result.criticalActionAllowed,
      true
    );

    assert.deepEqual(
      result.deviceRecall,
      {
        bitFirst:
          false,

        bitSecond:
          true,

        bitThird:
          false
      }
    );
  }
);

test(
  'decoded requestHash mismatch is rejected',
  async () => {
    const payload =
      goodPayload();

    payload
      .tokenPayloadExternal
      .requestDetails
      .requestHash =
        'B'.repeat(43);

    await assert.rejects(
      () =>
        decodeAndEvaluatePlayIntegrity({
          integrityToken:
            TOKEN,

          expectedRequestHash:
            HASH,

          env:
            {},

          dependencies:
            deps(payload)
        }),
      InvalidPlayIntegrityVerdict
    );
  }
);

test(
  'stale Google token timestamp is rejected',
  async () => {
    const payload =
      goodPayload();

    payload
      .tokenPayloadExternal
      .requestDetails
      .timestampMillis =
        String(
          NOW - 120_001
        );

    await assert.rejects(
      () =>
        decodeAndEvaluatePlayIntegrity({
          integrityToken:
            TOKEN,

          expectedRequestHash:
            HASH,

          env:
            {},

          dependencies:
            deps(payload)
        }),
      InvalidPlayIntegrityVerdict
    );
  }
);
