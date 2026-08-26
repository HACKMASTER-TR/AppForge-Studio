import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import path from "path";

const MAX_ZIP_ENTRIES =
  12_000;

const MAX_UNCOMPRESSED_BYTES =
  350 * 1024 * 1024;

function safeInside(
  root,
  candidate
) {
  const resolvedRoot =
    path.resolve(
      root
    );

  const resolvedCandidate =
    path.resolve(
      candidate
    );

  return (
    resolvedCandidate ===
      resolvedRoot ||
    resolvedCandidate.startsWith(
      resolvedRoot +
        path.sep
    )
  );
}

function ignoredPath(
  segments
) {
  const ignored =
    new Set([
      ".git",
      ".idea",
      ".gradle",
      "node_modules",
      "build",
      "dist"
    ]);

  return segments.some(
    segment =>
      ignored.has(
        segment
      )
  );
}

async function extractProjectZip(
  zipPath,
  destination
) {
  const zip =
    new AdmZip(
      zipPath
    );

  const entries =
    zip.getEntries();

  if (
    entries.length >
      MAX_ZIP_ENTRIES
  ) {
    throw new Error(
      "React Native / Expo projesinde çok fazla ZIP girdisi var."
    );
  }

  await fs.rm(
    destination,
    {
      recursive: true,
      force: true
    }
  );

  await fs.mkdir(
    destination,
    {
      recursive: true
    }
  );

  let totalBytes =
    0;

  for (
    const entry of
    entries
  ) {
    const rawName =
      String(
        entry.entryName ||
        ""
      )
        .replaceAll(
          "\\",
          "/"
        );

    if (
      !rawName ||
      rawName.startsWith(
        "/"
      ) ||
      rawName.includes(
        "\0"
      )
    ) {
      throw new Error(
        "React Native / Expo ZIP yolu güvenli değil."
      );
    }

    const normalized =
      path.posix.normalize(
        rawName
      );

    if (
      normalized ===
        ".." ||
      normalized.startsWith(
        "../"
      )
    ) {
      throw new Error(
        "React Native / Expo ZIP dizin dışına çıkmaya çalışıyor."
      );
    }

    const segments =
      normalized
        .split(
          "/"
        )
        .filter(
          Boolean
        );

    if (
      !segments.length ||
      ignoredPath(
        segments
      )
    ) {
      continue;
    }

    const target =
      path.join(
        destination,
        ...segments
      );

    if (
      !safeInside(
        destination,
        target
      )
    ) {
      throw new Error(
        "React Native / Expo ZIP hedef yolu güvenli değil."
      );
    }

    if (
      entry.isDirectory
    ) {
      await fs.mkdir(
        target,
        {
          recursive: true
        }
      );

      continue;
    }

    const data =
      entry.getData();

    totalBytes +=
      data.length;

    if (
      totalBytes >
        MAX_UNCOMPRESSED_BYTES
    ) {
      throw new Error(
        "React Native / Expo proje ZIP'i açıldığında boyut sınırını aşıyor."
      );
    }

    await fs.mkdir(
      path.dirname(
        target
      ),
      {
        recursive: true
      }
    );

    await fs.writeFile(
      target,
      data
    );
  }

  return {
    entries:
      entries.length,
    bytes:
      totalBytes
  };
}

async function walk(
  root,
  {
    maxDepth = 7,
    maxFiles = 8_000
  } = {}
) {
  const result =
    [];

  async function visit(
    dir,
    depth
  ) {
    if (
      depth >
        maxDepth ||
      result.length >=
        maxFiles
    ) {
      return;
    }

    const entries =
      await fs.readdir(
        dir,
        {
          withFileTypes:
            true
        }
      );

    for (
      const entry of
      entries
    ) {
      if (
        result.length >=
          maxFiles
      ) {
        break;
      }

      if (
        [
          ".git",
          ".idea",
          ".gradle",
          "node_modules",
          "build",
          "dist"
        ].includes(
          entry.name
        )
      ) {
        continue;
      }

      const full =
        path.join(
          dir,
          entry.name
        );

      if (
        entry.isDirectory()
      ) {
        await visit(
          full,
          depth + 1
        );
      } else if (
        entry.isFile()
      ) {
        result.push(
          full
        );
      }
    }
  }

  await visit(
    root,
    0
  );

  return result;
}

