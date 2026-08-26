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
  const a =
    path.resolve(
      root
    );

  const b =
    path.resolve(
      candidate
    );

  return (
    b ===
      a ||
    b.startsWith(
      a +
        path.sep
    )
  );
}

function ignored(
  segments
) {
  const names =
    new Set([
      ".git",
      ".idea",
      ".vs",
      "bin",
      "obj"
    ]);

  return segments.some(
    segment =>
      names.has(
        segment
      )
  );
}

async function extractZip(
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
      ".NET Android projesinde çok fazla ZIP girdisi var."
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

  let bytes =
    0;

  for (
    const entry of
    entries
  ) {
    const raw =
      String(
        entry.entryName ||
        ""
      )
        .replaceAll(
          "\\",
          "/"
        );

    if (
      !raw ||
      raw.startsWith(
        "/"
      ) ||
      raw.includes(
        "\0"
      )
    ) {
      throw new Error(
        ".NET Android ZIP yolu güvenli değil."
      );
    }

    const normalized =
      path.posix.normalize(
        raw
      );

    if (
      normalized ===
        ".." ||
      normalized.startsWith(
        "../"
      )
    ) {
      throw new Error(
        ".NET Android ZIP dizin dışına çıkmaya çalışıyor."
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
      ignored(
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
        ".NET Android ZIP hedef yolu güvenli değil."
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

    bytes +=
      data.length;

    if (
      bytes >
        MAX_UNCOMPRESSED_BYTES
    ) {
      throw new Error(
        ".NET Android ZIP açıldığında boyut sınırını aşıyor."
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
    bytes
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
        ignored(
          [
            entry.name
          ]
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
  text,
  tag
) {
  return (
    String(
      text ||
      ""
    )
      .match(
        new RegExp(
          `<${tag}>\\s*([^<]+?)\\s*</${tag}>`,
          "i"
        )
      )?.[1]
      ?.trim() ||
    null
  );
}

function targetFrameworks(
  text
) {
  const single =
    xmlValue(
      text,
      "TargetFramework"
    );

  const many =
    xmlValue(
      text,
      "TargetFrameworks"
    );

  return [
    ...(
      single
        ? [
            single
          ]
        : []
    ),
    ...(
      many
        ? many
            .split(
              ";"
            )
            .map(
              value =>
                value.trim()
            )
            .filter(
              Boolean
            )
        : []
    )
  ]
    .filter(
      (
        value,
        index,
        all
      ) =>
        all.indexOf(
          value
        ) ===
        index
    );
}

function boolValue(
  text,
  tag
) {
  return (
    xmlValue(
      text,
      tag
    )
      ?.toLowerCase() ===
    "true"
  );
}

export async function prepareDotnetAndroidSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      ".NET Android kaynak ZIP'i eksik."
    );
  }

  if (
    cancelled
  ) {
    await cancelled();
  }

  const sourceRoot =
    path.join(
      workDir,
      "source"
    );

  const extracted =
    await extractZip(
      projectZip,
      sourceRoot
    );

  const files =
    await walk(
      sourceRoot
    );

  const projects =
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

  if (
    !projects.length
  ) {
    throw new Error(
      ".NET Android projesinde .csproj bulunamadı."
    );
  }

  let selected =
    null;

  for (
    const file of
    projects
  ) {
    const text =
      await fs.readFile(
        file,
        "utf8"
      );

    const frameworks =
      targetFrameworks(
        text
      );

    const androidTargets =
      frameworks.filter(
        framework =>
          framework
            .toLowerCase()
            .includes(
              "-android"
            )
      );

    const useMaui =
      boolValue(
        text,
        "UseMaui"
      ) ||
      text
        .toLowerCase()
        .includes(
          "microsoft.maui"
        );

    if (
      androidTargets.length &&
      !useMaui
    ) {
      selected = {
        projectFile:
          file,
        projectRoot:
          path.dirname(
            file
          ),
        text,
        frameworks,
        androidTargets,
        useMaui
      };

      break;
    }
  }

  if (
    !selected
  ) {
    throw new Error(
      "MAUI olmayan .NET Android target içeren .csproj bulunamadı."
    );
  }

  const net10Targets =
    selected.androidTargets
      .filter(
        target =>
          target
            .toLowerCase()
            .startsWith(
              "net10.0-android"
            )
      );

  const applicationId =
    xmlValue(
      selected.text,
      "ApplicationId"
    );

  const applicationTitle =
    xmlValue(
      selected.text,
      "ApplicationTitle"
    );

  const applicationVersion =
    xmlValue(
      selected.text,
      "ApplicationVersion"
    );

  const applicationDisplayVersion =
    xmlValue(
      selected.text,
      "ApplicationDisplayVersion"
    );

  const outputType =
    xmlValue(
      selected.text,
      "OutputType"
    );

  if (
    onLog
  ) {
    await onLog(
      `🔷 .NET Android foundation • ${selected.androidTargets.join(", ")}`
    );
  }

  return {
    projectFile:
      selected.projectFile,
    projectRoot:
      selected.projectRoot,
    targetFrameworks:
      selected.frameworks,
    androidTargets:
      selected.androidTargets,
    net10AndroidTargets:
      net10Targets,
    androidReady:
      net10Targets.length >
      0,
    useMaui:
      false,
    outputType,
    applicationId,
    applicationTitle,
    applicationVersion,
    applicationDisplayVersion,
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}
