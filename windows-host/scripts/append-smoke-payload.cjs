"use strict";

const fs =
  require(
    "node:fs"
  );

const AdmZip =
  require(
    "adm-zip"
  );

const {
  FOOTER_MAGIC,
  PAYLOAD_MAGIC
} =
  require(
    "../payload.cjs"
  );


const executable =
  process.argv[2];

if (
  !executable
) {
  throw new Error(
    "EXE path required"
  );
}

const zip =
  new AdmZip();

zip.addFile(
  "index.html",
  Buffer.from(
    [
      "<!doctype html>",
      "<html>",
      "<head>",
      "<meta charset=\"utf-8\">",
      "<title>AppForge Host Smoke</title>",
      "</head>",
      "<body>",
      "<h1>APPFORGE_WINDOWS_HOST_SMOKE_OK</h1>",
      "</body>",
      "</html>"
    ].join(
      ""
    ),
    "utf8"
  )
);

const project =
  zip.toBuffer();

const manifest = {
  format:
    "appforge-project",

  formatVersion:
    1,

  producer:
    "AppForge Studio",

  platform:
    "windows",

  appName:
    "AppForge Windows Host Smoke",

  appId:
    "com.appforge.windows.smoke",

  versionName:
    "1.0.0",

  versionCode:
    1,

  sourceMode:
    "LOCAL",

  webUrl:
    "",

  projectRoot:
    "project.zip",

  startPage:
    "index.html",

  conversion: {
    apkToExe:
      true,

    exeToApk:
      true
  }
};

const manifestBytes =
  Buffer.from(
    JSON.stringify(
      manifest
    ),
    "utf8"
  );

const header =
  Buffer.alloc(
    12
  );

PAYLOAD_MAGIC.copy(
  header,
  0
);

header.writeUInt32BE(
  manifestBytes.length,
  8
);

const payloadLength =
  header.length +
  manifestBytes.length +
  project.length;

const footer =
  Buffer.alloc(
    8 +
    FOOTER_MAGIC.length
  );

footer.writeBigUInt64BE(
  BigInt(
    payloadLength
  ),
  0
);

FOOTER_MAGIC.copy(
  footer,
  8
);

fs.appendFileSync(
  executable,
  header
);

fs.appendFileSync(
  executable,
  manifestBytes
);

fs.appendFileSync(
  executable,
  project
);

fs.appendFileSync(
  executable,
  footer
);

console.log(
  "APPFORGE_SMOKE_PAYLOAD_APPENDED"
);
