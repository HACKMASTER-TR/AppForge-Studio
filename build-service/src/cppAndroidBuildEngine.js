import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import path from "path";

const MAX_ZIP_ENTRIES =
  12_000;

const MAX_UNCOMPRESSED_BYTES =
  300 * 1024 * 1024;

const CPP_EXTENSIONS =
  new Set([
    ".c",
    ".cc",
    ".cpp",
    ".cxx",
    ".h",
    ".hh",
    ".hpp",
    ".hxx"
  ]);

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
      "build",
      "out",
      "cmake-build-debug",
      "cmake-build-release"
    ]);

  return segments.some(
    segment =>
      ignored.has(
        segment
      )
  );
}

async function extractCppZip(
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
      "C/C++ projesinde çok fazla ZIP girdisi var."
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
        "C/C++ ZIP yolu güvenli değil."
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
        "C/C++ ZIP dizin dışına çıkmaya çalışıyor."
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
        "C/C++ ZIP hedef yolu güvenli değil."
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
        "C/C++ proje ZIP'i açıldığında boyut sınırını aşıyor."
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
    maxFiles = 10_000
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
          "build",
          "out",
          "cmake-build-debug",
          "cmake-build-release"
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

async function findCppProjectRoot(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot
    );

  const cmakeFiles =
    files
      .filter(
        file =>
          path.basename(
            file
          )
            .toLowerCase() ===
          "cmakelists.txt"
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

  if (
    cmakeFiles.length
  ) {
    return {
      projectRoot:
        path.dirname(
          cmakeFiles[0]
        ),
      cmakeFile:
        cmakeFiles[0],
      files
    };
  }

  const cppFiles =
    files
      .filter(
        file =>
          CPP_EXTENSIONS.has(
            path.extname(
              file
            )
              .toLowerCase()
          )
      );

  if (
    cppFiles.length
  ) {
    const root =
      path.dirname(
        cppFiles
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
          )[0]
      );

    return {
      projectRoot:
        root,
      cmakeFile:
        null,
      files
    };
  }

  throw new Error(
    "C/C++ kaynak dosyası veya CMakeLists.txt bulunamadı."
  );
}

function relativeNames(
  root,
  files
) {
  return files
    .map(
      file =>
        path.relative(
          root,
          file
        )
          .replaceAll(
            "\\",
            "/"
          )
          .toLowerCase()
    );
}

export async function prepareCppAndroidSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "C/C++ kaynak ZIP'i eksik."
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
    await extractCppZip(
      projectZip,
      extractedRoot
    );

  const found =
    await findCppProjectRoot(
      extractedRoot
    );

  const names =
    relativeNames(
      found.projectRoot,
      found.files
    );

  const cppFiles =
    found.files.filter(
      file =>
        CPP_EXTENSIONS.has(
          path.extname(
            file
          )
            .toLowerCase()
        )
    );

  const appForgeEntry =
    cppFiles.find(
      file =>
        [
          "appforge_main.c",
          "appforge_main.cc",
          "appforge_main.cpp",
          "appforge_main.cxx"
        ]
          .includes(
            path.basename(
              file
            )
              .toLowerCase()
          )
    ) ||
    null;

  const existingAndroidProject =
    (
      names.includes(
        "android/settings.gradle"
      ) ||
      names.includes(
        "android/settings.gradle.kts"
      ) ||
      names.includes(
        "settings.gradle"
      ) ||
      names.includes(
        "settings.gradle.kts"
      )
    ) &&
    names.some(
      name =>
        name.endsWith(
          "app/src/main/androidmanifest.xml"
        )
    );

  const mode =
    existingAndroidProject
      ? "android-gradle-native"
      : (
          appForgeEntry
            ? "appforge-native-entry"
            : (
                found.cmakeFile
                  ? "cmake-generic"
                  : "cpp-generic"
              )
        );

  if (
    onLog
  ) {
    await onLog(
      `🧩 C/C++ kaynak hazır • ${mode} • ${cppFiles.length} native dosya`
    );
  }

  return {
    projectRoot:
      found.projectRoot,
    cmakeFile:
      found.cmakeFile,
    cppFiles,
    appForgeEntry,
    existingAndroidProject,
    mode,
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}
