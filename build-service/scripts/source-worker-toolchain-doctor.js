import {
  loadSourceToolchainRegistry,
  inspectInstalledSourceWorkerToolchain
} from "../src/sourceToolchainRegistry.js";

const runtime =
  process.argv.includes(
    "--runtime"
  );

const strict =
  process.argv.includes(
    "--strict"
  );

const matrix =
  await loadSourceToolchainRegistry();

const status =
  await inspectInstalledSourceWorkerToolchain({
    registry: matrix,
    runtime
  });

const result = {
  ...status,
  matrix
};

console.log(
  JSON.stringify(
    result,
    null,
    2
  )
);

if (
  strict &&
  !result.ok
) {
  process.exitCode = 1;
}
