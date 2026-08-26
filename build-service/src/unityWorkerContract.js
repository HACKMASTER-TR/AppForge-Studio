const UNITY_VERSION_PATTERN =
  /^\d+\.\d+\.\d+[abfp]\d+(?:c\d+)?$/i;

export function normalizeUnityEditorVersion(
  value
) {
  const version =
    String(
      value ||
      ""
    )
      .trim();

  if (
    !UNITY_VERSION_PATTERN.test(
      version
    )
  ) {
    throw new Error(
      `Geçersiz Unity Editor sürümü: ${version || "(boş)"}`
    );
  }

  return version;
}

export function unityVersionChannel(
  value
) {
  const version =
    normalizeUnityEditorVersion(
      value
    );

  const [
    major,
    minor
  ] =
    version
      .split(
        "."
      );

  return {
    major,
    minor,
    family:
      `${major}.${minor}`
  };
}

export function unityWorkerRequirements(
  editorVersion
) {
  const version =
    normalizeUnityEditorVersion(
      editorVersion
    );

  const channel =
    unityVersionChannel(
      version
    );

  return [
    "unity-editor",
    "unity-android-build-support",
    `unity-family-${channel.family}`,
    `unity-editor-${version}`
  ];
}

export function unityWorkerCapabilities({
  editorVersion,
  androidBuildSupport = false
}) {
  const version =
    normalizeUnityEditorVersion(
      editorVersion
    );

  const channel =
    unityVersionChannel(
      version
    );

  const capabilities =
    [
      "unity-editor",
      `unity-family-${channel.family}`,
      `unity-editor-${version}`
    ];

  if (
    androidBuildSupport
  ) {
    capabilities.push(
      "unity-android-build-support"
    );
  }

  return capabilities;
}

export function unityWorkerCanClaim({
  editorVersion,
  androidBuildSupport = false,
  requiredCapabilities = []
}) {
  const available =
    new Set(
      unityWorkerCapabilities(
        {
          editorVersion,
          androidBuildSupport
        }
      )
    );

  return (
    requiredCapabilities
      .every(
        capability =>
          available.has(
            capability
          )
      )
  );
}
