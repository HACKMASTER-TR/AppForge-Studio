import fs from "fs";
import path from "path";
import {
  fileURLToPath
} from "url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const serviceRoot =
  path.resolve(
    here,
    ".."
  );

const matrixPath =
  path.join(
    serviceRoot,
    "source-worker-toolchain.json"
  );

const matrix =
  JSON.parse(
    fs.readFileSync(
      matrixPath,
      "utf8"
    )
  );

const sdkRoot =
  process.env.ANDROID_SDK_ROOT ||
  process.env.ANDROID_HOME ||
  "/opt/android-sdk";

const gradleRoot =
  "/opt/gradle";

const runtime =
  process.argv.includes(
    "--runtime"
  );

const strict =
  process.argv.includes(
    "--strict"
  );

const checks = [];
const errors = [];

function check(
  kind,
  version,
  target
) {
  const exists =
    fs.existsSync(
      target
    );

  checks.push({
    kind,
    version,
    target,
    exists
  });

  if (!exists) {
    errors.push(
      `${kind} ${version} eksik: ${target}`
    );
  }
}

for (
  const version of
  matrix.androidPlatforms
) {
  check(
    "android-platform",
    version,
    path.join(
      sdkRoot,
      "platforms",
      `android-${version}`,
      "android.jar"
    )
  );
}

for (
  const version of
  matrix.buildTools
) {
  check(
    "build-tools",
    version,
    path.join(
      sdkRoot,
      "build-tools",
      version,
      "aapt2"
    )
  );
}

for (
  const version of
  matrix.ndk
) {
  check(
    "ndk",
    version,
    path.join(
      sdkRoot,
      "ndk",
      version,
      "source.properties"
    )
  );
}

for (
  const version of
  matrix.cmake
) {
  check(
    "cmake",
    version,
    path.join(
      sdkRoot,
      "cmake",
      version,
      "bin",
      "cmake"
    )
  );
}

for (
  const version of
  matrix.gradle
) {
  check(
    "gradle",
    version,
    path.join(
      gradleRoot,
      `gradle-${version}`,
      "bin",
      "gradle"
    )
  );
}

let sdkWritable = false;

try {
  fs.accessSync(
    sdkRoot,
    fs.constants.W_OK
  );

  sdkWritable = true;
} catch {
  sdkWritable = false;
}

if (
  runtime &&
  sdkWritable
) {
  errors.push(
    `Runtime Android SDK yazılabilir olmamalı: ${sdkRoot}`
  );
}

const result = {
  ok:
    errors.length ===
    0,
  runtime,
  sdkRoot,
  sdkWritable,
  matrix,
  checks,
  errors
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
