"use strict";

const fs =
  require(
    "node:fs"
  );

const path =
  require(
    "node:path"
  );

const AdmZip =
  require(
    "adm-zip"
  );

const FOOTER_MAGIC =
  Buffer.from(
    "APPFORGE-EXE-V1!",
    "ascii"
  );

const PAYLOAD_MAGIC =
  Buffer.from(
    "AFEXEP01",
    "ascii"
  );

const FOOTER_BYTES =
  8 +
  FOOTER_MAGIC.length;

const HEADER_BYTES =
  12;

const MAX_PAYLOAD_BYTES =
  512 * 1024 * 1024;

const MAX_MANIFEST_BYTES =
  256 * 1024;

const MAX_SITE_BYTES =
  500 * 1024 * 1024;

const MAX_SITE_FILES =
  10000;


function readExactly(
  fd,
  length,
  position
) {
  const out =
    Buffer.alloc(
      length
    );

  let offset =
    0;

  while (
    offset <
    length
  ) {
    const read =
      fs.readSync(
        fd,
        out,
        offset,
        length - offset,
        position + offset
      );

    if (
      read <=
      0
    ) {
      throw new Error(
        "AppForge EXE payload beklenmedik biçimde sona erdi."
      );
    }

    offset +=
      read;
  }

  return out;
}


function safeRelativePath(
  value
) {
  const text =
    String(
      value ||
      ""
    )
      .replaceAll(
        "\\",
        "/"
      )
      .trim();

  if (
    !text ||
    text.startsWith(
      "/"
    ) ||
    text.includes(
      "\0"
    ) ||
    /^[A-Za-z]:/.test(
      text
    )
  ) {
    throw new Error(
      "AppForge payload dosya yolu geçersiz."
    );
  }

  const parts =
    text
      .split(
        "/"
      )
      .filter(
        Boolean
      );

  if (
    !parts.length ||
    parts.some(
      part =>
        part ===
          ".." ||
        part ===
          "."
    )
  ) {
    throw new Error(
      "AppForge payload path traversal engellendi."
    );
  }

  return parts.join(
    "/"
  );
}


function validateManifest(
  manifest
) {
  if (
    manifest?.format !==
      "appforge-project" ||
    manifest?.formatVersion !==
      1 ||
    manifest?.producer !==
      "AppForge Studio" ||
    manifest?.platform !==
      "windows"
  ) {
    throw new Error(
      "Geçersiz AppForge Windows manifesti."
    );
  }

  const appName =
    String(
      manifest.appName ||
      ""
    ).trim();

  if (
    !appName ||
    appName.length >
      120
  ) {
    throw new Error(
      "Windows uygulama adı geçersiz."
    );
  }

  const appId =
    String(
      manifest.appId ||
      ""
    ).trim();

  if (
    !/^[A-Za-z_]\w*(\.[A-Za-z_]\w*)+$/
      .test(
        appId
      )
  ) {
    throw new Error(
      "Windows app ID geçersiz."
    );
  }

  if (
    manifest.sourceMode !==
      "LOCAL" &&
    manifest.sourceMode !==
      "URL"
  ) {
    throw new Error(
      "Windows kaynak modu geçersiz."
    );
  }

  if (
    manifest.sourceMode ===
      "URL"
  ) {
    if (
      !/^https:\/\//i
        .test(
          String(
            manifest.webUrl ||
            ""
          )
        )
    ) {
      throw new Error(
        "Windows URL modu HTTPS gerektirir."
      );
    }
  } else {
    safeRelativePath(
      manifest.startPage
    );

    if (
      manifest.projectRoot !==
        "project.zip"
    ) {
      throw new Error(
        "Yerel Windows payload proje tanımı geçersiz."
      );
    }
  }

  return manifest;
}


