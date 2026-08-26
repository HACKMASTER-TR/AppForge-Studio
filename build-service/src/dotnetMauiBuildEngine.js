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
      ".vs",
      "bin",
      "obj"
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
      ".NET MAUI projesinde çok fazla ZIP girdisi var."
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
        ".NET MAUI ZIP yolu güvenli değil."
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
        ".NET MAUI ZIP dizin dışına çıkmaya çalışıyor."
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
        ".NET MAUI ZIP hedef yolu güvenli değil."
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
        ".NET MAUI proje ZIP'i açıldığında boyut sınırını aşıyor."
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
          ".vs",
          "bin",
          "obj"
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

function xmlValue(
  xml,
  tag
) {
  const expression =
    new RegExp(
      `<${tag}>\\s*([^<]+?)\\s*</${tag}>`,
      "i"
    );

  return (
    String(
      xml ||
      ""
    )
      .match(
        expression
      )?.[1]
      ?.trim() ||
    null
  );
}

function xmlBool(
  xml,
  tag
) {
  return (
    xmlValue(
      xml,
      tag
    )
      ?.toLowerCase() ===
    "true"
  );
}

function targetFrameworksOf(
  xml
) {
  const combined =
    [
      xmlValue(
        xml,
        "TargetFramework"
      ),
      xmlValue(
        xml,
        "TargetFrameworks"
      )
    ]
      .filter(
        Boolean
      )
      .join(
        ";"
      );

  return combined
    .split(
      ";"
    )
    .map(
      value =>
        value.trim()
    )
    .filter(
      Boolean
    );
}

async function findMauiProject(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot
    );

  const projectFiles =
    files
      .filter(
        file =>
          path.extname(
            file
          )
            .toLowerCase() ===
          ".csproj"
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
    const projectFile of
    projectFiles
  ) {
    let xml;

    try {
      xml =
        await fs.readFile(
          projectFile,
          "utf8"
        );
    } catch {
      continue;
    }

    const lower =
      xml.toLowerCase();

    const useMaui =
      xmlBool(
        xml,
        "UseMaui"
      );

    const hasMauiReference =
      lower.includes(
        "microsoft.maui"
      );

    if (
      !useMaui &&
      !hasMauiReference
    ) {
      continue;
    }

    const targetFrameworks =
      targetFrameworksOf(
        xml
      );

    const androidTargets =
      targetFrameworks.filter(
        target =>
          target
            .toLowerCase()
            .includes(
              "-android"
            )
      );

    return {
      projectFile,
      projectRoot:
        path.dirname(
          projectFile
        ),
      xml,
      useMaui,
      hasMauiReference,
      targetFrameworks,
      androidTargets,
      projectName:
        path.basename(
          projectFile,
          path.extname(
            projectFile
          )
        ),
      applicationId:
        xmlValue(
          xml,
          "ApplicationId"
        ),
      applicationTitle:
        xmlValue(
          xml,
          "ApplicationTitle"
        ),
      applicationVersion:
        xmlValue(
          xml,
          "ApplicationVersion"
        ),
      applicationDisplayVersion:
        xmlValue(
          xml,
          "ApplicationDisplayVersion"
        )
    };
  }

  throw new Error(
    ".NET MAUI .csproj bulunamadı."
  );
}

export async function prepareDotnetMauiSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      ".NET MAUI kaynak ZIP'i eksik."
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
    await findMauiProject(
      extractedRoot
    );

  const androidReady =
    found.androidTargets.length >
      0;

  if (
    onLog
  ) {
    await onLog(
      `🔷 .NET MAUI proje hazır • ${found.projectName} • ` +
      (
        androidReady
          ? found.androidTargets.join(", ")
          : "Android target yok"
      )
    );
  }

  return {
    projectRoot:
      found.projectRoot,
    projectFile:
      found.projectFile,
    projectName:
      found.projectName,
    targetFrameworks:
      found.targetFrameworks,
    androidTargets:
      found.androidTargets,
    androidReady,
    applicationId:
      found.applicationId,
    applicationTitle:
      found.applicationTitle,
    applicationVersion:
      found.applicationVersion,
    applicationDisplayVersion:
      found.applicationDisplayVersion,
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}
