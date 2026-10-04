const riskyAccess =
  value =>
    typeof value === 'string' &&
    (
      value.endsWith('_CAPTURING') ||
      value.endsWith('_CONTROLLING') ||
      value.endsWith('_OVERLAYS')
    );

export function evaluateAdvancedPlayIntegrity(
  decodedVerdict
) {
  const payload =
    decodedVerdict &&
    typeof decodedVerdict === 'object'
      ? decodedVerdict
      : {};

  const device =
    payload.deviceIntegrity &&
    typeof payload.deviceIntegrity === 'object'
      ? payload.deviceIntegrity
      : {};

  const environment =
    payload.environmentDetails &&
    typeof payload.environmentDetails === 'object'
      ? payload.environmentDetails
      : {};

  const recognition =
    Array.isArray(
      device.deviceRecognitionVerdict
    )
      ? device.deviceRecognitionVerdict
      : [];

  const meetsDeviceIntegrity =
    recognition.includes(
      'MEETS_DEVICE_INTEGRITY'
    );

  const recentActivity =
    device.recentDeviceActivity &&
    typeof device.recentDeviceActivity === 'object'
      ? device.recentDeviceActivity
      : {};

  const activityLevel =
    typeof recentActivity.deviceActivityLevel === 'string'
      ? recentActivity.deviceActivityLevel
      : 'UNEVALUATED';

  const activityEvaluated =
    activityLevel !== 'UNEVALUATED';

  const activityRisk =
    activityLevel === 'LEVEL_4';

  const access =
    environment.appAccessRiskVerdict &&
    typeof environment.appAccessRiskVerdict === 'object'
      ? environment.appAccessRiskVerdict
      : null;

  const accessDetected =
    access &&
    Array.isArray(
      access.appsDetected
    )
      ? access.appsDetected
      : [];

  const accessEvaluated =
    access !== null &&
    Array.isArray(
      access.appsDetected
    );

  const appAccessRisk =
    accessDetected.some(
      riskyAccess
    );

  const playProtectVerdict =
    typeof environment.playProtectVerdict === 'string'
      ? environment.playProtectVerdict
      : 'UNEVALUATED';

  const playProtectEvaluated =
    playProtectVerdict !==
      'UNEVALUATED';

  const playProtectRisk =
    playProtectVerdict !==
      'NO_ISSUES';

  const deviceAttributes =
    device.deviceAttributes &&
    typeof device.deviceAttributes === 'object'
      ? device.deviceAttributes
      : {};

  const sdkVersion =
    Number.isSafeInteger(
      deviceAttributes.sdkVersion
    )
      ? deviceAttributes.sdkVersion
      : null;

  const optionalVerdictsReady =
    accessEvaluated &&
    playProtectEvaluated &&
    activityEvaluated;

  const criticalActionAllowed =
    meetsDeviceIntegrity &&
    optionalVerdictsReady &&
    !appAccessRisk &&
    !playProtectRisk &&
    !activityRisk;

  return {
    meetsDeviceIntegrity,
    optionalVerdictsReady,
    appAccessRisk,
    playProtectVerdict,
    activityLevel,
    activityRisk,
    sdkVersion,
    criticalActionAllowed
  };
}
