import AdmZip from "adm-zip";
import { promises as fs } from "fs";
import path from "path";
import { spawn } from "child_process";

const ANDROID_HOME =
  process.env.ANDROID_HOME ||
  process.env.ANDROID_SDK_ROOT ||
  "/opt/android-sdk";

const BUILD_TOOLS = "36.0.0";

const AAPT2 =
  path.join(
    ANDROID_HOME,
    "build-tools",
    BUILD_TOOLS,
    "aapt2"
  );

const ZIPALIGN =
  path.join(
    ANDROID_HOME,
    "build-tools",
    BUILD_TOOLS,
    "zipalign"
  );

const APKSIGNER =
  path.join(
    ANDROID_HOME,
    "build-tools",
    BUILD_TOOLS,
    "apksigner"
  );

const ANDROID_JAR =
  path.join(
    ANDROID_HOME,
    "platforms",
    "android-37.0",
    "android.jar"
  );

const FAST_RUNTIME_ROOT =
  "/opt/appforge-fast-runtime";

const FAST_DEX =
  path.join(
    FAST_RUNTIME_ROOT,
    "classes.dex"
  );

const FAST_DEBUG_KEYSTORE =
  process.env.APPFORGE_FAST_DEBUG_KEYSTORE ||
  "/data/gradle-cache/appforge-signing/debug.keystore";

function xml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}

function safePackageName(value) {
  const pkg =
    String(value || "");

  if (
    !/^[A-Za-z_]\w*(\.[A-Za-z_]\w*)+$/.test(
      pkg
    )
  ) {
    throw new Error(
      "FAST BUILD: geçersiz package name."
    );
  }

  return pkg;
}

function safeOrientation(value) {
  return [
    "portrait",
    "landscape",
    "unspecified"
  ].includes(value)
    ? value
    : "unspecified";
}

function command(
  executable,
  args,
  {
    cwd,
    env = process.env
  } = {}
) {
  return new Promise(
    (resolve, reject) => {
      const child =
        spawn(
          executable,
          args,
          {
            cwd,
            env,
            stdio: [
              "ignore",
              "pipe",
              "pipe"
            ]
          }
        );

      let stdout = "";
      let stderr = "";

      child.stdout.on(
        "data",
        chunk => {
          stdout +=
            chunk.toString();
        }
      );

      child.stderr.on(
        "data",
        chunk => {
          stderr +=
            chunk.toString();
        }
      );

      child.on(
        "error",
        reject
      );

      child.on(
        "close",
        code => {
          if (code === 0) {
            resolve({
              stdout,
              stderr
            });

            return;
          }

          reject(
            new Error(
              [
                `Komut başarısız: ${path.basename(executable)}`,
                `exit=${code}`,
                stderr.trim(),
                stdout.trim()
              ]
                .filter(Boolean)
                .join("\n")
            )
          );
        }
      );
    }
  );
}

export function getFastBuildDecision(
  c,
  {
    outputType
  } = {}
) {
  const reasons = [];

  if (
    outputType !== "apk"
  ) {
    reasons.push(
      "AAB/BOTH istendi"
    );
  }

  if (
    c.features?.downloads
  ) {
    reasons.push(
      "native indirme yöneticisi"
    );
  }

  if (
    c.features?.camera
  ) {
    reasons.push(
      "kamera"
    );
  }

  if (
    c.features?.location
  ) {
    reasons.push(
      "konum"
    );
  }

  if (
    c.features?.notifications
  ) {
    reasons.push(
      "bildirim"
    );
  }

  if (
    c.deepLink?.enabled
  ) {
    reasons.push(
      "deep link"
    );
  }

  if (
    c.nativeBridge?.enabled
  ) {
    reasons.push(
      "Native Bridge"
    );
  }

  if (
    c.nativeBridge?.qrScanner
  ) {
    reasons.push(
      "QR tarayıcı"
    );
  }

  if (
    c.admob?.enabled
  ) {
    reasons.push(
      "AdMob"
    );
  }

  if (
    c.billing?.enabled
  ) {
    reasons.push(
      "Google Play Billing"
    );
  }

  if (
    c.firebase?.analytics ||
    c.firebase?.crashlytics
  ) {
    reasons.push(
      "Firebase"
    );
  }

  return {
    eligible:
      reasons.length === 0,

    reasons
  };
}

