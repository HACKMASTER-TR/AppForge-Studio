import AdmZip from "adm-zip";
import { promises as fs } from "fs";
import path from "path";
import { spawn } from "child_process";
import {
  androidAppCategoryAttribute
} from "./androidAppCategory.js";
import {
  resolveFastDebugKeystorePath
} from "./fastSigningKey.js";

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

const FAST_QR_DEX =
  "/opt/appforge-fast-features/qr/classes.dex";

const FAST_DEBUG_KEYSTORE =
  resolveFastDebugKeystorePath();

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


function resolveAndroidSdk(c) {
  const minSdk =
    Number(c?.minSdk ?? 26);

  const targetSdk =
    Number(c?.targetSdk ?? 37);

  if (
    !Number.isInteger(minSdk) ||
    minSdk < 26 ||
    minSdk > 37
  ) {
    throw new Error(
      "FAST BUILD: Min SDK 26 ile 37 arasında olmalı."
    );
  }

  if (
    !Number.isInteger(targetSdk) ||
    targetSdk < 26 ||
    targetSdk > 37
  ) {
    throw new Error(
      "FAST BUILD: Hedef SDK 26 ile 37 arasında olmalı."
    );
  }

  if (minSdk > targetSdk) {
    throw new Error(
      "FAST BUILD: Min SDK, Hedef SDK değerinden büyük olamaz."
    );
  }

  return {
    minSdk,
    targetSdk
  };
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
    c.firebase?.analytics || c.firebase?.crashlytics || c.firebase?.messaging
  ) {
    reasons.push(
      "Firebase"
    );
  }

  /*
   * FAST runtime artık onShowFileChooser, medya yakalama ve
   * kamera sonucu akışını içerir. Bu yaygın varsayılanlar için
   * FULL Gradle derlemesine düşmek gereksiz yere dakikalar ekler.
   */

  if (
    c.nativeBridge?.enabled === true &&
    c.nativeBridge?.mediaPlayer === true
  ) {
    reasons.push(
      "Media3 arka plan medya"
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

  const qrEnabled =
    c.nativeBridge?.enabled === true &&
    c.nativeBridge?.qrScanner === true;

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

  const requiredFiles = [
    AAPT2,
    ZIPALIGN,
    APKSIGNER,
    ANDROID_JAR,
    FAST_DEX,
    FAST_DEBUG_KEYSTORE
  ];

  if (qrEnabled) {
    requiredFiles.push(
      FAST_QR_DEX
    );
  }

  for (
    const required of
    requiredFiles
  ) {
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

  const {
    minSdk,
    targetSdk
  } =
    resolveAndroidSdk(c);

  const deepLinkEnabled =
    c.deepLink?.enabled === true;

  const deepLinkScheme =
    String(
      c.deepLink?.scheme || ""
    ).trim();

  const deepLinkHost =
    String(
      c.deepLink?.host || ""
    ).trim();

  let deepLinkPathPrefix =
    String(
      c.deepLink?.pathPrefix || "/"
    ).trim();

  if (deepLinkEnabled) {
    if (
      !/^[A-Za-z][A-Za-z0-9+.-]*$/.test(
        deepLinkScheme
      )
    ) {
      throw new Error(
        "FAST BUILD: geçersiz Deep Link scheme."
      );
    }

    if (
      !/^[A-Za-z0-9.-]+$/.test(
        deepLinkHost
      )
    ) {
      throw new Error(
        "FAST BUILD: geçersiz Deep Link host."
      );
    }

    if (
      !deepLinkPathPrefix.startsWith("/")
    ) {
      deepLinkPathPrefix =
        "/" + deepLinkPathPrefix;
    }
  }

  const deepLinkIntentFilter =
    deepLinkEnabled
      ? `
            <intent-filter>
                <action
                    android:name="android.intent.action.VIEW" />

                <category
                    android:name="android.intent.category.DEFAULT" />

                <category
                    android:name="android.intent.category.BROWSABLE" />

                <data
                    android:scheme="${xml(deepLinkScheme)}"
                    android:host="${xml(deepLinkHost)}"
                    android:pathPrefix="${xml(deepLinkPathPrefix)}" />
            </intent-filter>
`
      : "";

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

  const appCategoryAttribute =
    androidAppCategoryAttribute(
      c
    );

  const notificationPermission =
    c.features?.notifications === true
      ? `
    <uses-permission
        android:name="android.permission.POST_NOTIFICATIONS" />
`
      : "";

  const vibrationPermission =
    c.nativeBridge?.enabled === true &&
    c.nativeBridge?.vibration === true
      ? `
    <uses-permission
        android:name="android.permission.VIBRATE" />
`
      : "";

  const cameraPermission =
    c.features?.camera === true
      ? `
    <uses-permission
        android:name="android.permission.CAMERA" />

    <uses-feature
        android:name="android.hardware.camera"
        android:required="false" />
`
      : "";

  const locationPermission =
    c.features?.location === true
      ? `
    <uses-permission
        android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <uses-permission
        android:name="android.permission.ACCESS_FINE_LOCATION" />
`
      : "";

  const microphonePermission =
    (c.features?.microphone === true ||
      c.features?.fileUpload === true)
      ? `
    <uses-permission
        android:name="android.permission.RECORD_AUDIO" />
`
      : "";

  const networkStatePermission =
    c.features?.networkState === true
      ? `
    <uses-permission
        android:name="android.permission.ACCESS_NETWORK_STATE" />
`
      : "";

  const wakeLockPermission =
    c.features?.wakeLock === true
      ? `
    <uses-permission
        android:name="android.permission.WAKE_LOCK" />
`
      : "";

  const nfcPermission =
    c.features?.nfc === true
      ? `
    <uses-permission
        android:name="android.permission.NFC" />

    <uses-feature
        android:name="android.hardware.nfc"
        android:required="false" />
`
      : "";

  const additionalPermissionMap = {
    BLUETOOTH: ["BLUETOOTH_SCAN", "BLUETOOTH_CONNECT"],
    BIOMETRIC: ["USE_BIOMETRIC"],
    CALENDAR: ["READ_CALENDAR", "WRITE_CALENDAR"],
    CONTACTS: ["READ_CONTACTS", "WRITE_CONTACTS"],
    BACKGROUND_LOCATION: ["ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION"],
    EXACT_ALARM: ["SCHEDULE_EXACT_ALARM"],
    MEDIA_IMAGES: ["READ_MEDIA_IMAGES"],
    MEDIA_VIDEO: ["READ_MEDIA_VIDEO"],
    ACTIVITY_RECOGNITION: ["ACTIVITY_RECOGNITION"]
  };

  const additionalPermissions =
    (Array.isArray(c.features?.additionalPermissions)
      ? c.features.additionalPermissions
      : [])
      .flatMap(key => additionalPermissionMap[key] || [])
      .map(name => `
    <uses-permission
        android:name="android.permission.${name}" />
`)
      .join("");

  const qrNetworkPermission =
    qrEnabled
      ? `
    <uses-permission
        android:name="android.permission.ACCESS_NETWORK_STATE" />
`
      : "";

  const qrManifestComponents =
    qrEnabled
      ? `
        <!-- Google Code Scanner -->

        <activity
            android:name="com.google.mlkit.vision.codescanner.internal.GmsBarcodeScanningDelegateActivity"
            android:exported="false"
            android:screenOrientation="portrait" />

        <!-- ML Kit initialization -->

        <provider
            android:name="com.google.mlkit.common.internal.MlKitInitProvider"
            android:authorities="${xml(pkg)}.mlkitinitprovider"
            android:exported="false"
            android:initOrder="99" />

        <service
            android:name="com.google.mlkit.common.internal.MlKitComponentDiscoveryService"
            android:directBootAware="true"
            android:exported="false">

            <meta-data
                android:name="com.google.firebase.components:com.google.mlkit.common.internal.CommonComponentRegistrar"
                android:value="com.google.firebase.components.ComponentRegistrar" />

            <meta-data
                android:name="com.google.firebase.components:com.google.mlkit.vision.common.internal.VisionCommonRegistrar"
                android:value="com.google.firebase.components.ComponentRegistrar" />

        </service>

        <!-- Google Play Services -->

        <activity
            android:name="com.google.android.gms.common.api.GoogleApiActivity"
            android:exported="false"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" />

        <meta-data
            android:name="com.google.android.gms.version"
            android:value="12451000" />

        <!-- Data Transport -->

        <service
            android:name="com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService"
            android:exported="false"
            android:permission="android.permission.BIND_JOB_SERVICE" />

        <receiver
            android:name="com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver"
            android:exported="false" />

        <service
            android:name="com.google.android.datatransport.runtime.backends.TransportBackendDiscovery"
            android:exported="false">

            <meta-data
                android:name="backend:com.google.android.datatransport.cct.CctBackendFactory"
                android:value="cct" />

        </service>
`
      : "";

  const manifestXml =
`<?xml version="1.0" encoding="utf-8"?>
<manifest
    xmlns:android="http://schemas.android.com/apk/res/android"
    package="${xml(pkg)}"
    android:versionCode="${versionCode}"
    android:versionName="${xml(versionName)}">

    <uses-sdk
        android:minSdkVersion="${minSdk}"
        android:targetSdkVersion="${targetSdk}" />

    <uses-permission
        android:name="android.permission.INTERNET" />
${notificationPermission}${vibrationPermission}${cameraPermission}${microphonePermission}${locationPermission}${networkStatePermission}${wakeLockPermission}${nfcPermission}${additionalPermissions}${qrNetworkPermission}
    <application
        android:allowBackup="true"
        android:hardwareAccelerated="true"
        android:label="${xml(c.appName || "AppForge App")}"${appCategoryAttribute}${iconAttributes}
        android:theme="@android:style/Theme.Material.NoActionBar"
        android:usesCleartextTraffic="false">

        <activity
            android:name="com.appforge.runtime.FastActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:screenOrientation="${orientation}">

            <intent-filter>
                <action
                    android:name="android.intent.action.MAIN" />

                <category
                    android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
${deepLinkIntentFilter}
        </activity>
${qrManifestComponents}
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

  if (qrEnabled) {
    zip.addFile(
      "classes2.dex",
      await fs.readFile(
        FAST_QR_DEX
      )
    );
  }

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

    downloads:
      c.features?.downloads === true,

    notifications:
      c.features?.notifications === true,

    camera:
      c.features?.camera === true,

    location:
      c.features?.location === true,

    nativeBridge:
      c.nativeBridge?.enabled === true,

    nativeBridgeAllowRemote:
      c.nativeBridge?.allowRemote === true,

    shareBridge:
      c.nativeBridge?.share === true,

    clipboardBridge:
      c.nativeBridge?.clipboard === true,

    vibrationBridge:
      c.nativeBridge?.vibration === true,

    qrScanner:
      qrEnabled,

    versionName,

    deepLinkEnabled,

    deepLinkScheme,

    deepLinkHost,

    deepLinkPathPrefix,

    fullscreen:
      c.features?.fullscreen === true,

    offlineCache:
      c.features?.offlineCache !== false,

    webJavaScriptEnabled:
      c.webView?.javaScriptEnabled !== false,

    webDomStorageEnabled:
      c.webView?.domStorageEnabled !== false,

    webZoomEnabled:
      c.webView?.zoomEnabled !== false,

    webWideViewPortEnabled:
      c.webView?.wideViewPortEnabled !== false,

    webOverviewModeEnabled:
      c.webView?.overviewModeEnabled !== false,

    webMediaAutoplayEnabled:
      c.webView?.mediaAutoplayEnabled !== false,

    webMixedContentAllowed:
      c.webView?.mixedContentAllowed === true,

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

  const appForgeProjectManifest = {
    format:
      "appforge-project",

    formatVersion:
      1,

    producer:
      "AppForge Studio",

    platform:
      "android",

    appName:
      String(
        c.appName ||
        "AppForge App"
      ),

    appId:
      String(
        c.packageName ||
        ""
      ),

    versionName:
      String(
        c.versionName ||
        "1.0.0"
      ),

    versionCode:
      Math.max(
        1,
        Number(
          c.versionCode ||
          1
        )
      ),

    sourceMode:
      c.sourceMode ===
        "URL"
        ? "URL"
        : "LOCAL",

    webUrl:
      c.sourceMode ===
        "URL"
        ? String(
            c.webUrl ||
            ""
          )
        : null,

    projectRoot:
      c.sourceMode ===
        "LOCAL"
        ? "assets/site"
        : null,

    webView: {
      javaScriptEnabled:
        c.webView?.javaScriptEnabled !== false,

      domStorageEnabled:
        c.webView?.domStorageEnabled !== false,

      zoomEnabled:
        c.webView?.zoomEnabled !== false,

      wideViewPortEnabled:
        c.webView?.wideViewPortEnabled !== false,

      overviewModeEnabled:
        c.webView?.overviewModeEnabled !== false,

      mediaAutoplayEnabled:
        c.webView?.mediaAutoplayEnabled !== false,

      mixedContentAllowed:
        c.webView?.mixedContentAllowed === true
    },

    nativeBridge: {
      mediaPlayer:
        Boolean(
          c.nativeBridge?.enabled &&
          c.nativeBridge?.mediaPlayer
        )
    },

    conversion: {
      apkToExe:
        true,

      exeToApk:
        true
    }
  };

  zip.addFile(
    "assets/appforge-project.json",
    Buffer.from(
      JSON.stringify(
        appForgeProjectManifest,
        null,
        2
      ),
      "utf8"
    )
  );

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
