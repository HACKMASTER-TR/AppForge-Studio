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

assertCriticalConfig();

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

const diagnostics =
  await runToolchainDoctor();

console.log(
  JSON.stringify(
    {
      type:
        "worker_toolchain",
      ok:
        diagnostics.ok,
      detected:
        diagnostics.detected,
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
      ...diagnostics.capabilities
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