function readPayloadMetadata(
  executable
) {
  const stat =
    fs.statSync(
      executable
    );

  if (
    stat.size <
      FOOTER_BYTES +
        HEADER_BYTES +
        2
  ) {
    throw new Error(
      "AppForge Windows payload bulunamadı."
    );
  }

  const fd =
    fs.openSync(
      executable,
      "r"
    );

  try {
    const mz =
      readExactly(
        fd,
        2,
        0
      );

    if (
      mz[0] !==
        0x4d ||
      mz[1] !==
        0x5a
    ) {
      throw new Error(
        "Windows host MZ başlığı geçersiz."
      );
    }

    const footer =
      readExactly(
        fd,
        FOOTER_BYTES,
        stat.size -
          FOOTER_BYTES
      );

    const foundMagic =
      footer.subarray(
        8
      );

    if (
      !foundMagic.equals(
        FOOTER_MAGIC
      )
    ) {
      throw new Error(
        "AppForge Windows payload imzası bulunamadı."
      );
    }

    const payloadLengthBig =
      footer.readBigUInt64BE(
        0
      );

    if (
      payloadLengthBig <=
        0n ||
      payloadLengthBig >
        BigInt(
          MAX_PAYLOAD_BYTES
        )
    ) {
      throw new Error(
        "AppForge Windows payload boyutu geçersiz."
      );
    }

    const payloadLength =
      Number(
        payloadLengthBig
      );

    const payloadOffset =
      stat.size -
      FOOTER_BYTES -
      payloadLength;

    if (
      payloadOffset <
        2
    ) {
      throw new Error(
        "AppForge Windows payload konumu geçersiz."
      );
    }

    const header =
      readExactly(
        fd,
        HEADER_BYTES,
        payloadOffset
      );

    if (
      !header
        .subarray(
          0,
          8
        )
        .equals(
          PAYLOAD_MAGIC
        )
    ) {
      throw new Error(
        "AppForge Windows payload başlığı geçersiz."
      );
    }

    const manifestLength =
      header.readUInt32BE(
        8
      );

    if (
      manifestLength <=
        0 ||
      manifestLength >
        MAX_MANIFEST_BYTES ||
      HEADER_BYTES +
        manifestLength >
        payloadLength
    ) {
      throw new Error(
        "AppForge Windows manifest boyutu geçersiz."
      );
    }

    const manifestBytes =
      readExactly(
        fd,
        manifestLength,
        payloadOffset +
          HEADER_BYTES
      );

    const manifest =
      validateManifest(
        JSON.parse(
          manifestBytes.toString(
            "utf8"
          )
        )
      );

    const projectOffset =
      payloadOffset +
      HEADER_BYTES +
      manifestLength;

    const projectLength =
      payloadLength -
      HEADER_BYTES -
      manifestLength;

    if (
      manifest.sourceMode ===
        "LOCAL" &&
      projectLength <=
        0
    ) {
      throw new Error(
        "Yerel Windows proje payload'ı eksik."
      );
    }

    if (
      manifest.sourceMode ===
        "URL" &&
      projectLength !==
        0
    ) {
      throw new Error(
        "URL tabanlı Windows payload beklenmeyen proje verisi içeriyor."
      );
    }

    return {
      manifest,
      projectOffset,
      projectLength
    };

  } finally {
    fs.closeSync(
      fd
    );
  }
}


function copyProjectZip(
  executable,
  target,
  offset,
  length
) {
  const input =
    fs.openSync(
      executable,
      "r"
    );

  const output =
    fs.openSync(
      target,
      "w"
    );

  const buffer =
    Buffer.alloc(
      256 * 1024
    );

  let position =
    offset;

  let remaining =
    length;

  try {
    while (
      remaining >
      0
    ) {
      const wanted =
        Math.min(
          buffer.length,
          remaining
        );

      const read =
        fs.readSync(
          input,
          buffer,
          0,
          wanted,
          position
        );

      if (
        read <=
          0
      ) {
        throw new Error(
          "Windows proje payload'ı okunamadı."
        );
      }

      fs.writeSync(
        output,
        buffer,
        0,
        read
      );

      position +=
        read;

      remaining -=
        read;
    }
  } finally {
    fs.closeSync(
      input
    );

    fs.closeSync(
      output
    );
  }
}


