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


/*
 * AUTHENTICODE_CERTIFICATE_TABLE_V1
 *
 * AppForge's project payload is appended to the Portable Host before
 * Authenticode signing.
 *
 * Authenticode signing appends a WIN_CERTIFICATE table after the existing
 * executable bytes. Therefore the physical EOF is no longer the AppForge
 * payload footer.
 *
 * IMAGE_DIRECTORY_ENTRY_SECURITY is special: its VirtualAddress field is
 * a physical file offset rather than an RVA. For a signed Portable EXE,
 * the AppForge logical payload end is immediately before that certificate
 * table, allowing only the normal 0..7 byte alignment padding.
 *
 * Unsigned Portable EXEs preserve the historical physical-EOF behavior.
 */
const SECURITY_DIRECTORY_INDEX =
  4;

const AUTHENTICODE_ALIGNMENT_BYTES =
  8;


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


function readUInt16Le(
  fd,
  position
) {
  return readExactly(
    fd,
    2,
    position
  ).readUInt16LE(
    0
  );
}


function readUInt32Le(
  fd,
  position
) {
  return readExactly(
    fd,
    4,
    position
  ).readUInt32LE(
    0
  );
}


function payloadLogicalEnd(
  fd,
  physicalEnd
) {
  /*
   * Preserve the old unsigned behavior unless this is recognizably
   * a PE image carrying an Authenticode Certificate Table.
   */
  if (
    physicalEnd <
      64
  ) {
    return physicalEnd;
  }

  const peOffset =
    readUInt32Le(
      fd,
      0x3c
    );

  if (
    peOffset <=
      0 ||
    peOffset +
      24 >
      physicalEnd
  ) {
    return physicalEnd;
  }

  const peSignature =
    readExactly(
      fd,
      4,
      peOffset
    );

  if (
    peSignature[0] !==
      0x50 ||
    peSignature[1] !==
      0x45 ||
    peSignature[2] !==
      0x00 ||
    peSignature[3] !==
      0x00
  ) {
    return physicalEnd;
  }

  const optionalHeaderSize =
    readUInt16Le(
      fd,
      peOffset +
        20
    );

  const optionalHeaderOffset =
    peOffset +
      24;

  const optionalHeaderEnd =
    optionalHeaderOffset +
      optionalHeaderSize;

  if (
    optionalHeaderSize <
      2 ||
    optionalHeaderEnd >
      physicalEnd
  ) {
    return physicalEnd;
  }

  const optionalMagic =
    readUInt16Le(
      fd,
      optionalHeaderOffset
    );

  let dataDirectoryOffset;

  if (
    optionalMagic ===
      0x20b
  ) {
    dataDirectoryOffset =
      112;

  } else if (
    optionalMagic ===
      0x10b
  ) {
    dataDirectoryOffset =
      96;

  } else {
    return physicalEnd;
  }

  const securityDirectoryOffset =
    optionalHeaderOffset +
      dataDirectoryOffset +
      SECURITY_DIRECTORY_INDEX *
        8;

  if (
    securityDirectoryOffset +
      8 >
      optionalHeaderEnd
  ) {
    return physicalEnd;
  }

  /*
   * IMAGE_DIRECTORY_ENTRY_SECURITY:
   *   DWORD VirtualAddress -> physical file offset
   *   DWORD Size
   */
  const certificateOffset =
    readUInt32Le(
      fd,
      securityDirectoryOffset
    );

  const certificateSize =
    readUInt32Le(
      fd,
      securityDirectoryOffset +
        4
    );

  if (
    certificateOffset ===
      0 &&
    certificateSize ===
      0
  ) {
    return physicalEnd;
  }

  if (
    certificateOffset <=
      0 ||
    certificateSize <=
      0 ||
    certificateOffset >
      physicalEnd ||
    certificateSize >
      physicalEnd -
        certificateOffset
  ) {
    throw new Error(
      "Windows Authenticode sertifika tablosu geçersiz."
    );
  }

  return certificateOffset;
}


function locatePayloadFooter(
  fd,
  physicalEnd
) {
  const logicalEnd =
    payloadLogicalEnd(
      fd,
      physicalEnd
    );

  const authenticodePresent =
    logicalEnd <
      physicalEnd;

  const maxPadding =
    authenticodePresent
      ? AUTHENTICODE_ALIGNMENT_BYTES -
          1
      : 0;

  for (
    let padding =
      0;
    padding <=
      maxPadding;
    padding +=
      1
  ) {
    const footerEnd =
      logicalEnd -
        padding;

    if (
      footerEnd <
        FOOTER_BYTES
    ) {
      continue;
    }

    /*
     * Authenticode Certificate Table is 8-byte aligned.
     * Only zero alignment bytes are accepted between the
     * AppForge footer and the certificate table.
     */
    if (
      padding >
        0
    ) {
      const alignment =
        readExactly(
          fd,
          padding,
          footerEnd
        );

      if (
        alignment.some(
          value =>
            value !==
              0
        )
      ) {
        continue;
      }
    }

    const footer =
      readExactly(
        fd,
        FOOTER_BYTES,
        footerEnd -
          FOOTER_BYTES
      );

    if (
      footer
        .subarray(
          8
        )
        .equals(
          FOOTER_MAGIC
        )
    ) {
      return {
        footer,
        footerEnd,
        authenticodePresent,
        padding
      };
    }
  }

  throw new Error(
    "AppForge Windows payload imzası bulunamadı."
  );
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

    const footerLocation =
      locatePayloadFooter(
        fd,
        stat.size
      );

    const footer =
      footerLocation
        .footer;

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
      footerLocation
        .footerEnd -
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
