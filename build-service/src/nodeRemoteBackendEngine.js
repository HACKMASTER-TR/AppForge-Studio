import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import net from "net";
import path from "path";

const MAX_ZIP_ENTRIES =
  14_000;

const MAX_UNCOMPRESSED_BYTES =
  300 * 1024 * 1024;

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

function ignored(
  segments
) {
  const names =
    new Set([
      ".git",
      ".idea",
      ".vscode",
      "node_modules",
      ".next",
      ".nuxt",
      "dist",
      "build",
      "coverage"
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
      "Node.js backend projesinde çok fazla ZIP girdisi var."
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
        "Node.js backend ZIP yolu güvenli değil."
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
        "Node.js backend ZIP dizin dışına çıkmaya çalışıyor."
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
        "Node.js backend ZIP hedef yolu güvenli değil."
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
        "Node.js backend ZIP açıldığında boyut sınırını aşıyor."
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

function privateIpv4(
  host
) {
  const parts =
    host
      .split(
        "."
      )
      .map(
        Number
      );

  if (
    parts.length !==
      4 ||
    parts.some(
      value =>
        !Number.isInteger(
          value
        ) ||
        value <
          0 ||
        value >
          255
    )
  ) {
    return false;
  }

  return (
    parts[0] ===
      10 ||
    parts[0] ===
      127 ||
    parts[0] ===
      0 ||
    (
      parts[0] ===
        169 &&
      parts[1] ===
        254
    ) ||
    (
      parts[0] ===
        172 &&
      parts[1] >=
        16 &&
      parts[1] <=
        31
    ) ||
    (
      parts[0] ===
        192 &&
      parts[1] ===
        168
    )
  );
}

function blockedHostname(
  hostname
) {
  const host =
    String(
      hostname ||
      ""
    )
      .trim()
      .toLowerCase()
      .replace(
        /\.$/,
        ""
      );

  if (
    !host ||
    host ===
      "localhost" ||
    host.endsWith(
      ".localhost"
    ) ||
    host.endsWith(
      ".local"
    )
  ) {
    return true;
  }

  const ipType =
    net.isIP(
      host
    );

  if (
    ipType ===
      4
  ) {
    return privateIpv4(
      host
    );
  }

  if (
    ipType ===
      6
  ) {
    return (
      host ===
        "::1" ||
      host.startsWith(
        "fc"
      ) ||
      host.startsWith(
        "fd"
      ) ||
      host.startsWith(
        "fe8"
      ) ||
      host.startsWith(
        "fe9"
      ) ||
      host.startsWith(
        "fea"
      ) ||
      host.startsWith(
        "feb"
      )
    );
  }

  return false;
}

export function normalizeNodeBackendUrl(
  rawUrl
) {
  const value =
    String(
      rawUrl ||
      ""
    )
      .trim();

  if (
    !value
  ) {
    throw new Error(
      "Node.js remote backend URL eksik."
    );
  }

  let url;

  try {
    url =
      new URL(
        value
      );
  } catch {
    throw new Error(
      "Node.js remote backend URL geçersiz."
    );
  }

  if (
    url.protocol !==
      "https:"
  ) {
    throw new Error(
      "Node.js remote backend yalnız HTTPS olabilir."
    );
  }

  if (
    url.username ||
    url.password
  ) {
    throw new Error(
      "Node.js remote backend URL içinde kullanıcı adı/parola olamaz."
    );
  }

  if (
    blockedHostname(
      url.hostname
    )
  ) {
    throw new Error(
      "Node.js remote backend localhost/private ağ adresi olamaz."
    );
  }

  if (
    url.search ||
    url.hash
  ) {
    throw new Error(
      "Node.js remote backend ana URL query/hash içermemeli."
    );
  }

  url.pathname =
    url.pathname.replace(
      /\/+/g,
      "/"
    );

  if (
    !url.pathname.endsWith(
      "/"
    )
  ) {
    url.pathname +=
      "/";
  }

  return url.toString();
}

export function normalizeNodeHealthPath(
  rawPath
) {
  const value =
    String(
      rawPath ||
      "/"
    )
      .trim();

  if (
    !value.startsWith(
      "/"
    ) ||
    value.includes(
      ".."
    ) ||
    value.includes(
      "\\"
    ) ||
    value.includes(
      "://"
    )
  ) {
    throw new Error(
      "Node.js healthPath güvenli bir relative path olmalı."
    );
  }

  return value;
}

function detectNodeBackendFramework(
  packageJson
) {
  const dependencies =
    {
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

  const names =
    new Set(
      Object.keys(
        dependencies
      )
        .map(
          value =>
            value.toLowerCase()
        )
    );

  if (
    names.has(
      "@nestjs/core"
    )
  ) {
    return "nestjs";
  }

  if (
    names.has(
      "fastify"
    )
  ) {
    return "fastify";
  }

  if (
    names.has(
      "koa"
    )
  ) {
    return "koa";
  }

  if (
    names.has(
      "@hapi/hapi"
    )
  ) {
    return "hapi";
  }

  if (
    names.has(
      "express"
    )
  ) {
    return "express";
  }

  return "nodejs";
}

async function findPackageRoot(
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

  if (
    !packages.length
  ) {
    throw new Error(
      "Node.js backend projesinde package.json bulunamadı."
    );
  }

  return {
    projectRoot:
      path.dirname(
        packages[0]
      ),
    packageFile:
      packages[0],
    files
  };
}

export async function prepareNodeRemoteBackendSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "Node.js backend kaynak ZIP'i eksik."
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

  const found =
    await findPackageRoot(
      sourceRoot
    );

  let packageJson;

  try {
    packageJson =
      JSON.parse(
        await readSmallText(
          found.packageFile
        )
      );
  } catch {
    throw new Error(
      "Node.js backend package.json geçerli JSON değil."
    );
  }

  const files =
    await walk(
      found.projectRoot
    );

  const contractFile =
    files.find(
      file =>
        path.basename(
          file
        )
          .toLowerCase() ===
        "appforge.remote.json"
    ) ||
    null;

  let contract =
    null;

  if (
    contractFile
  ) {
    try {
      const raw =
        JSON.parse(
          await readSmallText(
            contractFile
          )
        );

      contract = {
        backendUrl:
          normalizeNodeBackendUrl(
            raw?.backendUrl
          ),
        healthPath:
          normalizeNodeHealthPath(
            raw?.healthPath
          ),
        openExternalLinks:
          raw?.openExternalLinks !==
            false
      };
    } catch (
      error
    ) {
      throw new Error(
        `appforge.remote.json geçersiz: ${String(error?.message || error)}`
      );
    }
  }

  const framework =
    detectNodeBackendFramework(
      packageJson
    );

  const scripts =
    packageJson
      ?.scripts ||
    {};

  const startScript =
    typeof scripts.start ===
      "string" &&
    scripts.start.trim()
      ? scripts.start.trim()
      : null;

  if (
    onLog
  ) {
    await onLog(
      `🟢 Node.js remote backend foundation • ${framework} • ${contract ? "HTTPS kontratı hazır" : "appforge.remote.json eksik"}`
    );
  }

  return {
    projectRoot:
      found.projectRoot,
    packageFile:
      found.packageFile,
    packageJson,
    framework,
    startScript,
    contractFile,
    contract,
    buildReady:
      false,
    buildBlockedReason:
      contract
        ? "HTTPS remote backend kontratı hazır. Android WebView wrapper canlı router bağlantısı sonraki aşamada açılacak."
        : "Node.js backend Android içinde sunucu olarak çalıştırılmaz. appforge.remote.json ile önceden deploy edilmiş public HTTPS backend URL'i gerekli.",
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}

export function applyNodeRemoteBackendConfig(
  buildConfig,
  prepared
) {
  const backendUrl =
    prepared
      ?.contract
      ?.backendUrl;

  if (
    !backendUrl
  ) {
    throw new Error(
      "Node.js remote backend kontratı hazır değil."
    );
  }

  const normalizedUrl =
    normalizeNodeBackendUrl(
      backendUrl
    );

  buildConfig.sourceMode =
    "URL";

  buildConfig.webUrl =
    normalizedUrl;

  buildConfig.nodeRemoteBackend =
    {
      framework:
        prepared.framework,
      backendUrl:
        normalizedUrl,
      healthPath:
        prepared.contract.healthPath,
      openExternalLinks:
        prepared.contract.openExternalLinks
    };

  return buildConfig;
}
