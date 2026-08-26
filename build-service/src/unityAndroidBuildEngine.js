import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import path from "path";

const MAX_ZIP_ENTRIES =
  30_000;

const MAX_UNCOMPRESSED_BYTES =
  750 * 1024 * 1024;

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

function ignoredUnityPath(
  segments
) {
  const ignored =
    new Set([
      ".git",
      ".idea",
      ".vs",
      "library",
      "temp",
      "logs",
      "obj",
      "userSettings".toLowerCase()
    ]);

  return segments.some(
    segment =>
      ignored.has(
        segment
          .toLowerCase()
      )
  );
}

async function extractUnityZip(
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
      "Unity projesinde çok fazla ZIP girdisi var."
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
        "Unity ZIP yolu güvenli değil."
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
        "Unity ZIP dizin dışına çıkmaya çalışıyor."
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
      ignoredUnityPath(
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
        "Unity ZIP hedef yolu güvenli değil."
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
        "Unity proje ZIP'i açıldığında boyut sınırını aşıyor."
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
    maxDepth = 8,
    maxFiles = 15_000
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

    let entries;

    try {
      entries =
        await fs.readdir(
          dir,
          {
            withFileTypes:
              true
          }
        );
    } catch {
      return;
    }

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
          ".vs",
          "Library",
          "Temp",
          "Logs",
          "obj",
          "UserSettings"
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

function parseProjectVersion(
  text
) {
  const editorVersion =
    String(
      text ||
      ""
    )
      .match(
        /^m_EditorVersion:\s*(.+?)\s*$/m
      )?.[1]
      ?.trim() ||
    null;

  const revision =
    String(
      text ||
      ""
    )
      .match(
        /^m_EditorVersionWithRevision:\s*(.+?)\s*$/m
      )?.[1]
      ?.trim() ||
    null;

  return {
    editorVersion,
    revision
  };
}

function parseBuildScenes(
  text
) {
  const lines =
    String(
      text ||
      ""
    )
      .split(
        /\r?\n/
      );

  const scenes =
    [];

  let enabled =
    false;

  for (
    const line of
    lines
  ) {
    const enabledMatch =
      line.match(
        /^\s*-?\s*enabled:\s*(\d+)\s*$/
      );

    if (
      enabledMatch
    ) {
      enabled =
        enabledMatch[1] ===
          "1";
      continue;
    }

    const pathMatch =
      line.match(
        /^\s*path:\s*(.+?)\s*$/
      );

    if (
      pathMatch
    ) {
      const scene =
        pathMatch[1]
          .trim();

      if (
        enabled &&
        scene
      ) {
        scenes.push(
          scene
        );
      }
    }
  }

  return scenes;
}

function parseAndroidIdentifier(
  text
) {
  const source =
    String(
      text ||
      ""
    );

  const block =
    source.match(
      /applicationIdentifier:\s*\{([^}]+)\}/i
    );

  if (
    block
  ) {
    const android =
      block[1]
        .match(
          /Android:\s*([^,\s}]+)/i
        )?.[1];

    if (
      android
    ) {
      return android.trim();
    }
  }

  return (
    source.match(
      /^\s*Android:\s*([A-Za-z0-9_.-]+)\s*$/m
    )?.[1] ||
    null
  );
}

async function findUnityProjectRoot(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot
    );

  const projectVersionFiles =
    files
      .filter(
        file =>
          path
            .relative(
              extractedRoot,
              file
            )
            .replaceAll(
              "\\",
              "/"
            )
            .toLowerCase()
            .endsWith(
              "projectsettings/projectversion.txt"
            )
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
    const projectVersionFile of
    projectVersionFiles
  ) {
    const projectRoot =
      path.dirname(
        path.dirname(
          projectVersionFile
        )
      );

    if (
      await dirExists(
        path.join(
          projectRoot,
          "Assets"
        )
      )
    ) {
      return {
        projectRoot,
        projectVersionFile
      };
    }
  }

  throw new Error(
    "Unity projesinde ProjectSettings/ProjectVersion.txt ve Assets klasörü birlikte bulunamadı."
  );
}

