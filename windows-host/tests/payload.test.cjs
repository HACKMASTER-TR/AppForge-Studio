"use strict";

const test =
  require(
    "node:test"
  );

const assert =
  require(
    "node:assert/strict"
  );

const fs =
  require(
    "node:fs"
  );

const os =
  require(
    "node:os"
  );

const path =
  require(
    "node:path"
  );

const AdmZip =
  require(
    "adm-zip"
  );

const {
  FOOTER_MAGIC,
  PAYLOAD_MAGIC,
  materializeAppForgePayload,
  safeRelativePath
} =
  require(
    "../payload.cjs"
  );


function makePayloadExe(
  root,
  entries
) {
  const exe =
    path.join(
      root,
      "host.exe"
    );

  /*
   * Minimal PE32+ shaped host.
   *
   * It is not executed by this unit test; it contains only the PE
   * structures required to model IMAGE_DIRECTORY_ENTRY_SECURITY.
   */
  const host =
    Buffer.alloc(
      512
    );

  host.write(
    "MZ",
    0,
    "ascii"
  );

  const peOffset =
    0x80;

  host.writeUInt32LE(
    peOffset,
    0x3c
  );

  host.write(
    "PE\0\0",
    peOffset,
    "binary"
  );

  host.writeUInt16LE(
    0x8664,
    peOffset +
      4
  );

  host.writeUInt16LE(
    1,
    peOffset +
      6
  );

  host.writeUInt16LE(
    0x00f0,
    peOffset +
      20
  );

  const optionalHeaderOffset =
    peOffset +
      24;

  host.writeUInt16LE(
    0x020b,
    optionalHeaderOffset
  );

  /*
   * NumberOfRvaAndSizes = 16.
   */
  host.writeUInt32LE(
    16,
    optionalHeaderOffset +
      108
  );

  fs.writeFileSync(
    exe,
    host
  );

  const zip =
    new AdmZip();

  for (
    const [
      name,
      contents
    ] of entries
  ) {
    zip.addFile(
      name,
      Buffer.from(
        contents,
        "utf8"
      )
    );
  }

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
      "Test",

    appId:
      "com.appforge.test",

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
      "index.html"
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
    exe,
    header
  );

  fs.appendFileSync(
    exe,
    manifestBytes
  );

  fs.appendFileSync(
    exe,
    project
  );

  fs.appendFileSync(
    exe,
    footer
  );

  return exe;
}


function appendMockAuthenticodeCertificate(
  executable
) {
  const before =
    fs.statSync(
      executable
    ).size;

  const padding =
    (
      8 -
      (
        before %
        8
      )
    ) %
      8;

  const certificateOffset =
    before +
      padding;

  /*
   * Minimal WIN_CERTIFICATE-shaped blob.
   * dwLength=16, wRevision=WIN_CERT_REVISION_2_0,
   * wCertificateType=WIN_CERT_TYPE_PKCS_SIGNED_DATA.
   */
  const certificate =
    Buffer.alloc(
      16
    );

  certificate.writeUInt32LE(
    certificate.length,
    0
  );

  certificate.writeUInt16LE(
    0x0200,
    4
  );

  certificate.writeUInt16LE(
    0x0002,
    6
  );

  if (
    padding >
      0
  ) {
    fs.appendFileSync(
      executable,
      Buffer.alloc(
        padding
      )
    );
  }

  fs.appendFileSync(
    executable,
    certificate
  );

  const fd =
    fs.openSync(
      executable,
      "r+"
    );

  try {
    const peOffset =
      0x80;

    const optionalHeaderOffset =
      peOffset +
        24;

    const securityDirectoryOffset =
      optionalHeaderOffset +
        112 +
        4 *
          8;

    const directory =
      Buffer.alloc(
        8
      );

    directory.writeUInt32LE(
      certificateOffset,
      0
    );

    directory.writeUInt32LE(
      certificate.length,
      4
    );

    fs.writeSync(
      fd,
      directory,
      0,
      directory.length,
      securityDirectoryOffset
    );

  } finally {
    fs.closeSync(
      fd
    );
  }
}


test(
  "portable host materializes valid AppForge payload",
  () => {
    const root =
      fs.mkdtempSync(
        path.join(
          os.tmpdir(),
          "appforge-win-host-"
        )
      );

    try {
      const exe =
        makePayloadExe(
          root,
          [
            [
              "index.html",
              "<h1>OK</h1>"
            ]
          ]
        );

      const result =
        materializeAppForgePayload(
          exe,
          path.join(
            root,
            "runtime"
          )
        );

      assert.equal(
        result.manifest.appId,
        "com.appforge.test"
      );

      assert.equal(
        fs.readFileSync(
          path.join(
            result.siteRoot,
            "index.html"
          ),
          "utf8"
        ),
        "<h1>OK</h1>"
      );

    } finally {
      fs.rmSync(
        root,
        {
          recursive:
            true,
          force:
            true
        }
      );
    }
  }
);


test(
  "portable host reads AppForge payload before Authenticode certificate table",
  () => {
    const root =
      fs.mkdtempSync(
        path.join(
          os.tmpdir(),
          "appforge-win-authenticode-"
        )
      );

    try {
      const exe =
        makePayloadExe(
          root,
          [
            [
              "index.html",
              "<h1>SIGNED_OK</h1>"
            ]
          ]
        );

      appendMockAuthenticodeCertificate(
        exe
      );

      const bytes =
        fs.readFileSync(
          exe
        );

      assert.equal(
        bytes
          .subarray(
            -FOOTER_MAGIC.length
          )
          .equals(
            FOOTER_MAGIC
          ),
        false,
        "Authenticode certificate data must move the physical EOF past the AppForge footer"
      );

      const result =
        materializeAppForgePayload(
          exe,
          path.join(
            root,
            "runtime"
          )
        );

      assert.equal(
        result.manifest.appId,
        "com.appforge.test"
      );

      assert.equal(
        fs.readFileSync(
          path.join(
            result.siteRoot,
            "index.html"
          ),
          "utf8"
        ),
        "<h1>SIGNED_OK</h1>"
      );

    } finally {
      fs.rmSync(
        root,
        {
          recursive:
            true,
          force:
            true
        }
      );
    }
  }
);


test(
  "portable host rejects traversal paths",
  () => {
    assert.throws(
      () =>
        safeRelativePath(
          "../evil.html"
        ),
      /traversal/
    );

    assert.throws(
      () =>
        safeRelativePath(
          "C:\\evil.html"
        ),
      /geçersiz/
    );
  }
);
