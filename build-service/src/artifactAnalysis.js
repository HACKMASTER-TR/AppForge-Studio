import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import crypto from "crypto";
import {
  materializeOutput
} from "./storage.js";

const MAX_ENTRIES =
  30_000;

const TOP_FILE_COUNT =
  25;

function categoryFor(
  name
) {
  const lower =
    String(name)
      .toLowerCase();

  if (
    lower.endsWith(".dex")
  ) {
    return "dex";
  }

  if (
    lower.endsWith(".so")
  ) {
    return "native";
  }

  if (
    lower.match(
      /\.(png|jpg|jpeg|webp|gif|svg)$/
    )
  ) {
    return "images";
  }

  if (
    lower.match(
      /\.(ttf|otf|woff|woff2)$/
    )
  ) {
    return "fonts";
  }

  if (
    lower.endsWith(".js")
  ) {
    return "javascript";
  }

  if (
    lower.endsWith(".css")
  ) {
    return "css";
  }

  if (
    lower.match(
      /\.(html|htm)$/
    )
  ) {
    return "html";
  }

  if (
    lower.startsWith(
      "assets/"
    ) ||
    lower.includes(
      "/assets/"
    )
  ) {
    return "assets";
  }

  if (
    lower.includes(
      "resources.arsc"
    ) ||
    lower.startsWith(
      "res/"
    ) ||
    lower.includes(
      "/res/"
    )
  ) {
    return "resources";
  }

  if (
    lower.includes(
      "androidmanifest.xml"
    )
  ) {
    return "manifest";
  }

  return "other";
}

function safeEntryName(
  name
) {
  const value =
    String(name || "")
      .replaceAll("\\", "/")
      .replace(/^\/+/, "");

  if (
    value.includes("../") ||
    value === ".." ||
    value.length > 512
  ) {
    return null;
  }

  return value;
}

async function sha256File(
  file
) {
  const buffer =
    await fs.readFile(
      file
    );

  return crypto
    .createHash("sha256")
    .update(buffer)
    .digest("hex");
}

function securityChecks(
  build
) {
  const c =
    build.config ||
    {};

  const checks =
    [];

  const add = (
    severity,
    title,
    detail
  ) =>
    checks.push({
      severity,
      title,
      detail
    });

  if (
    String(
      c.sourceMode ||
      ""
    ).toUpperCase() ===
    "URL"
  ) {
    if (
      String(
        c.webUrl ||
        ""
      ).startsWith(
        "https://"
      )
    ) {
      add(
        "pass",
        "HTTPS içerik",
        "Uzak kaynak HTTPS kullanıyor."
      );
    } else {
      add(
        "block",
        "HTTPS içerik",
        "URL modu HTTPS kullanmıyor."
      );
    }
  } else {
    add(
      "pass",
      "Yerel paket",
      "HTML/ZIP uygulama içinde paketleniyor."
    );
  }

  if (
    c.nativeBridge
      ?.allowRemote
  ) {
    add(
      "warn",
      "Remote Native Bridge",
      "Uzak içerikte Native Bridge açık. Origin sınırlarını ayrıca doğrula."
    );
  } else {
    add(
      "pass",
      "Native Bridge origin",
      "Uzak Native Bridge kapalı veya yerel içerikle sınırlı."
    );
  }

  if (
    c.signing
      ?.mode ===
    "CUSTOM"
  ) {
    add(
      "pass",
      "Release imzalama",
      "Özel release keystore kullanılıyor."
    );
  } else {
    add(
      "warn",
      "Release imzalama",
      "Debug imza üretim / Play Store dağıtımı için uygun değildir."
    );
  }

  if (
    c.features
      ?.camera ||
    c.features
      ?.location ||
    c.features
      ?.notifications
  ) {
    add(
      "warn",
      "Hassas izinler",
      "Kamera/konum/bildirim izinleri için Play Data safety beyanını kontrol et."
    );
  } else {
    add(
      "pass",
      "Minimum izin",
      "Ek hassas çalışma zamanı izni seçilmemiş."
    );
  }

  if (
    c.billing
      ?.enabled &&
    !String(
      c.billing
        ?.verificationUrl ||
      ""
    ).startsWith(
      "https://"
    )
  ) {
    add(
      "block",
      "Billing doğrulaması",
      "Billing açık ancak HTTPS doğrulama URL'si eksik."
    );
  } else if (
    c.billing
      ?.enabled
  ) {
    add(
      "pass",
      "Billing doğrulaması",
      "Sunucu doğrulama URL'si HTTPS."
    );
  }

  if (
    c.branding
      ?.serverEnforced
  ) {
    add(
      "pass",
      "AppForge branding",
      c.branding
        ?.showWatermark
        ? "Free watermark sunucu tarafından zorlanıyor."
        : "Pro watermark kaldırma sunucu yetkisiyle doğrulandı."
    );
  }

  return checks;
}

