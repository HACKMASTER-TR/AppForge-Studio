"use strict";

const {
  app,
  BrowserWindow,
  dialog,
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
  materializeAppForgePayload,
  readPayloadMetadata
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

    runtimeRoot,

    storageValue
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
        !url.startsWith(
          "file:"
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
    window.loadFile(
      path.join(
        payload.siteRoot,
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
    () => {
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
    cleanRuntime();
  }
);
