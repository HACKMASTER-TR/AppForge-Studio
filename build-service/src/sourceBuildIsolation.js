const UNTRUSTED_SOURCE_ENGINES =
  new Set([
    "node-web",
    "python-android",
    "android-gradle",
    "flutter",
    "react-native-android",
    "expo-android",
    "android-ndk",
    "dotnet-maui-android",
    "dotnet-android",
    "unity-android"
  ]);

const ISOLATED_MODES =
  new Set([
    "dedicated",
    "container",
    "vm"
  ]);

export function isUntrustedSourceEngine(engine) {
  return UNTRUSTED_SOURCE_ENGINES.has(
    String(engine || "").trim().toLowerCase()
  );
}

export function sourceBuildIsolationStatus({
  engine,
  mode = "shared",
  requireIsolation = false,
  workerCapabilities = [],
  requiredCapability = "source-isolation-dedicated"
}) {
  const normalizedEngine =
    String(engine || "").trim().toLowerCase();

  const normalizedMode =
    String(mode || "shared").trim().toLowerCase();

  const capability =
    String(
      requiredCapability ||
      "source-isolation-dedicated"
    ).trim();

  const capabilities =
    new Set(
      (Array.isArray(workerCapabilities) ? workerCapabilities : [])
        .map(value => String(value || "").trim())
        .filter(Boolean)
    );

  const untrusted =
    isUntrustedSourceEngine(normalizedEngine);

  const isolatedMode =
    ISOLATED_MODES.has(normalizedMode);

  const capabilityPresent =
    Boolean(capability) &&
    capabilities.has(capability);

  // Operator attestation only. Actual OS/container/VM isolation
  // must be provided by the deployment platform.
  const attestedIsolation =
    isolatedMode &&
    capabilityPresent;

  const blocked =
    untrusted &&
    Boolean(requireIsolation) &&
    !attestedIsolation;

  return {
    engine: normalizedEngine,
    untrusted,
    mode: normalizedMode,
    requireIsolation: Boolean(requireIsolation),
    requiredCapability: capability,
    isolatedMode,
    capabilityPresent,
    attestedIsolation,
    blocked
  };
}

export function assertSourceBuildIsolation(options) {
  const status =
    sourceBuildIsolationStatus(options);

  if (status.blocked) {
    throw new Error(
      "Kaynak kod çalıştıran build motoru için izole Worker zorunlu. " +
      `Mevcut isolation mode=${status.mode}, ` +
      `gerekli capability=${status.requiredCapability}.`
    );
  }

  return status;
}

export function untrustedSourceEngines() {
  return [...UNTRUSTED_SOURCE_ENGINES].sort();
}

export function requiredSourceWorkerCapabilities({
  payload,
  requiredCapabilities = [],
  requireIsolation = false,
  isolationCapability = "source-isolation-dedicated"
}) {
  const base =
    Array.isArray(
      requiredCapabilities
    )
      ? requiredCapabilities
      : [];

  const config =
    payload
      ?.config ||
    {};

  const sourceMode =
    String(
      config.sourceMode ||
      "LOCAL"
    )
      .trim()
      .toUpperCase();

  const engine =
    String(
      config.sourceBuildEngine ||
      ""
    )
      .trim()
      .toLowerCase();

  const capability =
    String(
      isolationCapability ||
      ""
    )
      .trim();

  const needsIsolation =
    Boolean(
      requireIsolation
    ) &&
    sourceMode ===
      "LOCAL" &&
    isUntrustedSourceEngine(
      engine
    );

  return [
    ...new Set([
      ...base
        .map(
          value =>
            String(
              value ||
              ""
            )
              .trim()
        )
        .filter(
          Boolean
        ),
      ...(
        needsIsolation &&
        capability
          ? [
              capability
            ]
          : []
      )
    ])
  ];
}