export async function buildFastApk({
  workDir,
  siteDir,
  config: c,
  localKeystore = null,
  iconFile = null
}) {
  const pkg =
    safePackageName(
      c.packageName
    );

  await fs.rm(
    workDir,
    {
      recursive: true,
      force: true
    }
  );

  await fs.mkdir(
    workDir,
    {
      recursive: true
    }
  );

  for (const required of [
    AAPT2,
    ZIPALIGN,
    APKSIGNER,
    ANDROID_JAR,
    FAST_DEX,
    FAST_DEBUG_KEYSTORE
  ]) {
    await fs.access(
      required
    );
  }

  if (
    c.sourceMode === "LOCAL"
  ) {
    if (!siteDir) {
      throw new Error(
        "FAST BUILD: yerel site klasörü bulunamadı."
      );
    }

    await fs.access(
      path.join(
        siteDir,
        "index.html"
      )
    );
  }

  const manifest =
    path.join(
      workDir,
      "AndroidManifest.xml"
    );

  const baseApk =
    path.join(
      workDir,
      "base.apk"
    );

  const payloadApk =
    path.join(
      workDir,
      "payload.apk"
    );

  const alignedApk =
    path.join(
      workDir,
      "aligned.apk"
    );

  const finalApk =
    path.join(
      workDir,
      "app-release.apk"
    );

  const versionCode =
    Math.max(
      1,
      Number(
        c.versionCode || 1
      ) | 0
    );

  const versionName =
    String(
      c.versionName ||
      "1.0.0"
    );

  const orientation =
    safeOrientation(
      c.orientation
    );

  const hasCustomIcon =
    Boolean(
      iconFile
    );

  const iconAttributes =
    hasCustomIcon
      ? `
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"`
      : "";

  const manifestXml =
`<?xml version="1.0" encoding="utf-8"?>
<manifest
    xmlns:android="http://schemas.android.com/apk/res/android"
    package="${xml(pkg)}"
    android:versionCode="${versionCode}"
    android:versionName="${xml(versionName)}">

    <uses-sdk
        android:minSdkVersion="26"
        android:targetSdkVersion="37" />

    <uses-permission
        android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:hardwareAccelerated="true"
        android:label="${xml(c.appName || "AppForge App")}"${iconAttributes}
        android:theme="@android:style/Theme.Material.NoActionBar"
        android:usesCleartextTraffic="false">

        <activity
            android:name="com.appforge.runtime.FastActivity"
            android:exported="true"
            android:screenOrientation="${orientation}">

            <intent-filter>
                <action
                    android:name="android.intent.action.MAIN" />

                <category
                    android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

        </activity>

    </application>

</manifest>
`;

  await fs.writeFile(
    manifest,
    manifestXml,
    "utf8"
  );

  const compiledResources =
    path.join(
      workDir,
      "compiled-res.zip"
    );

  if (
    hasCustomIcon
  ) {
    const resourceDir =
      path.join(
        workDir,
        "res"
      );

    const mipmapDir =
      path.join(
        resourceDir,
        "mipmap"
      );

    await fs.mkdir(
      mipmapDir,
      {
        recursive: true
      }
    );

    await fs.copyFile(
      iconFile,
      path.join(
        mipmapDir,
        "ic_launcher.png"
      )
    );

    await command(
      AAPT2,
      [
        "compile",
        "--dir",
        resourceDir,
        "-o",
        compiledResources
      ],
      {
        cwd:
          workDir
      }
    );
  }

  await command(
    AAPT2,
    [
      "link",
      "-o",
      baseApk,
      "-I",
      ANDROID_JAR,
      "--manifest",
      manifest,
      ...(
        hasCustomIcon
          ? [
              "-R",
              compiledResources
            ]
          : []
      ),
      "--min-sdk-version",
      "26",
      "--target-sdk-version",
      "37",
      "--version-code",
      String(versionCode),
      "--version-name",
      versionName
    ],
    {
      cwd: workDir
    }
  );

  const zip =
    new AdmZip(
      baseApk
    );

  zip.addFile(
    "classes.dex",
    await fs.readFile(
      FAST_DEX
    )
  );

  const fastConfig = {
    sourceMode:
      c.sourceMode,

    webUrl:
      c.webUrl || "",

    appName:
      c.appName || "AppForge App",

    splashEnabled:
      c.splashEnabled === true,

    splashText:
      c.splashText ||
      c.appName ||
      "AppForge App",

    hasCustomIcon,

    fileUpload:
      c.features?.fileUpload !== false,

    fullscreen:
      c.features?.fullscreen === true,

    offlineCache:
      c.features?.offlineCache !== false,

    watermark:
      c.branding?.showWatermark === true,

    primaryColor:
      c.primaryColor || "#6B7CFF",

    backgroundColor:
      c.backgroundColor || "#07101F",

    statusBarColor:
      c.statusBarColor || "#07101F",

    navigationBarColor:
      c.navigationBarColor || "#07101F"
  };

  zip.addFile(
    "assets/appforge-fast.json",
    Buffer.from(
      JSON.stringify(
        fastConfig
      ),
      "utf8"
    )
  );

  if (
    c.sourceMode === "LOCAL"
  ) {
    zip.addLocalFolder(
      siteDir,
      "assets/site"
    );
  }

  zip.writeZip(
    payloadApk
  );

  await command(
    ZIPALIGN,
    [
      "-p",
      "-f",
      "4",
      payloadApk,
      alignedApk
    ]
  );

  let signingArgs;
  let signingEnv =
    {
      ...process.env
    };

  if (
    c.signing?.mode ===
    "CUSTOM"
  ) {
    if (!localKeystore) {
      throw new Error(
        "FAST BUILD: custom keystore bulunamadı."
      );
    }

    signingEnv = {
      ...signingEnv,

      APPFORGE_FAST_STORE_PASSWORD:
        c.signing?.storePassword || "",

      APPFORGE_FAST_KEY_PASSWORD:
        c.signing?.keyPassword || ""
    };

    signingArgs = [
      "sign",
      "--ks",
      localKeystore,
      "--ks-key-alias",
      String(
        c.signing?.alias || ""
      ),
      "--ks-pass",
      "env:APPFORGE_FAST_STORE_PASSWORD",
      "--key-pass",
      "env:APPFORGE_FAST_KEY_PASSWORD",
      "--out",
      finalApk,
      alignedApk
    ];
  } else {
    signingArgs = [
      "sign",
      "--ks",
      FAST_DEBUG_KEYSTORE,
      "--ks-key-alias",
      "androiddebugkey",
      "--ks-pass",
      "pass:android",
      "--key-pass",
      "pass:android",
      "--out",
      finalApk,
      alignedApk
    ];
  }

  await command(
    APKSIGNER,
    signingArgs,
    {
      env:
        signingEnv
    }
  );

  await command(
    APKSIGNER,
    [
      "verify",
      "--verbose",
      finalApk
    ]
  );

  const stat =
    await fs.stat(
      finalApk
    );

  if (
    stat.size <= 0
  ) {
    throw new Error(
      "FAST BUILD boş APK oluşturdu."
    );
  }

  return finalApk;
}
