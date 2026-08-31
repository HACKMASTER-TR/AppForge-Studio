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

assertCriticalConfig();

await fs.mkdir(
  config.workRoot,
  {
    recursive: true
  }
);

await fs.mkdir(
  config.outputRoot,
  {
    recursive: true
  }
);

await fs.mkdir(
  config.sharedInputRoot,
  {
    recursive: true
  }
);

await migrate();

const capabilities = [
  "windows-exe",
  "electron-portable",
  "windows-x64"
];

const diagnostics = {
  ok: true,

  detected: {
    platform:
      "windows-cross-build",

    engine:
      "electron-builder"
  },

  errors: [],

  capabilities
};

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
  "AppForge Windows Worker starting: " +
  capabilities.join(", ")
);

await startWorker({
  capabilities,
  diagnostics,
  concurrency:
    Math.max(
      1,
      Number(
        process.env
          .WINDOWS_BUILD_CONCURRENCY ||
        1
      )
    )
});