export async function prepareUnityAndroidSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "Unity kaynak ZIP'i eksik."
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
    await extractUnityZip(
      projectZip,
      extractedRoot
    );

  const found =
    await findUnityProjectRoot(
      extractedRoot
    );

  const projectVersionText =
    await fs.readFile(
      found.projectVersionFile,
      "utf8"
    );

  const version =
    parseProjectVersion(
      projectVersionText
    );

  if (
    !version.editorVersion
  ) {
    throw new Error(
      "Unity ProjectVersion.txt içinde m_EditorVersion bulunamadı."
    );
  }

  const packagesFile =
    path.join(
      found.projectRoot,
      "Packages",
      "manifest.json"
    );

  let packages =
    {};

  if (
    await fileExists(
      packagesFile
    )
  ) {
    try {
      packages =
        JSON.parse(
          await fs.readFile(
            packagesFile,
            "utf8"
          )
        )?.dependencies ||
        {};
    } catch {
      throw new Error(
        "Unity Packages/manifest.json geçerli JSON değil."
      );
    }
  }

  const buildSettingsFile =
    path.join(
      found.projectRoot,
      "ProjectSettings",
      "EditorBuildSettings.asset"
    );

  let enabledScenes =
    [];

  if (
    await fileExists(
      buildSettingsFile
    )
  ) {
    enabledScenes =
      parseBuildScenes(
        await fs.readFile(
          buildSettingsFile,
          "utf8"
        )
      );
  }

  const projectSettingsFile =
    path.join(
      found.projectRoot,
      "ProjectSettings",
      "ProjectSettings.asset"
    );

  let androidApplicationId =
    null;

  if (
    await fileExists(
      projectSettingsFile
    )
  ) {
    androidApplicationId =
      parseAndroidIdentifier(
        await fs.readFile(
          projectSettingsFile,
          "utf8"
        )
      );
  }

  const androidPluginsDir =
    path.join(
      found.projectRoot,
      "Assets",
      "Plugins",
      "Android"
    );

  const hasAndroidPlugins =
    await dirExists(
      androidPluginsDir
    );

  const detectedTemplates =
    [];

  for (
    const name of [
      "mainTemplate.gradle",
      "launcherTemplate.gradle",
      "baseProjectTemplate.gradle",
      "gradleTemplate.properties",
      "settingsTemplate.gradle"
    ]
  ) {
    if (
      await fileExists(
        path.join(
          androidPluginsDir,
          name
        )
      )
    ) {
      detectedTemplates.push(
        name
      );
    }
  }

  if (
    onLog
  ) {
    await onLog(
      `🎮 Unity proje hazır • ${version.editorVersion} • ${enabledScenes.length} aktif sahne`
    );
  }

  return {
    projectRoot:
      found.projectRoot,
    projectVersionFile:
      found.projectVersionFile,
    editorVersion:
      version.editorVersion,
    editorRevision:
      version.revision,
    enabledScenes,
    packages,
    packageCount:
      Object.keys(
        packages
      ).length,
    androidApplicationId,
    hasAndroidPlugins,
    gradleTemplates:
      detectedTemplates,
    requiresLicensedUnityEditor:
      true,
    buildReady:
      false,
    buildBlockedReason:
      "Unity kaynak projesini Android'e derlemek için proje sürümüyle uyumlu lisanslı Unity Editor + Android Build Support worker gerekir.",
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}

export function inspectUnityProjectArchive(
  projectZip
) {
  if (
    !projectZip
  ) {
    throw new Error(
      "Unity kaynak ZIP'i eksik."
    );
  }

  const zip =
    new AdmZip(
      projectZip
    );

  const candidates =
    zip
      .getEntries()
      .filter(
        entry => {
          const normalized =
            String(
              entry.entryName ||
              ""
            )
              .replaceAll(
                "\\",
                "/"
              )
              .toLowerCase();

          return (
            !entry.isDirectory &&
            normalized.endsWith(
              "projectsettings/projectversion.txt"
            )
          );
        }
      )
      .sort(
        (
          a,
          b
        ) =>
          String(
            a.entryName
          )
            .split(
              "/"
            ).length -
          String(
            b.entryName
          )
            .split(
              "/"
            ).length
      );

  if (
    !candidates.length
  ) {
    throw new Error(
      "Unity ZIP içinde ProjectSettings/ProjectVersion.txt bulunamadı."
    );
  }

  const entry =
    candidates[0];

  const data =
    entry.getData();

  if (
    data.length >
      64 * 1024
  ) {
    throw new Error(
      "Unity ProjectVersion.txt beklenenden büyük."
    );
  }

  const parsed =
    parseProjectVersion(
      data.toString(
        "utf8"
      )
    );

  if (
    !parsed.editorVersion
  ) {
    throw new Error(
      "Unity ProjectVersion.txt içinde m_EditorVersion bulunamadı."
    );
  }

  return {
    editorVersion:
      parsed.editorVersion,
    editorRevision:
      parsed.revision,
    entryName:
      entry.entryName
  };
}
