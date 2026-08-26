import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import path from "path";

const MAX_ZIP_ENTRIES =
  10_000;

const MAX_UNCOMPRESSED_BYTES =
  300 * 1024 * 1024;

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

function ignoredFlutterPath(
  segments
) {
  const ignored =
    new Set([
      ".git",
      ".dart_tool",
      ".idea",
      "build"
    ]);

  return segments.some(
    segment =>
      ignored.has(
        segment
      )
  );
}

async function extractFlutterZip(
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
      "Flutter projesinde çok fazla ZIP girdisi var."
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
        "Flutter ZIP yolu güvenli değil."
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
        "Flutter ZIP dizin dışına çıkmaya çalışıyor."
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
      ignoredFlutterPath(
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
        "Flutter ZIP hedef yolu güvenli değil."
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
        "Flutter proje ZIP'i açıldığında boyut sınırını aşıyor."
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
          ".dart_tool",
          ".idea",
          "build"
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

async function findFlutterProjectRoot(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot
    );

  const pubspecFiles =
    files
      .filter(
        file =>
          path.basename(
            file
          )
            .toLowerCase() ===
          "pubspec.yaml"
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
    const pubspecFile of
    pubspecFiles
  ) {
    const root =
      path.dirname(
        pubspecFile
      );

    const mainFile =
      path.join(
        root,
        "lib",
        "main.dart"
      );

    try {
      const stat =
        await fs.stat(
          mainFile
        );

      if (
        stat.isFile()
      ) {
        return {
          projectRoot:
            root,
          pubspecFile,
          mainFile
        };
      }
    } catch {
    }
  }

  throw new Error(
    "Flutter projesinde pubspec.yaml ve lib/main.dart birlikte bulunamadı."
  );
}

function parseFlutterProjectName(
  pubspec
) {
  const match =
    String(
      pubspec ||
      ""
    )
      .match(
        /^name:\s*([a-zA-Z0-9_]+)\s*$/m
      );

  return match?.[1] ||
    null;
}

export async function prepareFlutterSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "Flutter kaynak ZIP'i eksik."
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
    await extractFlutterZip(
      projectZip,
      extractedRoot
    );

  const found =
    await findFlutterProjectRoot(
      extractedRoot
    );

  const pubspec =
    await fs.readFile(
      found.pubspecFile,
      "utf8"
    );

  const projectName =
    parseFlutterProjectName(
      pubspec
    );

  if (
    !projectName
  ) {
    throw new Error(
      "pubspec.yaml içinde geçerli Flutter proje adı bulunamadı."
    );
  }

  if (
    cancelled
  ) {
    await cancelled();
  }

  if (
    onLog
  ) {
    await onLog(
      `🦋 Flutter proje hazır • ${projectName}`
    );
  }

  return {
    projectRoot:
      found.projectRoot,
    pubspecFile:
      found.pubspecFile,
    mainFile:
      found.mainFile,
    projectName,
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}
