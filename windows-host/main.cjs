"use strict";

const {
  app,
  BrowserWindow,
  dialog,
  net,
  protocol,
  shell
} =
  require(
    "electron"
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

const crypto =
  require(
    "node:crypto"
  );

const {
  pathToFileURL
} =
  require(
    "node:url"
  );


const {
  materializeAppForgePayload,
  readPayloadMetadata,
  safeRelativePath
} =
  require(
    "./payload.cjs"
  );

const {
  persistentUserDataPath,
  runtimeDirectoryName
} =
  require(
    "./storage.cjs"
  );


const LOCAL_SCHEME =
  "appforge";

const LOCAL_HOST =
  "app";


const STORAGE_DURABILITY_FLUSH_MS =
  500;


protocol.registerSchemesAsPrivileged(
  [
    {
      scheme:
        LOCAL_SCHEME,

      privileges: {
        standard:
          true,

        secure:
          true,

        supportFetchAPI:
          true,

        corsEnabled:
          true,

        allowServiceWorkers:
          true,

        stream:
          true,

        codeCache:
          true
      }
    }
  ]
);


function portableExecutable() {
  const candidate =
    String(
      process.env
        .PORTABLE_EXECUTABLE_FILE ||
      process.execPath ||
      ""
    ).trim();

  if (
    !candidate
  ) {
    throw new Error(
      "Portable EXE yolu bulunamadı."
    );
  }

  return path.resolve(
    candidate
  );
}


const portableFile =
  portableExecutable();

let runtimeRoot =
  null;

let payload =
  null;

let startupError =
  null;

let persistentSessionData =
  null;

let ownsSingleInstance =
  false;

try {
  /*
   * APPFORGE_WINDOWS_PERSISTENT_USERDATA_V23
   *
   * Payload/site extraction remains disposable.
   * Browser profile data survives relaunch and EXE updates
   * for the same manifest appId.
   */
  const metadata =
    readPayloadMetadata(
      portableFile
    );

  const appId =
    metadata
      .manifest
      .appId;

  const userDataRoot =
    persistentUserDataPath(
      appId
    );

  fs.mkdirSync(
    userDataRoot,
    {
      recursive:
        true
    }
  );

  app.setPath(
    "userData",
    userDataRoot
  );

  persistentSessionData =
    path.join(
      userDataRoot,
      "session-data"
    );

  fs.mkdirSync(
    persistentSessionData,
    {
      recursive:
        true
    }
  );

  app.setPath(
    "sessionData",
    persistentSessionData
  );

  app.setAppUserModelId(
    appId
  );

  ownsSingleInstance =
    app.requestSingleInstanceLock();

  if (
    ownsSingleInstance
  ) {
    runtimeRoot =
      path.join(
        os.tmpdir(),
        "AppForgePortableHost",
        runtimeDirectoryName(
          appId
        )
      );

    payload =
      materializeAppForgePayload(
        portableFile,
        runtimeRoot
      );
  } else {
    app.quit();
  }

} catch (
  error
) {
  startupError =
    error;
}


function cleanRuntime() {
  if (
    !runtimeRoot
  ) {
    return;
  }

  try {
    fs.rmSync(
      runtimeRoot,
      {
        recursive:
          true,
        force:
          true
      }
    );
  } catch {}
}


function localAppUrl(
  startPage
) {
  const relative =
    safeRelativePath(
      startPage
    );

  const encoded =
    relative
      .split(
        "/"
      )
      .map(
        part =>
          encodeURIComponent(
            part
          )
      )
      .join(
        "/"
      );

  return (
    `${LOCAL_SCHEME}://` +
    `${LOCAL_HOST}/` +
    encoded
  );
}


function isLocalAppUrl(
  value
) {
  try {
    const parsed =
      new URL(
        value
      );

    return (
      parsed.protocol ===
        `${LOCAL_SCHEME}:` &&
      parsed.hostname ===
        LOCAL_HOST
    );

  } catch {
    return false;
  }
}


function resolveLocalRequestFile(
  requestUrl
) {
  if (
    !payload ||
    payload
      .manifest
      .sourceMode !==
      "LOCAL"
  ) {
    return null;
  }

  let parsed;

  try {
    parsed =
      new URL(
        requestUrl
      );
  } catch {
    return null;
  }

  if (
    parsed.protocol !==
      `${LOCAL_SCHEME}:` ||
    parsed.hostname !==
      LOCAL_HOST
  ) {
    return null;
  }

  let pathname;

  try {
    pathname =
      decodeURIComponent(
        parsed.pathname
      );
  } catch {
    return null;
  }

  const requested =
    pathname
      .replace(
        /^\/+/
        ,
        ""
      )
      .trim();

  let relative;

  try {
    relative =
      safeRelativePath(
        requested ||
          payload
            .manifest
            .startPage
      );
  } catch {
    return null;
  }

  const root =
    path.resolve(
      payload.siteRoot
    );

  const target =
    path.resolve(
      root,
      relative
    );

  const relativeToRoot =
    path.relative(
      root,
      target
    );

  if (
    !relativeToRoot ||
    relativeToRoot.startsWith(
      ".."
    ) ||
    path.isAbsolute(
      relativeToRoot
    )
  ) {
    return null;
  }

  try {
    if (
      !fs.statSync(
        target
      ).isFile()
    ) {
      return null;
    }
  } catch {
    return null;
  }

  return target;
}


async function registerLocalProtocol() {
  if (
    !payload ||
    payload
      .manifest
      .sourceMode !==
      "LOCAL"
  ) {
    return;
  }

  protocol.handle(
    LOCAL_SCHEME,
    request => {
      const file =
        resolveLocalRequestFile(
          request.url
        );

      if (
        !file
      ) {
        return new Response(
          "Not Found",
          {
            status:
              404,

            headers: {
              "content-type":
                "text/plain; charset=utf-8"
            }
          }
        );
      }

      return net.fetch(
        pathToFileURL(
          file
        ).toString()
      );
    }
  );
}


function flushStorageData(
  window
) {
  if (
    !window ||
    window.isDestroyed()
  ) {
    return;
  }

  try {
    window
      .webContents
      .session
      .flushStorageData();
  } catch {}
}


function startStorageDurabilityFlush(
  window
) {
  flushStorageData(
    window
  );

  const timer =
    setInterval(
      () => {
        flushStorageData(
          window
        );
      },
      STORAGE_DURABILITY_FLUSH_MS
    );

  if (
    typeof timer.unref ===
      "function"
  ) {
    timer.unref();
  }

  window.once(
    "closed",
    () => {
      clearInterval(
        timer
      );
    }
  );
}


async function smokeStorageValue(
  window
) {
  const key =
    "appforge-v23-persistent-smoke";

  const writeValue =
    String(
      process.env
        .APPFORGE_HOST_SMOKE_WRITE_VALUE ||
      ""
    );

  const expression =
    writeValue
      ? (
          `localStorage.setItem(${JSON.stringify(key)}, ` +
          `${JSON.stringify(writeValue)}); ` +
          `localStorage.getItem(${JSON.stringify(key)});`
        )
      : (
          `localStorage.getItem(${JSON.stringify(key)});`
        );

  const value =
    await window
      .webContents
      .executeJavaScript(
        expression,
        true
      );

  window
    .webContents
    .session
    .flushStorageData();

  return value;
}


async function readSmokeState(
  window
) {
  for (
    let attempt = 0;
    attempt < 100;
    attempt += 1
  ) {
    const state =
      await window
        .webContents
        .executeJavaScript(
          "window.__APPFORGE_SMOKE_STATE__ || null",
          true
        )
        .catch(
          () =>
            null
        );

    if (
      state &&
      (
        state.ready ===
          true ||
        state.error
      )
    ) {
      return state;
    }

    await new Promise(
      resolve =>
        setTimeout(
          resolve,
          100
        )
    );
  }

  return null;
}


async function writeSmokeResult(
  window
) {
  const smokeFile =
    String(
      process.env
        .APPFORGE_HOST_SMOKE_FILE ||
      ""
    ).trim();

  if (
    !smokeFile
  ) {
    return;
  }

  const storageValue =
    await smokeStorageValue(
      window
    );

  const smokeState =
    await readSmokeState(
      window
    );

  flushStorageData(
    window
  );

  const result = {
    payloadLoaded:
      true,

    appId:
      payload
        .manifest
        .appId,

    sourceMode:
      payload
        .manifest
        .sourceMode,

    portableExecutableFile:
      portableFile,

    loadedUrl:
      window
        .webContents
        .getURL(),

    userDataPath:
      app.getPath(
        "userData"
      ),

    persistentUserDataPath:
      app.getPath(
        "userData"
      ),

    persistentSessionDataPath:
      persistentSessionData,

    runtimeRoot,

    storageValue,

    smokeState
  };

  fs.writeFileSync(
    smokeFile,
    JSON.stringify(
      result,
      null,
      2
    ),
    "utf8"
  );

  if (
    process.env
      .APPFORGE_HOST_SMOKE_EXIT ===
      "1"
  ) {
    setTimeout(
      () => {
        app.quit();
      },
      300
    );
  }
}


function writeSmokeFailure(
  error
) {
  const smokeFile =
    String(
      process.env
        .APPFORGE_HOST_SMOKE_FILE ||
      ""
    ).trim();

  if (
    smokeFile
  ) {
    try {
      fs.writeFileSync(
        smokeFile,
        JSON.stringify(
          {
            payloadLoaded:
              false,

            error:
              String(
                error?.message ||
                error
              )
          },
          null,
          2
        ),
        "utf8"
      );
    } catch {}
  }

  process.exitCode =
    1;

  app.quit();
}


function createWindow() {
  const manifest =
    payload.manifest;

  const window =
    new BrowserWindow(
      {
        width:
          1280,

        height:
          800,

        minWidth:
          640,

        minHeight:
          480,

        title:
          manifest.appName,

        backgroundColor:
          "#07101F",

        autoHideMenuBar:
          true,

        webPreferences: {
          nodeIntegration:
            false,

          contextIsolation:
            true,

          sandbox:
            true,

          webSecurity:
            true,

          backgroundThrottling:
            false
        }
      }
    );

  startStorageDurabilityFlush(
    window
  );

  window
    .webContents
    .setWindowOpenHandler(
      (
        {
          url
        }
      ) => {

        if (
          /^https?:\/\//i
            .test(
              url
            )
        ) {
          shell.openExternal(
            url
          );
        }

        return {
          action:
            "deny"
        };
      }
    );

  window.webContents.on(
    "will-navigate",
    (
      event,
      url
    ) => {
      const current =
        window
          .webContents
          .getURL();

      if (
        url ===
          current
      ) {
        return;
      }

      if (
        manifest.sourceMode ===
          "LOCAL" &&
        !isLocalAppUrl(
          url
        )
      ) {
        event.preventDefault();

        if (
          /^https?:\/\//i
            .test(
              url
            )
        ) {
          shell.openExternal(
            url
          );
        }
      }

      if (
        manifest.sourceMode ===
          "URL" &&
        !/^https:\/\//i
          .test(
            url
          )
      ) {
        event.preventDefault();
      }
    }
  );

  window.webContents.once(
    "did-finish-load",
    () => {
      void writeSmokeResult(
        window
      ).catch(
        writeSmokeFailure
      );
    }
  );

  if (
    manifest.sourceMode ===
      "URL"
  ) {
    window.loadURL(
      manifest.webUrl
    );
  } else {
    window.loadURL(
      localAppUrl(
        manifest.startPage
      )
    );
  }
}


app.on(
  "second-instance",
  () => {
    const window =
      BrowserWindow
        .getAllWindows()
        [0];

    if (
      !window
    ) {
      return;
    }

    if (
      window.isMinimized()
    ) {
      window.restore();
    }

    window.show();
    window.focus();
  }
);


app
  .whenReady()
  .then(
    async () => {
      if (
        !ownsSingleInstance &&
        !startupError
      ) {
        return;
      }

      if (
        startupError
      ) {
        dialog.showErrorBox(
          "AppForge Portable EXE",
          String(
            startupError.message ||
            startupError
          )
        );

        app.quit();
        return;
      }

      await registerLocalProtocol();

      createWindow();

      app.on(
        "activate",
        () => {
          if (
            BrowserWindow
              .getAllWindows()
              .length ===
              0
          ) {
            createWindow();
          }
        }
      );
    }
  );


app.on(
  "window-all-closed",
  () => {
    app.quit();
  }
);


app.on(
  "will-quit",
  () => {
    for (
      const window of
      BrowserWindow.getAllWindows()
    ) {
      flushStorageData(
        window
      );
    }

    cleanRuntime();
  }
);
