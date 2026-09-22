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

  fs.writeFileSync(
    exe,
    Buffer.from(
      [
        0x4d,
        0x5a,
        0x00,
        0x00
      ]
    )
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
