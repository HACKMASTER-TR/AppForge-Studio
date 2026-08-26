import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import path from "path";

const MAX_ZIP_ENTRIES =
  12_000;

const MAX_UNCOMPRESSED_BYTES =
  250 * 1024 * 1024;

const MAX_TEXT_BYTES =
  2 * 1024 * 1024;

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

function ignoredPath(
  segments
) {
  const ignored =
    new Set([
      ".git",
      ".idea",
      ".gradle",
      ".venv",
      "venv",
      "__pycache__",
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

async function extractPythonWebZip(
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
      "Python web projesinde çok fazla ZIP girdisi var."
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
        "Python web ZIP yolu güvenli değil."
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
        "Python web ZIP dizin dışına çıkmaya çalışıyor."
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
        "Python web ZIP hedef yolu güvenli değil."
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
        "Python web ZIP açıldığında boyut sınırını aşıyor."
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
        ignoredPath(
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

async function readSmallText(
  file
) {
  try {
    const stat =
      await fs.stat(
        file
      );

    if (
      !stat.isFile() ||
      stat.size >
        MAX_TEXT_BYTES
    ) {
      return "";
    }

    return await fs.readFile(
      file,
      "utf8"
    );
  } catch {
    return "";
  }
}

function pythonModuleName(
  projectRoot,
  file
) {
  const relative =
    path.relative(
      projectRoot,
      file
    )
      .replaceAll(
        "\\",
        "/"
      )
      .replace(
        /\.py$/i,
        ""
      );

  return relative
    .split(
      "/"
    )
    .filter(
      Boolean
    )
    .join(
      "."
    );
}

function normalizeRequirementName(
  value
) {
  return String(
    value ||
    ""
  )
    .trim()
    .split(
      /[<>=!~;\[\]\s]/
    )[0]
    .trim()
    .toLowerCase()
    .replaceAll(
      "_",
      "-"
    );
}

export function parsePythonRequirements(
  text
) {
  const dependencies =
    [];

  const ignored =
    [];

  for (
    const rawLine of
    String(
      text ||
      ""
    )
      .split(
        /\r?\n/
      )
  ) {
    const line =
      rawLine
        .trim();

    if (
      !line ||
      line.startsWith(
        "#"
      )
    ) {
      continue;
    }

    if (
      line.startsWith(
        "-"
      ) ||
      line.includes(
        "://"
      ) ||
      line.startsWith(
        "."
      ) ||
      line.startsWith(
        "/"
      )
    ) {
      ignored.push(
        line
      );

      continue;
    }

    const name =
      normalizeRequirementName(
        line
      );

    if (
      name &&
      !dependencies.includes(
        name
      )
    ) {
      dependencies.push(
        name
      );
    }
  }

  return {
    dependencies,
    ignored
  };
}

function flaskContract(
  projectRoot,
  entryFile,
  source
) {
  const moduleName =
    pythonModuleName(
      projectRoot,
      entryFile
    );

  const appObject =
    String(
      source ||
      ""
    )
      .match(
        /^\s*([A-Za-z_]\w*)\s*=\s*Flask\s*\(/m
      )?.[1] ||
    null;

  const hasFactory =
    /^\s*def\s+create_app\s*\(/m
      .test(
        String(
          source ||
          ""
        )
      );

  return {
    framework:
      "flask",
    entryFile,
    moduleName,
    appObject,
    factory:
      hasFactory
        ? "create_app"
        : null,
    ready:
      Boolean(
        appObject ||
        hasFactory
      ),
    reason:
      appObject ||
      hasFactory
        ? "Flask app object/factory bulundu."
        : "Flask bulundu ancak app = Flask(...) veya create_app() giriş kontratı bulunamadı."
  };
}

async function detectFlask(
  projectRoot,
  files
) {
  const candidates =
    files
      .filter(
        file => {
          const name =
            path.basename(
              file
            )
              .toLowerCase();

          return (
            name ===
              "app.py" ||
            name ===
              "main.py"
          );
        }
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
    const file of
    candidates
  ) {
    const source =
      await readSmallText(
        file
      );

    if (
      /\bFlask\s*\(/.test(
        source
      ) ||
      /\bfrom\s+flask\b/.test(
        source
      ) ||
      /\bimport\s+flask\b/.test(
        source
      )
    ) {
      return flaskContract(
        projectRoot,
        file,
        source
      );
    }
  }

  return null;
}

async function detectDjango(
  projectRoot,
  files
) {
  const manage =
    files.find(
      file =>
        path.basename(
          file
        )
          .toLowerCase() ===
        "manage.py"
    );

  if (
    !manage
  ) {
    return null;
  }

  const settings =
    files
      .filter(
        file =>
          path.basename(
            file
          )
            .toLowerCase() ===
          "settings.py"
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
      )[0] ||
    null;

  const wsgi =
    files
      .filter(
        file =>
          path.basename(
            file
          )
            .toLowerCase() ===
          "wsgi.py"
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
      )[0] ||
    null;

  if (
    !settings
  ) {
    return {
      framework:
        "django",
      manageFile:
        manage,
      settingsFile:
        null,
      settingsModule:
        null,
      wsgiFile:
        wsgi,
      wsgiModule:
        wsgi
          ? pythonModuleName(
              projectRoot,
              wsgi
            )
          : null,
      ready:
        false,
      reason:
        "Django manage.py bulundu ancak settings.py bulunamadı."
    };
  }

  return {
    framework:
      "django",
    manageFile:
      manage,
    settingsFile:
      settings,
    settingsModule:
      pythonModuleName(
        projectRoot,
        settings
      ),
    wsgiFile:
      wsgi,
    wsgiModule:
      wsgi
        ? pythonModuleName(
            projectRoot,
            wsgi
          )
        : null,
    ready:
      Boolean(
        wsgi
      ),
    reason:
      wsgi
        ? "Django settings.py ve wsgi.py bulundu."
        : "Django settings.py bulundu ancak wsgi.py bulunamadı."
  };
}

async function findProjectRoot(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot
    );

  const anchors =
    files
      .filter(
        file => {
          const name =
            path.basename(
              file
            )
              .toLowerCase();

          return (
            name ===
              "manage.py" ||
            name ===
              "app.py" ||
            name ===
              "main.py" ||
            name ===
              "requirements.txt" ||
            name ===
              "pyproject.toml"
          );
        }
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
    !anchors.length
  ) {
    throw new Error(
      "Python web proje kökü bulunamadı."
    );
  }

  const manage =
    anchors.find(
      file =>
        path.basename(
          file
        )
          .toLowerCase() ===
        "manage.py"
    );

  if (
    manage
  ) {
    return path.dirname(
      manage
    );
  }

  return path.dirname(
    anchors[0]
  );
}

function databaseHints(
  dependencies,
  filesText
) {
  const names =
    new Set(
      dependencies
    );

  const hints =
    [];

  if (
    [
      "psycopg",
      "psycopg2",
      "asyncpg"
    ].some(
      name =>
        names.has(
          name
        )
    ) ||
    /postgres/i.test(
      filesText
    )
  ) {
    hints.push(
      "postgresql"
    );
  }

  if (
    [
      "mysqlclient",
      "pymysql"
    ].some(
      name =>
        names.has(
          name
        )
    ) ||
    /mysql/i.test(
      filesText
    )
  ) {
    hints.push(
      "mysql"
    );
  }

  return hints;
}

export async function preparePythonWebFrameworkSource({
  projectZip,
  workDir,
  technology = null,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "Python web kaynak ZIP'i eksik."
    );
  }

  if (
    cancelled
  ) {
    await cancelled();
  }

  const extractedRoot =
    path.join(
      workDir,
      "source"
    );

  const extracted =
    await extractPythonWebZip(
      projectZip,
      extractedRoot
    );

  const projectRoot =
    await findProjectRoot(
      extractedRoot
    );

  const files =
    await walk(
      projectRoot
    );

  const requirementsFile =
    files.find(
      file =>
        path.basename(
          file
        )
          .toLowerCase() ===
        "requirements.txt"
    ) ||
    null;

  const requirements =
    parsePythonRequirements(
      requirementsFile
        ? await readSmallText(
            requirementsFile
          )
        : ""
    );

  const django =
    await detectDjango(
      projectRoot,
      files
    );

  const flask =
    django
      ? null
      : await detectFlask(
          projectRoot,
          files
        );

  const contract =
    django ||
    flask;

  if (
    !contract
  ) {
    throw new Error(
      "Flask veya Django proje imzası bulunamadı."
    );
  }

  const framework =
    contract.framework;

  const sourcePreview =
    (
      await Promise.all(
        files
          .filter(
            file =>
              path.extname(
                file
              )
                .toLowerCase() ===
              ".py"
          )
          .slice(
            0,
            30
          )
          .map(
            file =>
              readSmallText(
                file
              )
          )
      )
    )
      .join(
        "\n"
      );

  const dbHints =
    databaseHints(
      requirements.dependencies,
      sourcePreview
    );

  if (
    onLog
  ) {
    await onLog(
      `🐍 ${framework} Android runtime foundation • ${contract.ready ? "giriş kontratı hazır" : "eksik"}`
    );
  }

  return {
    technology:
      technology ||
      (
        framework ===
          "flask"
          ? "python-flask"
          : "python-django"
      ),
    framework,
    projectRoot,
    contract,
    requirementsFile,
    dependencies:
      requirements.dependencies,
    ignoredRequirements:
      requirements.ignored,
    databaseHints:
      dbHints,
    localServerHost:
      "127.0.0.1",
    localServerPort:
      8765,
    runtimePlan:
      framework ===
        "flask"
        ? "chaquopy-flask-loopback-webview"
        : "chaquopy-django-wsgi-loopback-webview",
    buildReady:
      false,
    buildBlockedReason:
      "Chaquopy dependency install + Android loopback server/WebView canlı hattı sonraki aşamada bağlanacak.",
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}
