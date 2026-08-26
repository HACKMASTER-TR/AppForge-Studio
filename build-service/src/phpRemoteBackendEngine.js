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
      "vendor",
      "storage/logs",
      "cache",
      "tmp"
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
      "PHP projesinde çok fazla ZIP girdisi var."
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
        "PHP ZIP yolu güvenli değil."
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
        "PHP ZIP dizin dışına çıkmaya çalışıyor."
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
        "PHP ZIP hedef yolu güvenli değil."
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
        "PHP ZIP açıldığında boyut sınırını aşıyor."
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
    ) ||
    parts[0] ===
      0
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
    !host
  ) {
    return true;
  }

  if (
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

export function normalizePhpBackendUrl(
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
      "PHP remote backend URL eksik."
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
      "PHP remote backend URL geçersiz."
    );
  }

  if (
    url.protocol !==
      "https:"
  ) {
    throw new Error(
      "PHP remote backend yalnız HTTPS olabilir."
    );
  }

  if (
    url.username ||
    url.password
  ) {
    throw new Error(
      "PHP remote backend URL içinde kullanıcı adı/parola olamaz."
    );
  }

  if (
    blockedHostname(
      url.hostname
    )
  ) {
    throw new Error(
      "PHP remote backend localhost/private ağ adresi olamaz."
    );
  }

  if (
    url.search ||
    url.hash
  ) {
    throw new Error(
      "PHP remote backend ana URL query/hash içermemeli."
    );
  }

  url.pathname =
    url.pathname
      .replace(
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

export function normalizePhpHealthPath(
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
      "PHP healthPath güvenli bir relative path olmalı."
    );
  }

  return value;
}

function detectPhpFramework(
  composer,
  files
) {
  const require =
    {
      ...(
        composer
          ?.require ||
        {}
      ),
      ...(
        composer?.["require-dev"] ||
        {}
      )
    };

  const names =
    new Set(
      Object.keys(
        require
      )
        .map(
          value =>
            value.toLowerCase()
        )
    );

  if (
    names.has(
      "laravel/framework"
    ) ||
    files.some(
      file =>
        path.basename(
          file
        ) ===
          "artisan"
    )
  ) {
    return "laravel";
  }

  if (
    names.has(
      "symfony/framework-bundle"
    ) ||
    files.some(
      file =>
        file
          .replaceAll(
            "\\",
            "/"
          )
          .endsWith(
            "/bin/console"
          )
    )
  ) {
    return "symfony";
  }

  if (
    files.some(
      file =>
        path.basename(
          file
        )
          .toLowerCase() ===
        "wp-config.php"
    )
  ) {
    return "wordpress";
  }

  return "php";
}

async function findProjectRoot(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot
    );

  const composerFiles =
    files
      .filter(
        file =>
          path.basename(
            file
          )
            .toLowerCase() ===
          "composer.json"
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
    composerFiles.length
  ) {
    return {
      projectRoot:
        path.dirname(
          composerFiles[0]
        ),
      files
    };
  }

  const phpFiles =
    files
      .filter(
        file =>
          path.extname(
            file
          )
            .toLowerCase() ===
          ".php"
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
    !phpFiles.length
  ) {
    throw new Error(
      "PHP proje kökü bulunamadı."
    );
  }

  return {
    projectRoot:
      path.dirname(
        phpFiles[0]
      ),
    files
  };
}

export async function preparePhpRemoteBackendSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "PHP kaynak ZIP'i eksik."
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
    await extractZip(
      projectZip,
      extractedRoot
    );

  const found =
    await findProjectRoot(
      extractedRoot
    );

  const files =
    (
      await walk(
        found.projectRoot
      )
    );

  const composerFile =
    files.find(
      file =>
        path.basename(
          file
        )
          .toLowerCase() ===
        "composer.json"
    ) ||
    null;

  let composer =
    {};

  if (
    composerFile
  ) {
    try {
      composer =
        JSON.parse(
          await readSmallText(
            composerFile
          )
        );
    } catch {
      throw new Error(
        "composer.json geçerli JSON değil."
      );
    }
  }

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
          normalizePhpBackendUrl(
            raw?.backendUrl
          ),
        healthPath:
          normalizePhpHealthPath(
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
    detectPhpFramework(
      composer,
      files
    );

  if (
    onLog
  ) {
    await onLog(
      `🐘 PHP remote backend foundation • ${framework} • ${contract ? "HTTPS kontratı hazır" : "appforge.remote.json eksik"}`
    );
  }

  return {
    projectRoot:
      found.projectRoot,
    framework,
    composerFile,
    contractFile,
    contract,
    buildReady:
      false,
    buildBlockedReason:
      contract
        ? "HTTPS remote backend kontratı hazır. Android WebView wrapper canlı router bağlantısı sonraki aşamada açılacak."
        : "PHP Android içinde doğrudan çalıştırılmaz. appforge.remote.json ile önceden deploy edilmiş HTTPS backend URL'i gerekli.",
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}
