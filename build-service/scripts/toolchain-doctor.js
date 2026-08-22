import {
  runToolchainDoctor,
  assertToolchain
} from "../src/toolchain.js";

const diagnostics =
  await runToolchainDoctor();

const json =
  process.argv.includes(
    "--json"
  );

if (json) {
  console.log(
    JSON.stringify(
      diagnostics,
      null,
      2
    )
  );
} else {
  console.log(
    "AppForge Android Worker Doctor"
  );

  console.log(
    `Status: ${
      diagnostics.ok
        ? "OK"
        : "FAILED"
    }`
  );

  console.log(
    `JDK: ${
      diagnostics.detected
        .javaMajor ??
      "yok"
    }`
  );

  console.log(
    `Gradle: ${
      diagnostics.detected
        .gradleVersion ??
      "yok"
    }`
  );

  console.log(
    `Android SDK: ${
      diagnostics.detected
        .sdkRoot ??
      "yok"
    }`
  );

  console.log(
    `Capabilities: ${
      diagnostics.capabilities
        .join(", ") ||
      "-"
    }`
  );

  if (
    diagnostics.errors.length
  ) {
    console.log(
      "Sorunlar:"
    );

    for (
      const error of
      diagnostics.errors
    ) {
      console.log(
        `- ${error}`
      );
    }
  }
}

if (
  process.argv.includes(
    "--strict"
  )
) {
  assertToolchain(
    diagnostics
  );
}
