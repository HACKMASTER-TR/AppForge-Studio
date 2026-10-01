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
  safeRelativePath
} =
  require(
    "./payload.cjs"
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


function persistentUserDataRoot(
  appId
) {
  const localAppData =
    String(
      process.env
        .LOCALAPPDATA ||
      ""
    ).trim();

  const home =
    String(
      os.homedir() ||
      ""
    ).trim();

  if (
    !localAppData &&
    !home
  ) {
    throw new Error(
      "Windows kullanıcı veri dizini bulunamadı."
    );
  }

  const base =
    localAppData
      ? path.resolve(
          localAppData
        )
      : path.join(
          home,
          "AppData",
          "Local"
        );

  return path.join(
    base,
    "AppForgePortable",
    String(
      appId
    )
  );
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

const runtimeId =
  crypto
    .createHash(
      "sha256"
    )
    .update(
      portableFile,
      "utf8"
    )
    .digest(
      "hex"
    )
    .slice(
      0,
      16
    );

const runtimeRoot =
  path.join(
    os.tmpdir(),
    "AppForgePortableHost",
    `${runtimeId}-${process.pid}`
  );

let payload =
  null;

let startupError =
  null;

let persistentUserData =
  null;

let persistentSessionData =
  null;

try {
  payload =
    materializeAppForgePayload(
      portableFile,
      runtimeRoot
    );

  persistentUserData =
    persistentUserDataRoot(
      payload
        .manifest
        .appId
    );

  persistentSessionData =
    path.join(
      persistentUserData,
      "session-data"
    );

  fs.mkdirSync(
    persistentUserData,
    {
      recursive:
        true
    }
  );

  fs.mkdirSync(
    persistentSessionData,
    {
      recursive:
        true
    }
  );

  app.setPath(
    "userData",
    persistentUserData
  );

  app.setPath(
    "sessionData",
    persistentSessionData
  );

  app.setAppUserModelId(
    payload.manifest.appId
  );

} catch (
  error
) {
  startupError =
    error;
}


function cleanRuntime() {
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

  const smokeState =
    await readSmokeState(
      window
    );

  /*
   * Force Chromium DOM storage to disk before the smoke checkpoint.
   * This makes forced-termination acceptance test the real durability
   * boundary instead of only in-memory localStorage state.
   */
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

    persistentUserDataPath:
      persistentUserData,

    persistentSessionDataPath:
      persistentSessionData,

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
      250
    );
  }
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

  /*
   * Recent localStorage mutations may otherwise remain buffered in
   * Chromium memory until a graceful shutdown. Keep a bounded periodic
   * durability flush so unexpected process termination loses neither the
   * latest persisted application state nor the stable appforge origin.
   */
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


app
  .whenReady()
  .then(
    async () => {
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
  )
  .catch(
    error => {
      dialog.showErrorBox(
        "AppForge Portable EXE",
        String(
          error?.message ||
          error
        )
      );

      app.quit();
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
