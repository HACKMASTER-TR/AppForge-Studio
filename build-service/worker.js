import { promises as fs } from "fs";
import {
  config,
  assertCriticalConfig
} from "./src/config.js";
import {
  migrate,
  closeDb
} from "./src/db.js";
import {
  startWorker,
  stopWorker
} from "./src/workerRuntime.js";
import {
  runToolchainDoctor,
  assertToolchain
} from "./src/toolchain.js";
import {
  materializeFastDebugKeystore
} from "./src/fastSigningKey.js";
import {
  loadSourceToolchainRegistry,
  inspectInstalledSourceWorkerToolchain
} from "./src/sourceToolchainRegistry.js";

assertCriticalConfig();

const signingKey =
  await materializeFastDebugKeystore();

if (signingKey.materialized) {
  console.log(
    `FAST signing keystore hazır: ${signingKey.path}`
  );
}

await fs.mkdir(
  config.workRoot,
  { recursive: true }
);

await fs.mkdir(
  config.outputRoot,
  { recursive: true }
);

await fs.mkdir(
  config.sharedInputRoot,
  { recursive: true }
);

await fs.mkdir(
  config.gradleCacheRoot,
  { recursive: true }
);

await migrate();

const baseDiagnostics =
  await runToolchainDoctor();

const dedicatedSourceWorker =
  config.sourceBuildRequireIsolation ===
    true &&
  config.sourceBuildIsolationMode !==
    "shared" &&
  config.workerCapabilities.includes(
    config.sourceBuildIsolationCapability
  );

let sourceToolchainRegistry =
  null;

if (dedicatedSourceWorker) {
  const registry =
    await loadSourceToolchainRegistry();

  sourceToolchainRegistry =
    await inspectInstalledSourceWorkerToolchain({
      registry,
      runtime: true
    });
}

const diagnostics = {
  ...baseDiagnostics,
  ok:
    baseDiagnostics.ok &&
    (
      sourceToolchainRegistry
        ?.ok ??
      true
    ),
  errors: [
    ...(baseDiagnostics.errors || []),
    ...(
      sourceToolchainRegistry
        ?.errors ||
      []
    )
  ],
  sourceToolchainRegistry:
    sourceToolchainRegistry
      ? {
          ok:
            sourceToolchainRegistry.ok,
          registrySchemaVersion:
            sourceToolchainRegistry
              .registrySchemaVersion,
          sdkWritable:
            sourceToolchainRegistry
              .sdkWritable,
          javaMajor:
            sourceToolchainRegistry
              .javaMajor,
          capabilities:
            sourceToolchainRegistry
              .capabilities,
          errors:
            sourceToolchainRegistry
              .errors
        }
      : null
};

console.log(
  JSON.stringify(
    {
      type:
        "worker_toolchain",
      ok:
        diagnostics.ok,
      detected:
        diagnostics.detected,
      sourceToolchainRegistry:
        diagnostics
          .sourceToolchainRegistry,
      errors:
        diagnostics.errors
    },
    null,
    2
  )
);

assertToolchain(
  diagnostics
);

const effectiveCapabilities =
  [
    ...new Set([
      ...config.workerCapabilities,
      ...baseDiagnostics.capabilities,
      ...(
        sourceToolchainRegistry
          ?.capabilities ||
        []
      )
    ])
  ];

process.on(
  "SIGINT",
  async () => {
    stopWorker();
    await closeDb();
    process.exit(0);
  }
);

process.on(
  "SIGTERM",
  async () => {
    stopWorker();
    await closeDb();
    process.exit(0);
  }
);

console.log(
  `AppForge v1.6 worker starting with capabilities: ${
    effectiveCapabilities.join(", ")
  }`
);

await startWorker({
  capabilities:
    effectiveCapabilities,
  diagnostics
});
