import test from 'node:test';
import assert from 'node:assert/strict';

import {
  evaluateAdvancedPlayIntegrity
} from '../src/play_integrity_policy.mjs';

test(
  'safe fully-evaluated integrity verdict permits critical action',
  () => {
    const result =
      evaluateAdvancedPlayIntegrity({
        deviceIntegrity: {
          deviceRecognitionVerdict: [
            'MEETS_DEVICE_INTEGRITY'
          ],
          recentDeviceActivity: {
            deviceActivityLevel:
              'LEVEL_1'
          },
          deviceAttributes: {
            sdkVersion: 37
          }
        },
        environmentDetails: {
          appAccessRiskVerdict: {
            appsDetected: []
          },
          playProtectVerdict:
            'NO_ISSUES'
        }
      });

    assert.equal(
      result.optionalVerdictsReady,
      true
    );

    assert.equal(
      result.criticalActionAllowed,
      true
    );
  }
);

test(
  'screen capture or remote control risk blocks critical action',
  () => {
    const result =
      evaluateAdvancedPlayIntegrity({
        deviceIntegrity: {
          deviceRecognitionVerdict: [
            'MEETS_DEVICE_INTEGRITY'
          ],
          recentDeviceActivity: {
            deviceActivityLevel:
              'LEVEL_1'
          }
        },
        environmentDetails: {
          appAccessRiskVerdict: {
            appsDetected: [
              'UNKNOWN_CAPTURING'
            ]
          },
          playProtectVerdict:
            'NO_ISSUES'
        }
      });

    assert.equal(
      result.appAccessRisk,
      true
    );

    assert.equal(
      result.criticalActionAllowed,
      false
    );
  }
);

test(
  'high Play Protect risk blocks critical action',
  () => {
    const result =
      evaluateAdvancedPlayIntegrity({
        deviceIntegrity: {
          deviceRecognitionVerdict: [
            'MEETS_DEVICE_INTEGRITY'
          ],
          recentDeviceActivity: {
            deviceActivityLevel:
              'LEVEL_1'
          }
        },
        environmentDetails: {
          appAccessRiskVerdict: {
            appsDetected: []
          },
          playProtectVerdict:
            'HIGH_RISK'
        }
      });

    assert.equal(
      result.criticalActionAllowed,
      false
    );
  }
);

test(
  'missing optional verdicts fail closed for critical actions',
  () => {
    const result =
      evaluateAdvancedPlayIntegrity({
        deviceIntegrity: {
          deviceRecognitionVerdict: [
            'MEETS_DEVICE_INTEGRITY'
          ]
        }
      });

    assert.equal(
      result.optionalVerdictsReady,
      false
    );

    assert.equal(
      result.criticalActionAllowed,
      false
    );
  }
);