function dependenciesOf(
  packageJson
) {
  return {
    ...(
      packageJson
        ?.dependencies ||
      {}
    ),
    ...(
      packageJson
        ?.devDependencies ||
      {}
    )
  };
}

function hasDependency(
  packageJson,
  name
) {
  return Object.prototype.hasOwnProperty.call(
    dependenciesOf(
      packageJson
    ),
    name
  );
}

async function findProjectRoot(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot
    );

  const packages =
    files
      .filter(
        file =>
          path.basename(
            file
          )
            .toLowerCase() ===
          "package.json"
      )
      .sort(
        (
          a,
          b
        ) =>
          a.split(
            path.sep
          ).length -
          b.split(
            path.sep
          ).length
      );

  for (
    const packageFile of
    packages
  ) {
    let json;

    try {
      json =
        JSON.parse(
          await fs.readFile(
            packageFile,
            "utf8"
          )
        );
    } catch {
      continue;
    }

    if (
      hasDependency(
        json,
        "expo"
      ) ||
      hasDependency(
        json,
        "react-native"
      )
    ) {
      return {
        projectRoot:
          path.dirname(
            packageFile
          ),
        packageFile,
        packageJson:
          json
      };
    }
  }

  throw new Error(
    "package.json içinde expo veya react-native bağımlılığı bulunamadı."
  );
}

async function fileExists(
  file
) {
  try {
    return (
      await fs.stat(
        file
      )
    )
      .isFile();
  } catch {
    return false;
  }
}

async function dirExists(
  dir
) {
  try {
    return (
      await fs.stat(
        dir
      )
    )
      .isDirectory();
  } catch {
    return false;
  }
}

function packageManagerOf(
  projectRoot,
  files
) {
  const names =
    new Set(
      files.map(
        file =>
          path.basename(
            file
          )
            .toLowerCase()
      )
    );

  if (
    names.has(
      "pnpm-lock.yaml"
    )
  ) {
    return "pnpm";
  }

  if (
    names.has(
      "yarn.lock"
    )
  ) {
    return "yarn";
  }

  if (
    names.has(
      "package-lock.json"
    )
  ) {
    return "npm";
  }

  return "npm";
}

export async function prepareReactNativeSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "React Native / Expo kaynak ZIP'i eksik."
    );
  }

  const extractedRoot =
    path.join(
      workDir,
      "source"
    );

  if (
    cancelled
  ) {
    await cancelled();
  }

  const extracted =
    await extractProjectZip(
      projectZip,
      extractedRoot
    );

  const found =
    await findProjectRoot(
      extractedRoot
    );

  const files =
    await walk(
      found.projectRoot
    );

  const expo =
    hasDependency(
      found.packageJson,
      "expo"
    );

  const reactNative =
    hasDependency(
      found.packageJson,
      "react-native"
    );

  const androidDir =
    path.join(
      found.projectRoot,
      "android"
    );

  const hasAndroidDir =
    await dirExists(
      androidDir
    );

  const hasGradleSettings =
    (
      await fileExists(
        path.join(
          androidDir,
          "settings.gradle"
        )
      )
    ) ||
    (
      await fileExists(
        path.join(
          androidDir,
          "settings.gradle.kts"
        )
      )
    );

  const hasAndroidManifest =
    await fileExists(
      path.join(
        androidDir,
        "app",
        "src",
        "main",
        "AndroidManifest.xml"
      )
    );

  const nativeAndroidReady =
    hasAndroidDir &&
    hasGradleSettings &&
    hasAndroidManifest;

  const packageManager =
    packageManagerOf(
      found.projectRoot,
      files
    );

  const projectType =
    expo
      ? (
          nativeAndroidReady
            ? "expo-prebuilt"
            : "expo-managed"
        )
      : "react-native";

  if (
    onLog
  ) {
    await onLog(
      `⚛️ ${projectType} algılandı • paket yöneticisi: ${packageManager}`
    );
  }

  return {
    projectRoot:
      found.projectRoot,
    packageFile:
      found.packageFile,
    packageJson:
      found.packageJson,
    projectType,
    expo,
    reactNative,
    packageManager,
    androidDir,
    nativeAndroidReady,
    hasAndroidDir,
    hasGradleSettings,
    hasAndroidManifest,
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}
