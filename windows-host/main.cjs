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
  materializeAppForgePayload
} =
  require(
    "./payload.cjs"
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

try {
  payload =
    materializeAppForgePayload(
      portableFile,
      runtimeRoot
    );

  app.setPath(
    "userData",
    path.join(
      runtimeRoot,
      "user-data"
    )
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


function writeSmokeResult(
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
        .getURL()
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
      writeSmokeResult(
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
    window.loadFile(
      path.join(
        payload.siteRoot,
        manifest.startPage
      )
    );
  }
}


app
  .whenReady()
  .then(
    () => {
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