async function analyzeArchive(
  outputRef,
  kind
) {
  if (!outputRef) {
    return null;
  }

  const tempDir =
    await fs.mkdtemp(
      path.join(
        os.tmpdir(),
        "appforge-analysis-"
      )
    );

  const tempFile =
    path.join(
      tempDir,
      kind === "aab"
        ? "artifact.aab"
        : "artifact.apk"
    );

  try {
    await materializeOutput(
      outputRef,
      tempFile
    );

    const stat =
      await fs.stat(
        tempFile
      );

    const zip =
      new AdmZip(
        tempFile
      );

    const entries =
      zip.getEntries();

    if (
      entries.length >
      MAX_ENTRIES
    ) {
      const error =
        new Error(
          "Artifact çok fazla ZIP girdisi içeriyor."
        );

      error.statusCode =
        422;

      throw error;
    }

    const groups =
      {};

    const files =
      [];

    let uncompressedBytes =
      0;

    let compressedBytes =
      0;

    for (
      const entry of entries
    ) {
      if (entry.isDirectory) {
        continue;
      }

      const name =
        safeEntryName(
          entry.entryName
        );

      if (!name) {
        continue;
      }

      const rawSize =
        Number(
          entry.header
            ?.size ||
          0
        );

      const compressedSize =
        Number(
          entry.header
            ?.compressedSize ||
          0
        );

      uncompressedBytes +=
        rawSize;

      compressedBytes +=
        compressedSize;

      const category =
        categoryFor(
          name
        );

      groups[category] =
        (
          groups[category] ||
          0
        ) +
        rawSize;

      files.push({
        path:
          name,
        category,
        sizeBytes:
          rawSize,
        compressedBytes:
          compressedSize
      });
    }

    files.sort(
      (
        a,
        b
      ) =>
        b.sizeBytes -
        a.sizeBytes
    );

    return {
      kind,
      name:
        outputRef.name ||
        path.basename(
          outputRef.key ||
          tempFile
        ),
      fileSizeBytes:
        stat.size,
      sha256:
        outputRef.sha256 ||
        await sha256File(
          tempFile
        ),
      entryCount:
        files.length,
      uncompressedBytes,
      compressedBytes,
      compressionRatio:
        uncompressedBytes > 0
          ? Number(
              (
                stat.size /
                uncompressedBytes
              ).toFixed(4)
            )
          : 1,
      groups,
      topFiles:
        files.slice(
          0,
          TOP_FILE_COUNT
        )
    };
  } finally {
    await fs.rm(
      tempDir,
      {
        recursive: true,
        force: true
      }
    );
  }
}

export async function analyzeBuildArtifacts(
  build
) {
  if (
    build.status !==
    "success"
  ) {
    const error =
      new Error(
        "Artifact analizi için build başarılı olmalı."
      );

    error.statusCode =
      409;

    throw error;
  }

  const outputs =
    build.outputs ||
    {};

  const [
    apk,
    aab
  ] =
    await Promise.all([
      analyzeArchive(
        outputs.apk,
        "apk"
      ),
      analyzeArchive(
        outputs.aab,
        "aab"
      )
    ]);

  return {
    buildId:
      build.id,
    appName:
      build.app_name,
    packageName:
      build.package_name,
    versionName:
      build.config
        ?.versionName ||
      null,
    versionCode:
      build.config
        ?.versionCode ||
      null,
    generatedAt:
      new Date()
        .toISOString(),
    security:
      securityChecks(
        build
      ),
    apk,
    aab
  };
}
