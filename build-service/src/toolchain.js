import { promises as fs } from "fs";
import path from "path";
import { execFile } from "child_process";
import { promisify } from "util";
import { config } from "./config.js";

const execFileAsync =
  promisify(execFile);

function versionTuple(value) {
  return String(value || "")
    .split(".")
    .map(part => {
      const match =
        String(part).match(/\d+/);

      return match
        ? Number(match[0])
        : 0;
    });
}

export function compareVersions(
  a,
  b
) {
  const left =
    versionTuple(a);

  const right =
    versionTuple(b);

  const length =
    Math.max(
      left.length,
      right.length
    );

  for (
    let i = 0;
    i < length;
    i++
  ) {
    const av =
      left[i] || 0;

    const bv =
      right[i] || 0;

    if (av > bv) return 1;
    if (av < bv) return -1;
  }

  return 0;
}

async function run(
  command,
  args = [],
  timeout = 12000
) {
  try {
    const {
      stdout,
      stderr
    } =
      await execFileAsync(
        command,
        args,
        {
          timeout,
          windowsHide: true,
          maxBuffer:
            2 * 1024 * 1024
        }
      );

    return {
      ok: true,
      stdout:
        String(stdout || ""),
      stderr:
        String(stderr || "")
    };
  } catch (error) {
    return {
      ok: false,
      stdout:
        String(
          error?.stdout ||
          ""
        ),
      stderr:
        String(
          error?.stderr ||
          error?.message ||
          ""
        ),
      code:
        error?.code || null
    };
  }
}

function extractJavaMajor(text) {
  const value =
    String(text || "");

  const match =
    value.match(
      /version\s+"(?:1\.)?(\d+)/
    );

  return match
    ? Number(match[1])
    : null;
}

function extractGradleVersion(text) {
  const match =
    String(text || "")
      .match(
        /Gradle\s+([0-9.]+)/
      );

  return match
    ? match[1]
    : null;
}

function sdkRoot() {
  return (
    process.env.ANDROID_SDK_ROOT ||
    process.env.ANDROID_HOME ||
    ""
  );
}

function sdkManagerPath(root) {
  if (!root) {
    return "sdkmanager";
  }

  const suffix =
    process.platform === "win32"
      ? "sdkmanager.bat"
      : "sdkmanager";

  return path.join(
    root,
    "cmdline-tools",
    "latest",
    "bin",
    suffix
  );
}

async function exists(file) {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

export async function runToolchainDoctor() {
  const root =
    sdkRoot();

  const java =
    await run(
      "java",
      ["-version"]
    );

  const gradle =
    await run(
      config.gradleBin,
      ["--version"],
      20000
    );

  const sdkmanager =
    await run(
      sdkManagerPath(root),
      ["--version"]
    );

  const javaText =
    `${java.stdout}\n${java.stderr}`;

  const gradleText =
    `${gradle.stdout}\n${gradle.stderr}`;

  const javaMajor =
    extractJavaMajor(
      javaText
    );

  const gradleVersion =
    extractGradleVersion(
      gradleText
    );

  const platformCandidates =
    root
      ? [
          path.join(
            root,
            "platforms",
            `android-${config.expectedAndroidApi}`,
            "android.jar"
          ),
          path.join(
            root,
            "platforms",
            `android-${config.expectedAndroidApi}.0`,
            "android.jar"
          )
        ]
      : [];

  let platformJar = "";

  for (const candidate of platformCandidates) {
    if (await exists(candidate)) {
      platformJar = candidate;
      break;
    }
  }

  if (!platformJar && platformCandidates.length) {
    platformJar = platformCandidates[0];
  }

  const buildToolsDir =
    root
      ? path.join(
          root,
          "build-tools",
          config.expectedBuildTools
        )
      : "";

  const aapt2 =
    buildToolsDir
      ? path.join(
          buildToolsDir,
          process.platform === "win32"
            ? "aapt2.exe"
            : "aapt2"
        )
      : "";

  const platformOk =
    Boolean(
      platformJar &&
      await exists(
        platformJar
      )
    );

  const buildToolsOk =
    Boolean(
      aapt2 &&
      await exists(
        aapt2
      )
    );

  const javaOk =
    java.ok &&
    javaMajor ===
      config.expectedJdkMajor;

  const gradleOk =
    gradle.ok &&
    gradleVersion &&
    compareVersions(
      gradleVersion,
      config.expectedGradle
    ) >= 0;

  const sdkManagerOk =
    sdkmanager.ok;

  const capabilities = [];

  if (javaOk) {
    capabilities.push(
      `java-${config.expectedJdkMajor}`
    );
  }

  if (gradleOk) {
    capabilities.push(
      "gradle",
      `gradle-${gradleVersion}`
    );
  }

  if (platformOk) {
    capabilities.push(
      `android-api-${config.expectedAndroidApi}`
    );
  }

  if (buildToolsOk) {
    capabilities.push(
      `build-tools-${config.expectedBuildTools}`
    );
  }

  const errors = [];

  if (!javaOk) {
    errors.push(
      `JDK ${config.expectedJdkMajor} gerekli.`
    );
  }

  if (!gradleOk) {
    errors.push(
      `Gradle ${config.expectedGradle}+ gerekli.`
    );
  }

  if (!sdkManagerOk) {
    errors.push(
      "Android sdkmanager bulunamadı."
    );
  }

  if (!platformOk) {
    errors.push(
      `Android platform android-${config.expectedAndroidApi} bulunamadı.`
    );
  }

  if (!buildToolsOk) {
    errors.push(
      `Android Build Tools ${config.expectedBuildTools} bulunamadı.`
    );
  }

  return {
    ok:
      javaOk &&
      gradleOk &&
      sdkManagerOk &&
      platformOk &&
      buildToolsOk,

    expected: {
      jdkMajor:
        config.expectedJdkMajor,
      gradle:
        config.expectedGradle,
      androidApi:
        config.expectedAndroidApi,
      buildTools:
        config.expectedBuildTools
    },

    detected: {
      javaMajor,
      gradleVersion,
      sdkRoot:
        root || null,
      sdkManager:
        sdkmanager.ok,
      platformJar:
        platformJar || null,
      buildToolsDir:
        buildToolsDir || null
    },

    capabilities:
      [...new Set(capabilities)],

    errors,

    raw: {
      java:
        (
          javaText
            .trim()
            .split(/\r?\n/)
            .slice(0, 8)
            .join("\n")
        ),
      gradle:
        (
          gradleText
            .trim()
            .split(/\r?\n/)
            .slice(0, 18)
            .join("\n")
        ),
      sdkmanager:
        (
          `${sdkmanager.stdout}\n${sdkmanager.stderr}`
            .trim()
            .split(/\r?\n/)
            .slice(0, 8)
            .join("\n")
        )
    }
  };
}

export function assertToolchain(
  diagnostics
) {
  if (
    config.workerStrictToolchain &&
    !diagnostics?.ok
  ) {
    const error =
      new Error(
        "Android worker toolchain doğrulaması başarısız: " +
        (
          diagnostics?.errors ||
          []
        ).join(" ")
      );

    error.code =
      "TOOLCHAIN_INVALID";

    throw error;
  }
}
