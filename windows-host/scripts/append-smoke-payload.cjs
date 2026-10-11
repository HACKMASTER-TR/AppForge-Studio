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

const smokeHtml =
  `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>AppForge Host Smoke</title>
</head>
<body>
<h1>APPFORGE_WINDOWS_HOST_SMOKE_OK</h1>
<script>
(async () => {
  const localKey =
    "appforge-smoke-launch-count";

  const previousLocal =
    Number(
      localStorage.getItem(
        localKey
      ) || "0"
    );

  const localStorageCount =
    previousLocal + 1;

  localStorage.setItem(
    localKey,
    String(
      localStorageCount
    )
  );

  const database =
    await new Promise(
      (resolve, reject) => {
        const request =
          indexedDB.open(
            "appforge-smoke-db",
            1
          );

        request.onupgradeneeded =
          () => {
            const db =
              request.result;

            if (
              !db.objectStoreNames
                .contains(
                  "state"
                )
            ) {
              db.createObjectStore(
                "state"
              );
            }
          };

        request.onsuccess =
          () =>
            resolve(
              request.result
            );

        request.onerror =
          () =>
            reject(
              request.error
            );
      }
    );

  const indexedDbCount =
    await new Promise(
      (resolve, reject) => {
        const transaction =
          database.transaction(
            "state",
            "readwrite"
          );

        const store =
          transaction.objectStore(
            "state"
          );

        const request =
          store.get(
            "launchCount"
          );

        let next =
          1;

        request.onsuccess =
          () => {
            next =
              Number(
                request.result ||
                0
              ) + 1;

            store.put(
              next,
              "launchCount"
            );
          };

        request.onerror =
          () =>
            reject(
              request.error
            );

        transaction.oncomplete =
          () =>
            resolve(
              next
            );

        transaction.onerror =
          () =>
            reject(
              transaction.error
            );
      }
    );

  database.close();

  window.__APPFORGE_SMOKE_STATE__ = {
    ready:
      true,

    localStorageCount,
    indexedDbCount
  };
})().catch(
  error => {
    window.__APPFORGE_SMOKE_STATE__ = {
      ready:
        false,

      error:
        String(
          error?.message ||
          error
        )
    };
  }
);
</script>
</body>
</html>`;

zip.addFile(
  "index.html",
  Buffer.from(
    smokeHtml,
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
