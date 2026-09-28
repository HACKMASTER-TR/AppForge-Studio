"use strict";

const crypto =
  require(
    "node:crypto"
  );

const path =
  require(
    "node:path"
  );


function normalizedAppId(
  appId
) {
  const value =
    String(
      appId ||
      ""
    )
      .trim()
      .toLowerCase();

  if (
    !/^[a-z_]\w*(\.[a-z_]\w*)+$/
      .test(
        value
      )
  ) {
    throw new Error(
      "Windows kalıcı veri app ID değeri geçersiz."
    );
  }

  return value;
}


function persistentUserDataPath(
  appId,
  localAppData =
    process.env
      .LOCALAPPDATA
) {
  const base =
    String(
      localAppData ||
      ""
    ).trim();

  if (
    !base
  ) {
    throw new Error(
      "Windows LOCALAPPDATA yolu bulunamadı."
    );
  }

  const root =
    path.resolve(
      base,
      "AppForgeStudio",
      "PortableApps"
    );

  const target =
    path.resolve(
      root,
      normalizedAppId(
        appId
      ),
      "user-data"
    );

  if (
    target ===
      root ||
    !target.startsWith(
      root +
        path.sep
    )
  ) {
    throw new Error(
      "Windows kalıcı veri yolu güvenli değil."
    );
  }

  return target;
}


function runtimeDirectoryName(
  appId
) {
  return (
    "app-" +
    crypto
      .createHash(
        "sha256"
      )
      .update(
        normalizedAppId(
          appId
        ),
        "utf8"
      )
      .digest(
        "hex"
      )
      .slice(
        0,
        24
      )
  );
}


module.exports = {
  normalizedAppId,
  persistentUserDataPath,
  runtimeDirectoryName
};
