import {
  promises as fs
} from "fs";
import path from "path";

const MIRROR_SETTING_PATH =
  "../.appforge/flutter-tools-gradle";

async function isDirectory(candidate) {
  try {
    return (
      await fs.stat(candidate)
    ).isDirectory();
  } catch {
    return false;
  }
}

async function findFlutterSettings(projectRoot) {
  const androidDir =
    path.join(projectRoot, "android");

  const candidates = [
    path.join(
      androidDir,
      "settings.gradle.kts"
    ),
    path.join(
      androidDir,
      "settings.gradle"
    )
  ];

  for (const candidate of candidates) {
    try {
      if (
        (
          await fs.stat(candidate)
        ).isFile()
      ) {
        return candidate;
      }
    } catch {
    }
  }

  return null;
}

export function patchFlutterSettingsForWritablePlugin(
  raw
) {
  const source =
    String(raw ?? "");

  if (
    source.includes(
      MIRROR_SETTING_PATH
    )
  ) {
    return {
      content: source,
      usesMirror: true,
      changed: false
    };
  }

  const patterns = [
    /^(\s*)includeBuild\s*\(\s*["'][^"'\r\n]*flutter_tools\/gradle["']\s*\)\s*;?\s*$/m,
    /^(\s*)includeBuild\s+["'][^"'\r\n]*flutter_tools\/gradle["']\s*;?\s*$/m
  ];

  for (const pattern of patterns) {
    if (pattern.test(source)) {
      return {
        content:
          source.replace(
            pattern,
            (_match, indent) =>
              `${indent}includeBuild("${MIRROR_SETTING_PATH}")`
          ),
        usesMirror: true,
        changed: true
      };
    }
  }

  return {
    content: source,
    usesMirror: false,
    changed: false
  };
}

export async function prepareWritableFlutterGradlePlugin({
  projectRoot,
  flutterRoot =
    process.env.FLUTTER_ROOT ||
    process.env.FLUTTER_HOME ||
    "/opt/flutter",
  onLog = null
}) {
  if (!projectRoot) {
    throw new Error(
      "Flutter writable Gradle mirror: proje kökü eksik."
    );
  }

  const settingsFile =
    await findFlutterSettings(
      projectRoot
    );

  if (!settingsFile) {
    return {
      mirrored: false,
      reason: "settings-not-found"
    };
  }

  const currentSettings =
    await fs.readFile(
      settingsFile,
      "utf8"
    );

  const patched =
    patchFlutterSettingsForWritablePlugin(
      currentSettings
    );

  // Eski Flutter projelerinde includeBuild bulunmayabilir.
  if (!patched.usesMirror) {
    return {
      mirrored: false,
      reason:
        "flutter-include-build-not-used",
      settingsFile
    };
  }

  const trustedSource =
    path.join(
      flutterRoot,
      "packages",
      "flutter_tools",
      "gradle"
    );

  if (
    !(
      await isDirectory(
        trustedSource
      )
    )
  ) {
    throw new Error(
      `Flutter Gradle plugin kaynağı bulunamadı: ${trustedSource}`
    );
  }

  const appForgeDir =
    path.join(
      projectRoot,
      ".appforge"
    );

  const mirrorDir =
    path.join(
      appForgeDir,
      "flutter-tools-gradle"
    );

  // ZIP içinden aynı isimde kötü/yanlış içerik gelmişse temizle.
  await fs.rm(
    mirrorDir,
    {
      recursive: true,
      force: true
    }
  );

  await fs.mkdir(
    appForgeDir,
    {
      recursive: true
    }
  );

  // Read-only /opt/flutter altındaki trusted Flutter Gradle
  // pluginini build workspace içine kopyala.
  await fs.cp(
    trustedSource,
    mirrorDir,
    {
      recursive: true,
      force: true,
      errorOnExist: false
    }
  );

  await fs.chmod(
    mirrorDir,
    0o755
  );

  if (patched.changed) {
    await fs.writeFile(
      settingsFile,
      patched.content,
      "utf8"
    );
  }

  if (onLog) {
    await onLog(
      "🦋 Flutter Gradle plugin writable workspace mirror hazır."
    );
  }

  return {
    mirrored: true,
    settingsFile,
    sourceDir: trustedSource,
    mirrorDir
  };
}