function extractZipSafely(
  zipFile,
  siteRoot
) {
  const zip =
    new AdmZip(
      zipFile
    );

  const entries =
    zip.getEntries();

  if (
    entries.length >
      MAX_SITE_FILES
  ) {
    throw new Error(
      "Windows projesi çok fazla dosya içeriyor."
    );
  }

  fs.mkdirSync(
    siteRoot,
    {
      recursive:
        true
    }
  );

  const canonicalRoot =
    path.resolve(
      siteRoot
    );

  let totalBytes =
    0;

  for (
    const entry of
    entries
  ) {
    const relative =
      safeRelativePath(
        entry.entryName
      );

    if (
      entry.isDirectory
    ) {
      const directory =
        path.resolve(
          siteRoot,
          relative
        );

      if (
        directory !==
          canonicalRoot &&
        !directory.startsWith(
          canonicalRoot +
            path.sep
        )
      ) {
        throw new Error(
          "Windows ZIP path traversal engellendi."
        );
      }

      fs.mkdirSync(
        directory,
        {
          recursive:
            true
        }
      );

      continue;
    }

    const size =
      Number(
        entry.header?.size ||
        0
      );

    totalBytes +=
      size;

    if (
      totalBytes >
        MAX_SITE_BYTES
    ) {
      throw new Error(
        "Windows proje içeriği 500 MB sınırını aşıyor."
      );
    }

    const target =
      path.resolve(
        siteRoot,
        relative
      );

    if (
      target !==
        canonicalRoot &&
      !target.startsWith(
        canonicalRoot +
          path.sep
      )
    ) {
      throw new Error(
        "Windows ZIP path traversal engellendi."
      );
    }

    fs.mkdirSync(
      path.dirname(
        target
      ),
      {
        recursive:
          true
      }
    );

    fs.writeFileSync(
      target,
      entry.getData()
    );
  }
}


function materializeAppForgePayload(
  executable,
  runtimeRoot
) {
  const metadata =
    readPayloadMetadata(
      executable
    );

  fs.rmSync(
    runtimeRoot,
    {
      recursive:
        true,
      force:
        true
    }
  );

  fs.mkdirSync(
    runtimeRoot,
    {
      recursive:
        true
    }
  );

  if (
    metadata
      .manifest
      .sourceMode ===
      "URL"
  ) {
    return {
      manifest:
        metadata.manifest,

      siteRoot:
        null
    };
  }

  const projectZip =
    path.join(
      runtimeRoot,
      "project.zip"
    );

  copyProjectZip(
    executable,
    projectZip,
    metadata.projectOffset,
    metadata.projectLength
  );

  const siteRoot =
    path.join(
      runtimeRoot,
      "site"
    );

  extractZipSafely(
    projectZip,
    siteRoot
  );

  fs.rmSync(
    projectZip,
    {
      force:
        true
    }
  );

  const startPage =
    safeRelativePath(
      metadata
        .manifest
        .startPage
    );

  const startFile =
    path.resolve(
      siteRoot,
      startPage
    );

  const root =
    path.resolve(
      siteRoot
    );

  if (
    !startFile.startsWith(
      root +
        path.sep
    ) ||
    !fs.existsSync(
      startFile
    ) ||
    !fs.statSync(
      startFile
    ).isFile()
  ) {
    throw new Error(
      "Windows başlangıç sayfası bulunamadı."
    );
  }

  return {
    manifest:
      metadata.manifest,

    siteRoot
  };
}


module.exports = {
  FOOTER_MAGIC,
  PAYLOAD_MAGIC,
  readPayloadMetadata,
  materializeAppForgePayload,
  safeRelativePath
};
