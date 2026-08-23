import AdmZip from "adm-zip";
import { promises as fs } from "fs";
import path from "path";
import { spawn } from "child_process";
import { query } from "./db.js";
import { config } from "./config.js";
import {
  materializeInput,
  deleteInput,
  putOutput
} from "./storage.js";
import { storeCache } from "./buildCache.js";
import { appendBuildLog } from "./buildLogs.js";

function esc(s) {
  return String(s ?? "").replaceAll("\\", "\\\\").replaceAll('"', '\\"');
}

function xml(s) {
  return String(s ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}

function kotlinList(values) {
  const items = (values || []).map(v => `"${esc(v)}"`).join(", ");
  return `listOf(${items})`;
}

function safePackageName(value) {
  if (!/^[A-Za-z_]\w*(\.[A-Za-z_]\w*)+$/.test(String(value || ""))) {
    throw new Error("Geçersiz package name.");
  }
  return value;
}

function safeHex(value, fallback) {
  const v = String(value || "");
  return /^#[0-9A-Fa-f]{6}$/.test(v) ? v : fallback;
}

async function appendLog(
  buildId,
  line
) {
  const text =
    String(line ?? "");

  console.log(
    `[BUILD-LOG ${buildId}] ${text}`
  );

  return appendBuildLog(
    buildId,
    text
  );
}

async function updateBuild(buildId, fields) {
  const allowed = {
    status: "status",
    progress: "progress",
    outputs: "outputs",
    error: "error",
    startedAt: "started_at",
    completedAt: "completed_at",
    workerId: "worker_id",
    durationMs: "duration_ms",
    artifactManifest: "artifact_manifest"
  };

  const sets = [];
  const values = [buildId];
  let index = 2;

  for (const [key, value] of Object.entries(fields)) {
    const column = allowed[key];
    if (!column) continue;

    if (
      key === "outputs" ||
      key === "artifactManifest"
    ) {
      sets.push(
        `${column} = $${index}::jsonb`
      );
      values.push(
        JSON.stringify(
          value || {}
        )
      );
    } else {
      sets.push(`${column} = $${index}`);
      values.push(value);
    }
    index += 1;
  }

  if (!sets.length) return;

  await query(
    `UPDATE appforge_builds SET ${sets.join(", ")} WHERE id = $1`,
    values
  );
}

export function preflight(c, files = {}) {
  const out = [];
  const ok = x => out.push(`✅ ${x}`);
  const warn = x => out.push(`⚠️ ${x}`);

  safePackageName(c.packageName);
  ok("Package name geçerli.");

  if (Number(c.versionCode || 0) > 0) ok("versionCode pozitif.");
  if (String(c.versionName || "").trim()) ok("versionName dolu.");

  if (c.sourceMode === "URL") {
    if (!/^https:\/\//i.test(c.webUrl || "")) {
      throw new Error("URL modu HTTPS gerektirir.");
    }
    ok("HTTPS URL.");
  } else {
    if (!files.hasProject) throw new Error("Yerel proje ZIP'i eksik.");
    ok("Yerel proje kaynağı mevcut.");
  }

  if (c.signing?.mode === "CUSTOM") {
    if (!files.hasKeystore) throw new Error("Custom signing için keystore eksik.");
    ok("Custom signing.");
  } else {
    warn("Debug signing seçili.");
  }

  if ((c.firebase?.analytics || c.firebase?.crashlytics) && !files.hasFirebaseConfig) {
    throw new Error("Firebase açık ancak google-services.json eksik.");
  }

  if (c.firebase?.analytics || c.firebase?.crashlytics) {
    ok("Firebase config mevcut.");
  }

  if (c.billing?.enabled) ok("Google Play Billing etkin.");
  if (c.admob?.enabled) ok("AdMob etkin.");
  if (c.admob?.umpConsent) ok("UMP izin akışı etkin.");
  if (c.nativeBridge?.qrScanner) ok("QR/Barkod tarayıcı etkin.");

  if (
    c.nativeBridge?.enabled &&
    c.sourceMode === "URL" &&
    c.nativeBridge?.allowRemote !== true
  ) {
    warn(
      "URL modunda Native Bridge varsayılan olarak kapalı."
    );
  }

  ok("Secure WebMessage Native Bridge v2.");

  if (
    c.branding?.showWatermark ===
    true
  ) {
    ok(
      "Free plan: native 'Built with AppForge' watermark etkin."
    );
  } else if (
    c.branding?.serverEnforced ===
    true
  ) {
    ok(
      "Pro plan: AppForge watermark kaldırıldı."
    );
  }

  ok("Target SDK 37.");
  return out;
}


function cancelledError() {
  const error =
    new Error(
      "Build kullanıcı tarafından iptal edildi."
    );

  error.code =
    "BUILD_CANCELLED";

  return error;
}

async function throwIfCancelled(
  buildId
) {
  const result =
    await query(
      `SELECT cancel_requested
       FROM appforge_builds
       WHERE id = $1`,
      [buildId]
    );

  if (
    result.rows[0]
      ?.cancel_requested
  ) {
    throw cancelledError();
  }
}


async function validateUploadedPng(file) {
  if (!file) return;

  const stat =
    await fs.stat(file);

  if (
    stat.size >
      5 * 1024 * 1024
  ) {
    throw new Error(
      "Uygulama ikonu en fazla 5 MB olabilir."
    );
  }

  const handle =
    await fs.open(
      file,
      "r"
    );

  try {
    const signature =
      Buffer.alloc(8);

    const result =
      await handle.read(
        signature,
        0,
        8,
        0
      );

    if (
      result.bytesRead !== 8 ||
      !signature.equals(
        Buffer.from([
          0x89,
          0x50,
          0x4e,
          0x47,
          0x0d,
          0x0a,
          0x1a,
          0x0a
        ])
      )
    ) {
      throw new Error(
        "İkon gerçek bir PNG dosyası değil."
      );
    }
  } finally {
    await handle.close();
  }
}

async function validateFirebaseConfig(
  file,
  packageName
) {
  if (!file) return;

  const stat =
    await fs.stat(file);

  if (
    stat.size >
      2 * 1024 * 1024
  ) {
    throw new Error(
      "google-services.json beklenenden büyük."
    );
  }

  let json;

  try {
    json =
      JSON.parse(
        await fs.readFile(
          file,
          "utf8"
        )
      );
  } catch {
    throw new Error(
      "google-services.json geçerli JSON değil."
    );
  }

  const clients =
    Array.isArray(json?.client)
      ? json.client
      : [];

  const matches =
    clients.some(
      client =>
        client
          ?.client_info
          ?.android_client_info
          ?.package_name ===
        packageName
    );

  if (!matches) {
    throw new Error(
      "Firebase package name, oluşturulan uygulamanın package name değeriyle eşleşmiyor."
    );
  }
}

export async function executeBuild(job) {
  const {
    buildId,
    userId,
    teamId = null,
    workerId = null,
    cacheKey = null,
    config: c,
    projectRef,
    keystoreRef,
    iconRef,
    firebaseConfigRef
  } = job;

  const startedAtMs =
    Date.now();

  let buildSucceeded =
    false;

  const work =
    path.join(
      config.workRoot,
      buildId
    );

  const android =
    path.join(
      work,
      "android"
    );
  const outputsDir = path.join(config.outputRoot, buildId);

  try {
    await fs.rm(work, { recursive: true, force: true });
    await fs.mkdir(android, { recursive: true });
    await fs.mkdir(outputsDir, { recursive: true });

    const inputDir = path.join(work, "_inputs");
    await fs.mkdir(inputDir, { recursive: true });

    const uploadedProject = projectRef
      ? await materializeInput(
          projectRef,
          path.join(inputDir, "project.zip")
        )
      : null;

    const uploadedKeystore = keystoreRef
      ? await materializeInput(
          keystoreRef,
          path.join(inputDir, "release.jks")
        )
      : null;

    const uploadedIcon = iconRef
      ? await materializeInput(
          iconRef,
          path.join(inputDir, "icon.png")
        )
      : null;

    const uploadedFirebaseConfig = firebaseConfigRef
      ? await materializeInput(
          firebaseConfigRef,
          path.join(inputDir, "google-services.json")
        )
      : null;

    await validateUploadedPng(
      uploadedIcon
    );

    if (
      c.firebase?.analytics ||
      c.firebase?.crashlytics
    ) {
      await validateFirebaseConfig(
        uploadedFirebaseConfig,
        c.packageName
      );
    }

    await throwIfCancelled(buildId);

    await updateBuild(
      buildId,
      {
        status: "building",
        progress: 5,
        startedAt:
          new Date(),
        workerId
      }
    );

    await appendLog(buildId, "Android proje şablonu oluşturuluyor...");

    const localKeystore =
      c.signing?.mode === "CUSTOM"
        ? path.join(work, "release.jks")
        : null;

    if (localKeystore) {
      await fs.copyFile(uploadedKeystore, localKeystore);
    }

    await throwIfCancelled(buildId);

    await generateAndroidProject(android, c, {
      localKeystore,
      iconFile: uploadedIcon,
      firebaseConfig: uploadedFirebaseConfig
    });

    if (c.sourceMode === "LOCAL") {
      await extractZipSafely(
        uploadedProject,
        path.join(android, "app/src/main/assets/site")
      );
    }

    await updateBuild(buildId, { progress: 25 });
    await appendLog(buildId, "Gradle derlemesi başlatılıyor...");

    const outputType = ["apk", "aab", "both"].includes(c.buildOutput)
      ? c.buildOutput
      : "both";

    const tasks = [];
    if (outputType === "apk" || outputType === "both") tasks.push("assembleRelease");
    if (outputType === "aab" || outputType === "both") tasks.push("bundleRelease");

    await throwIfCancelled(buildId);

    await runGradle(buildId, android, tasks, {
      ...process.env,
      APPFORGE_STORE_PASSWORD: c.signing?.storePassword || "",
      APPFORGE_KEY_PASSWORD: c.signing?.keyPassword || "",
      APPFORGE_KEY_ALIAS: c.signing?.alias || "",
      GRADLE_USER_HOME: config.gradleCacheRoot
    });

    await throwIfCancelled(buildId);

    await updateBuild(buildId, { progress: 90 });

    const out = {};

    if (outputType === "apk" || outputType === "both") {
      const apk = await findFirst(
        android,
        p => p.endsWith(".apk") && p.includes("/release/")
      );

      if (apk) {
        const outputRef = await putOutput(
          buildId,
          "app-release.apk",
          apk
        );
        out.apk = outputRef;
      }
    }

    if (outputType === "aab" || outputType === "both") {
      const aab = await findFirst(android, p => p.endsWith(".aab"));
      if (aab) {
        const outputRef = await putOutput(
          buildId,
          "app-release.aab",
          aab
        );
        out.aab = outputRef;
      }
    }

    const durationMs =
      Date.now() -
      startedAtMs;

    const artifactManifest = {
      workerId,
      durationMs,
      createdAt:
        new Date()
          .toISOString(),
      packageName:
        c.packageName,
      versionName:
        c.versionName,
      versionCode:
        c.versionCode,
      outputs: out
    };

    await updateBuild(
      buildId,
      {
        status: "success",
        progress: 100,
        outputs: out,
        completedAt:
          new Date(),
        workerId,
        durationMs,
        artifactManifest
      }
    );

    if (cacheKey) {
      await storeCache({
        cacheKey,
        sourceBuildId: buildId,
        outputs: out,
        metadata: {
          packageName: c.packageName,
          versionName: c.versionName,
          versionCode: c.versionCode,
          buildOutput: c.buildOutput
        }
      });
    }

    await appendLog(buildId, "Build başarıyla tamamlandı.");

    buildSucceeded =
      true;
  } catch (error) {
    await appendLog(buildId, `HATA: ${String(error?.message || error)}`);
    throw error;
  } finally {
    if (buildSucceeded) {
      for (const ref of [
        projectRef,
        keystoreRef,
        iconRef,
        firebaseConfigRef
      ]) {
        if (!ref) continue;

        try {
          await deleteInput(ref);
        } catch {}
      }
    }

    try {
      await fs.rm(work, { recursive: true, force: true });
    } catch {}
  }
}

async function generateAndroidProject(projectDir, c, files) {
  const pkg = safePackageName(c.packageName);
  const pkgPath = pkg.replaceAll(".", "/");
  const appDir = path.join(projectDir, "app");
  const javaDir = path.join(appDir, "src/main/java", pkgPath);
  const valuesDir = path.join(appDir, "src/main/res/values");
  const values31Dir = path.join(appDir, "src/main/res/values-v31");
  const drawableDir = path.join(appDir, "src/main/res/drawable");
  const mipmapDir = path.join(appDir, "src/main/res/mipmap");
  const mipmapV26 = path.join(appDir, "src/main/res/mipmap-anydpi-v26");
  const xmlDir = path.join(appDir, "src/main/res/xml");
  const assetsDir = path.join(appDir, "src/main/assets/site");

  for (const dir of [
    javaDir, valuesDir, values31Dir, drawableDir,
    mipmapDir, mipmapV26, xmlDir, assetsDir
  ]) {
    await fs.mkdir(dir, { recursive: true });
  }

  const firebaseEnabled =
    Boolean(c.firebase?.analytics || c.firebase?.crashlytics);

  await fs.writeFile(
    path.join(projectDir, "settings.gradle.kts"),
`pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "GeneratedApp"
include(":app")
`
  );

  await fs.writeFile(
    path.join(projectDir, "build.gradle.kts"),
`plugins {
    id("com.android.application") version "9.1.1" apply false
    ${firebaseEnabled ? 'id("com.google.gms.google-services") version "4.5.0" apply false' : ""}
    ${c.firebase?.crashlytics ? 'id("com.google.firebase.crashlytics") version "3.0.7" apply false' : ""}
}
`
  );

  await fs.writeFile(
    path.join(projectDir, "gradle.properties"),
`org.gradle.jvmargs=-Xmx320m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC -Dfile.encoding=UTF-8
org.gradle.workers.max=1
org.gradle.parallel=false
org.gradle.vfs.watch=false
org.gradle.daemon=false
android.useAndroidX=true
kotlin.code.style=official
kotlin.compiler.execution.strategy=in-process
`
  );

  const signingConfig =
    c.signing?.mode === "CUSTOM"
      ? `
    signingConfigs {
        create("release") {
            storeFile = file("${esc(files.localKeystore)}")
            storePassword = System.getenv("APPFORGE_STORE_PASSWORD")
            keyAlias = System.getenv("APPFORGE_KEY_ALIAS")
            keyPassword = System.getenv("APPFORGE_KEY_PASSWORD")
        }
    }`
      : "";

  const signingLine =
    c.signing?.mode === "CUSTOM"
      ? `signingConfig = signingConfigs.getByName("release")`
      : `signingConfig = signingConfigs.getByName("debug")`;

  const dependencies = [
    'implementation("androidx.core:core-ktx:1.19.0")',
    'implementation("androidx.appcompat:appcompat:1.7.1")',
    'implementation("androidx.core:core-splashscreen:1.2.0")',
    'implementation("androidx.webkit:webkit:1.16.0")',
    c.admob?.enabled
      ? 'implementation("com.google.android.gms:play-services-ads:25.4.0")'
      : "",
    c.admob?.enabled && c.admob?.umpConsent
      ? 'implementation("com.google.android.ump:user-messaging-platform:4.0.0")'
      : "",
    c.billing?.enabled
      ? 'implementation("com.android.billingclient:billing-ktx:9.1.0")'
      : "",
    c.nativeBridge?.qrScanner
      ? 'implementation("com.google.android.gms:play-services-code-scanner:16.1.0")'
      : "",
    c.firebase?.analytics || c.firebase?.crashlytics
      ? 'implementation(platform("com.google.firebase:firebase-bom:34.17.0"))'
      : "",
    c.firebase?.analytics
      ? 'implementation("com.google.firebase:firebase-analytics")'
      : "",
    c.firebase?.crashlytics
      ? 'implementation("com.google.firebase:firebase-crashlytics")'
      : ""
  ].filter(Boolean).join("\n    ");

  await fs.writeFile(
    path.join(appDir, "build.gradle.kts"),
`plugins {
    id("com.android.application")
    ${firebaseEnabled ? 'id("com.google.gms.google-services")' : ""}
    ${c.firebase?.crashlytics ? 'id("com.google.firebase.crashlytics")' : ""}
}

android {
    namespace = "${pkg}"
    compileSdk = 37

    defaultConfig {
        applicationId = "${pkg}"
        minSdk = 26
        targetSdk = 37
        versionCode = ${Number(c.versionCode || 1)}
        versionName = "${esc(c.versionName || "1.0.0")}"
    }
${signingConfig}

    buildTypes {
        release {
            isMinifyEnabled = false
            ${signingLine}
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    ${dependencies}
}
`
  );

  const permissions = [
    '<uses-permission android:name="android.permission.INTERNET" />',
    c.features?.camera
      ? '<uses-permission android:name="android.permission.CAMERA" />'
      : "",
    c.features?.location
      ? '<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />'
      : "",
    c.features?.notifications
      ? '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />'
      : "",
    c.nativeBridge?.vibration
      ? '<uses-permission android:name="android.permission.VIBRATE" />'
      : ""
  ].filter(Boolean).join("\n    ");

  const deepLink = c.deepLink?.enabled
    ? `
            <intent-filter android:autoVerify="${c.deepLink.scheme === "https" ? "true" : "false"}">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:scheme="${xml(c.deepLink.scheme)}"
                    android:host="${xml(c.deepLink.host)}"
                    android:pathPrefix="${xml(c.deepLink.pathPrefix || "/")}" />
            </intent-filter>`
    : "";

  const provider = c.features?.camera
    ? `
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="\${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>`
    : "";

  const admobMeta = c.admob?.enabled
    ? `
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="${xml(c.admob.appId)}" />`
    : "";

  const scannerMeta = c.nativeBridge?.qrScanner
    ? `
        <meta-data
            android:name="com.google.mlkit.vision.DEPENDENCIES"
            android:value="barcode_ui" />`
    : "";

  const orientation = ["portrait", "landscape", "unspecified"].includes(c.orientation)
    ? c.orientation
    : "unspecified";

  await fs.writeFile(
    path.join(appDir, "src/main/AndroidManifest.xml"),
`<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    ${permissions}

    <application
        android:allowBackup="true"
        android:label="${xml(c.appName)}"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:theme="${c.splashEnabled ? "@style/AppSplashTheme" : "@style/AppTheme"}"
        android:usesCleartextTraffic="false">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="${orientation}">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>${deepLink}
        </activity>
        ${provider}
        ${admobMeta}
        ${scannerMeta}
    </application>
</manifest>
`
  );

  const primary = safeHex(c.primaryColor, "#6B7CFF");
  const background = safeHex(c.backgroundColor, "#07101F");
  const statusBar = safeHex(c.statusBarColor, background);
  const navBar = safeHex(c.navigationBarColor, background);

  await fs.writeFile(
    path.join(valuesDir, "colors.xml"),
`<resources>
    <color name="app_primary">${primary}</color>
    <color name="app_background">${background}</color>
    <color name="app_status_bar">${statusBar}</color>
    <color name="app_navigation_bar">${navBar}</color>
    <color name="icon_background">${primary}</color>
</resources>
`
  );

  await fs.writeFile(
    path.join(valuesDir, "styles.xml"),
`<resources>
    <style name="AppTheme" parent="Theme.AppCompat.DayNight.NoActionBar">
        <item name="colorPrimary">@color/app_primary</item>
        <item name="android:windowBackground">@color/app_background</item>
        <item name="android:statusBarColor">@color/app_status_bar</item>
        <item name="android:navigationBarColor">@color/app_navigation_bar</item>
    </style>

    <style name="AppSplashTheme" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/app_background</item>
        <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item>
        <item name="postSplashScreenTheme">@style/AppTheme</item>
    </style>
</resources>
`
  );

  await fs.writeFile(
    path.join(values31Dir, "styles.xml"),
`<resources>
    <style name="AppSplashTheme" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/app_background</item>
        <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item>
        <item name="postSplashScreenTheme">@style/AppTheme</item>
    </style>
</resources>
`
  );

  if (files.iconFile) {
    await fs.copyFile(files.iconFile, path.join(mipmapDir, "ic_launcher.png"));
    await fs.copyFile(files.iconFile, path.join(drawableDir, "ic_launcher_foreground.png"));
  } else {
    await fs.writeFile(
      path.join(drawableDir, "ic_launcher_foreground.xml"),
`<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF" android:pathData="M28,28h52v52h-52z"/>
    <path android:fillColor="${primary}" android:pathData="M38,38h32v32h-32z"/>
</vector>
`
    );

    await fs.writeFile(
      path.join(mipmapDir, "ic_launcher.xml"),
`<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="${primary}" android:pathData="M0,0h108v108h-108z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M28,28h52v52h-52z"/>
</vector>
`
    );
  }

  await fs.writeFile(
    path.join(mipmapV26, "ic_launcher.xml"),
`<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/icon_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
`
  );

  if (c.features?.camera) {
    await fs.writeFile(
      path.join(xmlDir, "file_paths.xml"),
`<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="camera" path="camera/"/>
</paths>
`
    );
  }

  if (firebaseEnabled) {
    if (!files.firebaseConfig) {
      throw new Error("Firebase açık fakat google-services.json eksik.");
    }
    await fs.copyFile(files.firebaseConfig, path.join(appDir, "google-services.json"));
  }

  await fs.writeFile(
    path.join(javaDir, "MainActivity.kt"),
    generatedMainActivity(c, pkg)
  );
}

function generatedMainActivity(c, pkg) {
  const isLocalSource =
    c.sourceMode === "LOCAL";

  const bridgeEnabled =
    Boolean(
      c.nativeBridge?.enabled &&
      (
        isLocalSource ||
        c.nativeBridge?.allowRemote === true
      )
    );

  const bridgeOriginRule =
    isLocalSource
      ? "https://appassets.androidplatform.net"
      : (() => {
          try {
            const u =
              new URL(
                String(
                  c.webUrl || ""
                )
              );

            if (
              u.protocol !==
              "https:"
            ) {
              return "";
            }

            const defaultPort =
              u.port === "" ||
              u.port === "443";

            return (
              `https://${u.hostname}` +
              (
                defaultPort
                  ? ""
                  : `:${u.port}`
              )
            );
          } catch {
            return "";
          }
        })();


  const loadTarget =
    c.sourceMode === "URL"
      ? (
          c.deepLink?.enabled
            ? `
        val incomingDeepLink =
            intent?.data

        val deepLinkMatches =
            incomingDeepLink != null &&
            incomingDeepLink.scheme ==
                "${esc(String(c.deepLink?.scheme || ""))}" &&
            incomingDeepLink.host ==
                "${esc(String(c.deepLink?.host || ""))}"

        if (deepLinkMatches) {
            web.loadUrl(
                incomingDeepLink.toString()
            )
        } else {
            web.loadUrl(
                "${esc(String(c.webUrl))}"
            )
        }
`
            : `web.loadUrl("${esc(String(c.webUrl))}")`
        )
      : `web.loadUrl("https://appassets.androidplatform.net/assets/site/index.html")`;

  const deepLinkHandling = "";

  const inappIds = String(c.billing?.productIds || "")
    .split(",")
    .map(x => x.trim())
    .filter(Boolean);

  const subIds = String(c.billing?.subscriptionIds || "")
    .split(",")
    .map(x => x.trim())
    .filter(Boolean);

  const consumableIds = String(c.billing?.consumableProductIds || "")
    .split(",")
    .map(x => x.trim())
    .filter(Boolean);

  const imports = [
    "android.annotation.SuppressLint",
    "android.app.Activity",
    c.features?.downloads ? "android.app.DownloadManager" : "",
    "android.content.Context",
    "android.content.Intent",
    "android.content.pm.PackageManager",
    "android.net.Uri",
    "android.os.Bundle",
    c.nativeBridge?.vibration ? "android.os.VibrationEffect" : "",
    c.nativeBridge?.vibration ? "android.os.Vibrator" : "",
    c.features?.downloads ? "android.os.Environment" : "",
    "android.webkit.CookieManager",
    "android.webkit.GeolocationPermissions",
    "android.webkit.ValueCallback",
    "android.webkit.WebChromeClient",
    "android.webkit.WebView",
    "android.webkit.WebViewClient",
    "android.webkit.WebResourceRequest",
    "android.webkit.WebResourceResponse",
    "androidx.webkit.WebViewAssetLoader",
    bridgeEnabled ? "androidx.webkit.WebViewCompat" : "",
    bridgeEnabled ? "androidx.webkit.WebViewFeature" : "",
    bridgeEnabled ? "androidx.webkit.WebMessageCompat" : "",
    bridgeEnabled ? "androidx.webkit.JavaScriptReplyProxy" : "",
    "android.widget.FrameLayout",
    c.branding?.showWatermark ? "android.widget.TextView" : "",
    c.branding?.showWatermark ? "android.view.Gravity" : "",
    c.branding?.showWatermark ? "android.graphics.Color" : "",
    c.branding?.showWatermark ? "android.graphics.drawable.GradientDrawable" : "",
    "android.widget.Toast",
    "androidx.activity.result.contract.ActivityResultContracts",
    "androidx.appcompat.app.AppCompatActivity",
    "androidx.core.content.ContextCompat",
    c.features?.camera ? "android.provider.MediaStore" : "",
    c.features?.camera ? "androidx.core.content.FileProvider" : "",
    c.features?.camera ? "java.io.File" : "",
    c.features?.camera ? "java.text.SimpleDateFormat" : "",
    c.features?.camera ? "java.util.Date" : "",
    c.features?.camera ? "java.util.Locale" : "",
    c.splashEnabled ? "androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen" : "",
    c.admob?.enabled ? "com.google.android.gms.ads.AdRequest" : "",
    c.admob?.enabled ? "com.google.android.gms.ads.AdSize" : "",
    c.admob?.enabled ? "com.google.android.gms.ads.AdView" : "",
    c.admob?.enabled ? "com.google.android.gms.ads.MobileAds" : "",
    c.admob?.enabled && c.admob?.interstitialUnitId ? "com.google.android.gms.ads.interstitial.InterstitialAd" : "",
    c.admob?.enabled && c.admob?.interstitialUnitId ? "com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback" : "",
    c.admob?.enabled && c.admob?.rewardedUnitId ? "com.google.android.gms.ads.rewarded.RewardedAd" : "",
    c.admob?.enabled && c.admob?.rewardedUnitId ? "com.google.android.gms.ads.rewarded.RewardedAdLoadCallback" : "",
    c.admob?.enabled ? "com.google.android.gms.ads.LoadAdError" : "",
    c.admob?.enabled && c.admob?.umpConsent ? "com.google.android.ump.ConsentInformation" : "",
    c.admob?.enabled && c.admob?.umpConsent ? "com.google.android.ump.ConsentRequestParameters" : "",
    c.admob?.enabled && c.admob?.umpConsent ? "com.google.android.ump.UserMessagingPlatform" : "",
    c.billing?.enabled ? "com.android.billingclient.api.AcknowledgePurchaseParams" : "",
    c.billing?.enabled ? "com.android.billingclient.api.BillingClient" : "",
    c.billing?.enabled ? "com.android.billingclient.api.BillingClientStateListener" : "",
    c.billing?.enabled ? "com.android.billingclient.api.BillingFlowParams" : "",
    c.billing?.enabled ? "com.android.billingclient.api.BillingResult" : "",
    c.billing?.enabled ? "com.android.billingclient.api.ConsumeParams" : "",
    c.billing?.enabled ? "com.android.billingclient.api.PendingPurchasesParams" : "",
    c.billing?.enabled ? "com.android.billingclient.api.ProductDetails" : "",
    c.billing?.enabled ? "com.android.billingclient.api.Purchase" : "",
    c.billing?.enabled ? "com.android.billingclient.api.QueryProductDetailsParams" : "",
    c.billing?.enabled ? "com.android.billingclient.api.QueryPurchasesParams" : "",
    c.nativeBridge?.clipboard ? "android.content.ClipboardManager" : "",
    c.nativeBridge?.clipboard ? "android.content.ClipData" : "",
    c.nativeBridge?.qrScanner ? "com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions" : "",
    c.nativeBridge?.qrScanner ? "com.google.mlkit.vision.codescanner.GmsBarcodeScanning" : "",
    c.firebase?.analytics ? "com.google.firebase.analytics.FirebaseAnalytics" : "",
    "java.net.HttpURLConnection",
    "java.net.URL",
    "org.json.JSONObject"
  ].filter(Boolean).map(x => `import ${x}`).join("\n");

  const fields = `
    private lateinit var web: WebView
    ${isLocalSource ? `private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(this)
            )
            .build()
    }` : ""}
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    ${c.features?.camera ? "private var cameraUri: Uri? = null" : ""}
    ${c.billing?.enabled ? "private var billingClient: BillingClient? = null" : ""}
    ${c.billing?.enabled ? "private val productDetailsCache = mutableMapOf<String, ProductDetails>()" : ""}
    ${c.admob?.enabled && c.admob?.interstitialUnitId ? "private var interstitialAd: InterstitialAd? = null" : ""}
    ${c.admob?.enabled && c.admob?.rewardedUnitId ? "private var rewardedAd: RewardedAd? = null" : ""}
    ${c.admob?.enabled ? "private var bannerAdView: AdView? = null" : ""}
`;

  const fileChooserLauncher = c.features?.fileUpload ? `
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback ?: return@registerForActivityResult
            val data = result.data
            val uris = when {
                result.resultCode != Activity.RESULT_OK -> null
                data?.clipData != null -> Array(data.clipData!!.itemCount) { i ->
                    data.clipData!!.getItemAt(i).uri
                }
                data?.data != null -> arrayOf(data.data!!)
                ${c.features?.camera ? "cameraUri != null -> arrayOf(cameraUri!!)" : "false -> null"}
                else -> null
            }
            callback.onReceiveValue(uris)
            fileChooserCallback = null
            ${c.features?.camera ? "cameraUri = null" : ""}
        }
` : "";

  const geoLauncher = c.features?.location ? `
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val origin = pendingGeoOrigin
            val callback = pendingGeoCallback
            if (origin != null && callback != null) {
                callback.invoke(origin, granted, false)
            }
            pendingGeoOrigin = null
            pendingGeoCallback = null
        }
` : "";

  const notificationLauncher = c.features?.notifications ? `
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
` : "";

  const settings = `
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode =
                ${c.features?.offlineCache
                  ? "android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK"
                  : "android.webkit.WebSettings.LOAD_DEFAULT"}
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
        }
`;

  const webChrome = `
        web.webChromeClient =
            object : WebChromeClient() {
                ${c.features?.fileUpload ? `
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileChooserCallback
                        ?.onReceiveValue(null)

                    fileChooserCallback =
                        filePathCallback

                    val picker =
                        try {
                            fileChooserParams
                                ?.createIntent()
                                ?: Intent(
                                    Intent.ACTION_GET_CONTENT
                                ).apply {
                                    type = "*/*"
                                    addCategory(
                                        Intent.CATEGORY_OPENABLE
                                    )
                                }
                        } catch (_: Throwable) {
                            Intent(
                                Intent.ACTION_GET_CONTENT
                            ).apply {
                                type = "*/*"
                                addCategory(
                                    Intent.CATEGORY_OPENABLE
                                )
                            }
                        }

                    ${c.features?.camera ? `
                    val cameraIntent =
                        Intent(
                            MediaStore.ACTION_IMAGE_CAPTURE
                        )

                    val cameraDir =
                        File(
                            cacheDir,
                            "camera"
                        ).apply {
                            mkdirs()
                        }

                    val cameraFile =
                        File.createTempFile(
                            "appforge_",
                            ".jpg",
                            cameraDir
                        )

                    cameraUri =
                        FileProvider.getUriForFile(
                            this@MainActivity,
                            packageName +
                                ".fileprovider",
                            cameraFile
                        )

                    cameraIntent.putExtra(
                        MediaStore.EXTRA_OUTPUT,
                        cameraUri
                    )

                    cameraIntent.addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                    val chooser =
                        Intent.createChooser(
                            picker,
                            "Dosya seç"
                        ).apply {
                            putExtra(
                                Intent.EXTRA_INITIAL_INTENTS,
                                arrayOf(
                                    cameraIntent
                                )
                            )
                        }

                    fileChooserLauncher.launch(
                        chooser
                    )
                    ` : `
                    fileChooserLauncher.launch(
                        picker
                    )
                    `}

                    return true
                }
                ` : ""}

                ${c.features?.location ? `
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    if (
                        origin == null ||
                        callback == null
                    ) {
                        return
                    }

                    val granted =
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        ) ==
                        PackageManager.PERMISSION_GRANTED

                    if (granted) {
                        callback.invoke(
                            origin,
                            true,
                            false
                        )
                    } else {
                        pendingGeoOrigin =
                            origin

                        pendingGeoCallback =
                            callback

                        locationPermissionLauncher.launch(
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    }
                }
                ` : ""}
            }
`;

  const downloads = c.features?.downloads ? `
        web.setDownloadListener {
            url,
            userAgent,
            contentDisposition,
            mimeType,
            _ ->

            val uri =
                runCatching {
                    Uri.parse(url)
                }.getOrNull()

            val scheme =
                uri?.scheme
                    ?.lowercase()
                    .orEmpty()

            if (
                uri == null ||
                (
                    scheme != "http" &&
                    scheme != "https"
                )
            ) {
                Toast.makeText(
                    this,
                    "Bu indirme türü desteklenmiyor.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setDownloadListener
            }

            try {
                val request =
                    DownloadManager.Request(
                        uri
                    )

                if (
                    !userAgent.isNullOrBlank()
                ) {
                    request.addRequestHeader(
                        "User-Agent",
                        userAgent
                    )
                }

                val cookie =
                    CookieManager
                        .getInstance()
                        .getCookie(url)

                if (
                    !cookie.isNullOrBlank()
                ) {
                    request.addRequestHeader(
                        "Cookie",
                        cookie
                    )
                }

                if (
                    !mimeType.isNullOrBlank()
                ) {
                    request.setMimeType(
                        mimeType
                    )
                }

                val filename =
                    android.webkit.URLUtil
                        .guessFileName(
                            url,
                            contentDisposition,
                            mimeType
                        )

                request
                    .setTitle(filename)
                    .setNotificationVisibility(
                        DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        filename
                    )

                val manager =
                    getSystemService(
                        Context.DOWNLOAD_SERVICE
                    ) as DownloadManager

                manager.enqueue(
                    request
                )

                Toast.makeText(
                    this,
                    "İndirme başlatıldı.",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Throwable) {
                Toast.makeText(
                    this,
                    "İndirme başlatılamadı: " +
                        error.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
` : "";

  const notifications = c.features?.notifications ? `
        if (
            android.os.Build.VERSION.SDK_INT >=
            33 &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        }
` : "";


  const bridgeClass =
    bridgeEnabled
      ? `
    private fun bridgeError(
        message: String
    ) {
        dispatchEvent(
            "appforge-bridge-error",
            JSONObject()
                .put(
                    "message",
                    message.take(600)
                )
                .toString()
        )
    }

    private fun bridgeString(
        json: JSONObject,
        key: String,
        maxLength: Int
    ): String =
        json
            .optString(
                key,
                ""
            )
            .take(
                maxLength
            )

    private fun handleBridgeMessage(
        rawMessage: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        if (!isMainFrame) {
            return
        }

        if (
            rawMessage.length >
            65536
        ) {
            bridgeError(
                "Native Bridge mesajı çok büyük."
            )
            return
        }

        val expectedOrigin =
            "${esc(bridgeOriginRule)}"

        if (
            expectedOrigin.isBlank() ||
            sourceOrigin.toString()
                .trimEnd('/') !=
            expectedOrigin
        ) {
            bridgeError(
                "Native Bridge origin doğrulaması başarısız."
            )
            return
        }

        val payload =
            runCatching {
                JSONObject(
                    rawMessage
                )
            }.getOrElse {
                bridgeError(
                    "Native Bridge JSON geçersiz."
                )
                return
            }

        val action =
            payload
                .optString(
                    "action",
                    ""
                )
                .take(80)

        val args =
            payload
                .optJSONObject(
                    "args"
                )
                ?: JSONObject()

        when (action) {
            ${c.nativeBridge?.share ? `
            "share" -> {
                val title =
                    bridgeString(
                        args,
                        "title",
                        400
                    )

                val text =
                    bridgeString(
                        args,
                        "text",
                        40000
                    )

                runOnUiThread {
                    val intent =
                        Intent(
                            Intent.ACTION_SEND
                        ).apply {
                            type =
                                "text/plain"

                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                title
                            )

                            putExtra(
                                Intent.EXTRA_TEXT,
                                text
                            )
                        }

                    startActivity(
                        Intent.createChooser(
                            intent,
                            title.ifBlank {
                                "Paylaş"
                            }
                        )
                    )
                }
            }
            ` : ""}

            ${c.nativeBridge?.clipboard ? `
            "copy" -> {
                val text =
                    bridgeString(
                        args,
                        "text",
                        40000
                    )

                val clipboard =
                    getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager

                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "AppForge",
                        text
                    )
                )
            }
            ` : ""}

            ${c.nativeBridge?.vibration ? `
            "vibrate" -> {
                val duration =
                    args
                        .optLong(
                            "milliseconds",
                            100L
                        )
                        .coerceIn(
                            1L,
                            1000L
                        )

                val vibrator =
                    getSystemService(
                        Context.VIBRATOR_SERVICE
                    ) as Vibrator

                if (
                    android.os.Build.VERSION.SDK_INT >=
                    26
                ) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            duration,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(
                        duration
                    )
                }
            }
            ` : ""}

            ${c.nativeBridge?.qrScanner ? `
            "scanCode" -> {
                runOnUiThread {
                    startCodeScanner()
                }
            }
            ` : ""}

            ${c.admob?.enabled && c.admob?.interstitialUnitId ? `
            "showInterstitial" -> {
                runOnUiThread {
                    if (
                        adsRemoved()
                    ) {
                        return@runOnUiThread
                    }

                    interstitialAd
                        ?.let {
                            interstitialAd =
                                null

                            it.show(
                                this@MainActivity
                            )

                            loadInterstitial()
                        }
                        ?: loadInterstitial()
                }
            }
            ` : ""}

            ${c.admob?.enabled && c.admob?.rewardedUnitId ? `
            "showRewarded" -> {
                runOnUiThread {
                    if (
                        adsRemoved()
                    ) {
                        return@runOnUiThread
                    }

                    rewardedAd
                        ?.let { ad ->
                            rewardedAd =
                                null

                            ad.show(
                                this@MainActivity
                            ) { reward ->
                                val event =
                                    JSONObject()
                                        .put(
                                            "amount",
                                            reward.amount
                                        )
                                        .put(
                                            "type",
                                            reward.type
                                        )
                                        .toString()

                                dispatchEvent(
                                    "appforge-reward-earned",
                                    event
                                )
                            }

                            loadRewarded()
                        }
                        ?: loadRewarded()
                }
            }
            ` : ""}

            ${c.billing?.enabled ? `
            "queryProducts" -> {
                runOnUiThread {
                    queryBillingProducts()
                }
            }

            "purchase" -> {
                val productId =
                    bridgeString(
                        args,
                        "productId",
                        400
                    )

                if (
                    productId.isBlank()
                ) {
                    bridgeError(
                        "productId boş olamaz."
                    )
                    return
                }

                runOnUiThread {
                    launchPurchase(
                        productId,
                        null
                    )
                }
            }

            "purchaseWithOffer" -> {
                val productId =
                    bridgeString(
                        args,
                        "productId",
                        400
                    )

                val offerToken =
                    bridgeString(
                        args,
                        "offerToken",
                        8192
                    )

                if (
                    productId.isBlank() ||
                    offerToken.isBlank()
                ) {
                    bridgeError(
                        "productId ve offerToken gerekli."
                    )
                    return
                }

                runOnUiThread {
                    launchPurchase(
                        productId,
                        offerToken
                    )
                }
            }

            "restorePurchases" -> {
                runOnUiThread {
                    queryOwnedPurchases()
                }
            }
            ` : ""}

            "getRuntimeInfo" -> {
                val reply =
                    JSONObject()
                        .put(
                            "platform",
                            "android"
                        )
                        .put(
                            "appVersion",
                            "${esc(String(c.versionName || "1.0.0"))}"
                        )
                        .put(
                            "adsRemoved",
                            adsRemoved()
                        )
                        .toString()

                replyProxy.postMessage(
                    reply
                )
            }

            else -> {
                bridgeError(
                    "Desteklenmeyen Native Bridge işlemi."
                )
            }
        }
    }
`
      : "";

  const bridgeSetup =
    bridgeEnabled
      ? `
        if (
            WebViewFeature.isFeatureSupported(
                WebViewFeature.WEB_MESSAGE_LISTENER
            )
        ) {
            val allowedBridgeOrigins =
                setOf(
                    "${esc(bridgeOriginRule)}"
                )

            WebViewCompat.addWebMessageListener(
                web,
                "AppForgeNative",
                allowedBridgeOrigins,
                object :
                    WebViewCompat.WebMessageListener {
                    override fun onPostMessage(
                        view: WebView,
                        message: WebMessageCompat,
                        sourceOrigin: Uri,
                        isMainFrame: Boolean,
                        replyProxy: JavaScriptReplyProxy
                    ) {
                        val raw =
                            message.data
                                ?: return

                        handleBridgeMessage(
                            raw,
                            sourceOrigin,
                            isMainFrame,
                            replyProxy
                        )
                    }
                }
            )

            if (
                WebViewFeature.isFeatureSupported(
                    WebViewFeature.DOCUMENT_START_SCRIPT
                )
            ) {
                WebViewCompat.addDocumentStartJavaScript(
                    web,
                    ${JSON.stringify(`
(() => {
  "use strict";

  const MAX_MESSAGE = 65536;

  function safeText(value, max) {
    return String(value ?? "").slice(0, max);
  }

  function send(action, args = {}) {
    const payload = JSON.stringify({ action, args });

    if (payload.length > MAX_MESSAGE) {
      throw new Error("AppForge Native Bridge mesajı çok büyük.");
    }

    if (
      !window.AppForgeNative ||
      typeof window.AppForgeNative.postMessage !== "function"
    ) {
      throw new Error("AppForge Native Bridge kullanılamıyor.");
    }

    window.AppForgeNative.postMessage(payload);
  }

  const bridge = {
    ${c.nativeBridge?.share ? `share(title, text) {
      send("share", {
        title: safeText(title, 400),
        text: safeText(text, 40000)
      });
    },` : ""}

    ${c.nativeBridge?.clipboard ? `copy(text) {
      send("copy", {
        text: safeText(text, 40000)
      });
    },` : ""}

    ${c.nativeBridge?.vibration ? `vibrate(milliseconds) {
      const value = Number(milliseconds);
      send("vibrate", {
        milliseconds:
          Number.isFinite(value)
            ? Math.max(1, Math.min(1000, Math.round(value)))
            : 100
      });
    },` : ""}

    ${c.nativeBridge?.qrScanner ? `scanCode() {
      send("scanCode");
    },` : ""}

    ${c.admob?.enabled && c.admob?.interstitialUnitId ? `showInterstitial() {
      send("showInterstitial");
    },` : ""}

    ${c.admob?.enabled && c.admob?.rewardedUnitId ? `showRewarded() {
      send("showRewarded");
    },` : ""}

    ${c.billing?.enabled ? `queryProducts() {
      send("queryProducts");
    },

    purchase(productId) {
      send("purchase", {
        productId: safeText(productId, 400)
      });
    },

    purchaseWithOffer(productId, offerToken) {
      send("purchaseWithOffer", {
        productId: safeText(productId, 400),
        offerToken: safeText(offerToken, 8192)
      });
    },

    restorePurchases() {
      send("restorePurchases");
    },` : ""}

    platform() {
      return "android";
    },

    appVersion() {
      return ${JSON.stringify(String(c.versionName || "1.0.0"))};
    },

    adsRemoved() {
      return (
        localStorage.getItem(
          "__appforge_ads_removed"
        ) === "1"
      );
    }
  };

  Object.defineProperty(
    window,
    "AppForge",
    {
      value: Object.freeze(bridge),
      configurable: false,
      writable: false
    }
  );

  window.addEventListener(
    "appforge-ads-removed",
    () => {
      try {
        localStorage.setItem(
          "__appforge_ads_removed",
          "1"
        );
      } catch {}
    }
  );
})();
`)},
                    allowedBridgeOrigins
                )
            } else {
                dispatchEvent(
                    "appforge-bridge-error",
                    JSONObject()
                        .put(
                            "message",
                            "Document-start JavaScript desteklenmiyor; Native Bridge shim yüklenmedi."
                        )
                        .toString()
                )
            }
        } else {
            dispatchEvent(
                "appforge-bridge-error",
                JSONObject()
                    .put(
                        "message",
                        "Bu WebView sürümü güvenli WebMessage Native Bridge'i desteklemiyor."
                    )
                    .toString()
            )
        }
`
      : "";


  const qrFunctions = c.nativeBridge?.qrScanner ? `
    private fun startCodeScanner() {
        val scanner = GmsBarcodeScanning.getClient(
            this,
            GmsBarcodeScannerOptions.Builder().enableAutoZoom().build()
        )

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                dispatchEvent(
                    "appforge-scan-result",
                    JSONObject().apply {
                        put(
                            "rawValue",
                            (barcode.rawValue ?: "")
                                .take(8192)
                        )
                        put(
                            "displayValue",
                            (barcode.displayValue ?: "")
                                .take(8192)
                        )
                        put("format", barcode.format)
                        put("valueType", barcode.valueType)
                    }.toString()
                )
            }
            .addOnCanceledListener {
                dispatchEvent("appforge-scan-cancelled", "{}")
            }
            .addOnFailureListener { error ->
                dispatchEvent(
                    "appforge-scan-error",
                    JSONObject().put(
                        "message",
                        error.message ?: "Tarama başarısız."
                    ).toString()
                )
            }
    }
` : "";

  const adFunctions = c.admob?.enabled ? `
    private fun adsRemoved(): Boolean =
        getSharedPreferences("appforge", MODE_PRIVATE)
            .getBoolean("ads_removed", false)

    private fun removeAdsEntitlement() {
        getSharedPreferences("appforge", MODE_PRIVATE)
            .edit()
            .putBoolean("ads_removed", true)
            .apply()

        bannerAdView?.let { view ->
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.destroy()
        }
        bannerAdView = null
        interstitialAd = null
        rewardedAd = null

        dispatchEvent("appforge-ads-removed", "{}")
    }

    ${c.admob?.interstitialUnitId ? `
    private fun loadInterstitial() {
        if (adsRemoved()) return
        InterstitialAd.load(
            this,
            "${esc(c.admob.interstitialUnitId)}",
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    dispatchEvent("appforge-ad-ready", """{"type":"interstitial"}""")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }
    ` : ""}

    ${c.admob?.rewardedUnitId ? `
    private fun loadRewarded() {
        if (adsRemoved()) return
        RewardedAd.load(
            this,
            "${esc(c.admob.rewardedUnitId)}",
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    dispatchEvent("appforge-ad-ready", """{"type":"rewarded"}""")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }
    ` : ""}
` : `
    private fun adsRemoved(): Boolean = false

    private fun removeAdsEntitlement() {
        getSharedPreferences("appforge", MODE_PRIVATE)
            .edit()
            .putBoolean("ads_removed", true)
            .apply()
        dispatchEvent("appforge-ads-removed", "{}")
    }
`;

  const umpSetup = c.admob?.enabled && c.admob?.umpConsent ? `
        val consentInformation =
            UserMessagingPlatform.getConsentInformation(this)

        val consentParams = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            this,
            consentParams,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { _ ->
                    if (consentInformation.canRequestAds()) {
                        initializeAds(root)
                    }
                }

                if (consentInformation.canRequestAds()) {
                    initializeAds(root)
                }
            },
            { error ->
                dispatchEvent(
                    "appforge-consent-error",
                    JSONObject()
                        .put(
                            "message",
                            error.message
                        )
                        .toString()
                )

                if (
                    consentInformation.canRequestAds()
                ) {
                    initializeAds(root)
                }
            }
        )
` : c.admob?.enabled ? `
        initializeAds(root)
` : "";

  const adsSetupFunction = c.admob?.enabled ? `
    private fun initializeAds(root: FrameLayout) {
        if (adsRemoved()) return

        MobileAds.initialize(this) {}

        ${c.admob?.bannerUnitId ? `
        if (bannerAdView == null) {
            bannerAdView = AdView(this).apply {
                adUnitId = "${esc(c.admob.bannerUnitId)}"
                setAdSize(AdSize.BANNER)
                loadAd(AdRequest.Builder().build())
            }
            root.addView(
                bannerAdView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM
                }
            )
        }
        ` : ""}

        ${c.admob?.interstitialUnitId ? "loadInterstitial()" : ""}
        ${c.admob?.rewardedUnitId ? "loadRewarded()" : ""}
    }
` : "";

  const billingFunctions = c.billing?.enabled ? `
    private fun inappProductIds(): List<String> =
        ${kotlinList(inappIds)}

    private fun subscriptionProductIds(): List<String> =
        ${kotlinList(subIds)}

    private fun consumableProductIds(): Set<String> =
        ${kotlinList(consumableIds)}.toSet()

    private fun queryBillingProducts() {
        queryProductType(
            ids = inappProductIds(),
            productType = BillingClient.ProductType.INAPP
        )
        queryProductType(
            ids = subscriptionProductIds(),
            productType = BillingClient.ProductType.SUBS
        )
    }

    private fun queryProductType(ids: List<String>, productType: String) {
        val client = billingClient ?: return
        if (ids.isEmpty()) return

        val products = ids.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(productType)
                .build()
        }

        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build()
        ) { result, detailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                return@queryProductDetailsAsync
            }

            val array = org.json.JSONArray()

            detailsResult.productDetailsList.forEach { details ->
                productDetailsCache[details.productId] = details

                val oneTime = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
                val subOffer = details.subscriptionOfferDetails?.firstOrNull()
                val pricingPhase = subOffer
                    ?.pricingPhases
                    ?.pricingPhaseList
                    ?.firstOrNull()

                val offers = org.json.JSONArray()
                details.subscriptionOfferDetails?.forEach { offer ->
                    offers.put(
                        JSONObject().apply {
                            put("basePlanId", offer.basePlanId)
                            put("offerId", offer.offerId ?: JSONObject.NULL)
                            put("offerToken", offer.offerToken)
                            put(
                                "tags",
                                org.json.JSONArray(offer.offerTags)
                            )
                            put(
                                "pricingPhases",
                                org.json.JSONArray().apply {
                                    offer.pricingPhases.pricingPhaseList.forEach { phase ->
                                        put(
                                            JSONObject().apply {
                                                put("price", phase.formattedPrice)
                                                put("currency", phase.priceCurrencyCode)
                                                put("billingPeriod", phase.billingPeriod)
                                                put("recurrenceMode", phase.recurrenceMode)
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    )
                }

                array.put(
                    JSONObject().apply {
                        put("id", details.productId)
                        put("type", details.productType)
                        put("title", details.title)
                        put("description", details.description)
                        put(
                            "price",
                            oneTime?.formattedPrice
                                ?: pricingPhase?.formattedPrice
                                ?: ""
                        )
                        put(
                            "currency",
                            oneTime?.priceCurrencyCode
                                ?: pricingPhase?.priceCurrencyCode
                                ?: ""
                        )
                        put("subscriptionOffers", offers)
                    }
                )
            }

            dispatchEvent(
                "appforge-products",
                JSONObject()
                    .put("productType", productType)
                    .put("products", array)
                    .toString()
            )
        }
    }

    private fun launchPurchase(productId: String, requestedOfferToken: String?) {
        val client = billingClient ?: return
        val details = productDetailsCache[productId] ?: return

        val builder =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)

        val availableOfferTokens =
            details.subscriptionOfferDetails
                ?.map { it.offerToken }
                ?.toSet()
                .orEmpty()

        val offerToken =
            requestedOfferToken
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.also {
                    if (
                        availableOfferTokens.isNotEmpty() &&
                        !availableOfferTokens.contains(it)
                    ) {
                        dispatchEvent(
                            "appforge-purchase-error",
                            JSONObject()
                                .put(
                                    "message",
                                    "Geçersiz subscription offer token."
                                )
                                .toString()
                        )
                        return
                    }
                }
                ?: details
                    .subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.offerToken

        if (!offerToken.isNullOrBlank()) {
            builder.setOfferToken(
                offerToken
            )
        }

        val result = client.launchBillingFlow(
            this,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(builder.build()))
                .build()
        )

        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            dispatchEvent(
                "appforge-purchase-error",
                JSONObject()
                    .put("code", result.responseCode)
                    .put("message", result.debugMessage)
                    .toString()
            )
        }
    }

    private fun queryOwnedPurchases() {
        val client = billingClient ?: return

        listOf(
            BillingClient.ProductType.INAPP,
            BillingClient.ProductType.SUBS
        ).forEach { type ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(type)
                    .build()
            ) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.forEach(::handlePurchase)
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                verifyAndGrantPurchase(purchase)
            }

            Purchase.PurchaseState.PENDING -> {
                dispatchEvent(
                    "appforge-purchase-pending",
                    JSONObject()
                        .put("products", org.json.JSONArray(purchase.products))
                        .toString()
                )
            }
        }
    }

    private fun verifyAndGrantPurchase(purchase: Purchase) {
        val verificationUrl =
            "${esc(String(c.billing?.verificationUrl || ""))}"

        if (verificationUrl.isBlank()) {
            dispatchEvent(
                "appforge-purchase-verification-error",
                JSONObject()
                    .put(
                        "message",
                        "Secure purchase verification URL yapılandırılmamış."
                    )
                    .toString()
            )
            return
        }

        Thread {
            try {
                val firstProduct = purchase.products.firstOrNull().orEmpty()
                val productType =
                    if (subscriptionProductIds().contains(firstProduct)) "subs"
                    else "inapp"

                val connection =
                    URL(verificationUrl).openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                val body = JSONObject().apply {
                    put("packageName", packageName)
                    put("productId", firstProduct)
                    put("purchaseToken", purchase.purchaseToken)
                    put("productType", productType)
                }.toString()

                connection.outputStream.use {
                    it.write(body.toByteArray())
                }

                val responseText =
                    if (connection.responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader()?.use { it.readText() }
                            .orEmpty()
                    }

                val verificationJson =
                    runCatching {
                        JSONObject(
                            responseText
                        )
                    }.getOrNull()

                val verified =
                    connection.responseCode in 200..299 &&
                    verificationJson
                        ?.optBoolean(
                            "ok",
                            false
                        ) == true &&
                    verificationJson
                        .optBoolean(
                            "entitlement",
                            false
                        )

                val processedByServer =
                    verificationJson
                        ?.optBoolean(
                            "processedByServer",
                            false
                        ) == true

                runOnUiThread {
                    if (verified) {
                        grantEntitlement(
                            purchase,
                            processedByServer
                        )
                    } else {
                        dispatchEvent(
                            "appforge-purchase-verification-failed",
                            JSONObject()
                                .put("response", responseText)
                                .toString()
                        )
                    }
                }
            } catch (error: Exception) {
                dispatchEvent(
                    "appforge-purchase-verification-error",
                    JSONObject()
                        .put("message", error.message ?: "Doğrulama hatası")
                        .toString()
                )
            }
        }.start()
    }

    private fun grantEntitlement(purchase: Purchase, processedByServer: Boolean = false) {
        val client = billingClient ?: return
        val products = purchase.products

        val removeAdsId =
            "${esc(String(c.billing?.removeAdsProductId || ""))}"

        if (
            removeAdsId.isNotBlank() &&
            products.contains(removeAdsId)
        ) {
            removeAdsEntitlement()
        }

        val isConsumable =
            products.any { consumableProductIds().contains(it) }

        if (!processedByServer) {
            if (isConsumable) {
                client.consumeAsync(
                    ConsumeParams.newBuilder()
                        .setPurchaseToken(
                            purchase.purchaseToken
                        )
                        .build()
                ) { result, _ ->
                    dispatchEvent(
                        "appforge-purchase-consumed",
                        JSONObject()
                            .put(
                                "code",
                                result.responseCode
                            )
                            .put(
                                "products",
                                org.json.JSONArray(
                                    products
                                )
                            )
                            .toString()
                    )
                }
            } else if (
                !purchase.isAcknowledged
            ) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(
                            purchase.purchaseToken
                        )
                        .build()
                ) { result ->
                    dispatchEvent(
                        "appforge-purchase-acknowledged",
                        JSONObject()
                            .put(
                                "code",
                                result.responseCode
                            )
                            .put(
                                "products",
                                org.json.JSONArray(
                                    products
                                )
                            )
                            .toString()
                    )
                }
            }
        }

        dispatchEvent(
            "appforge-purchase-success",
            JSONObject()
                .put("products", org.json.JSONArray(products))
                .put("acknowledged", purchase.isAcknowledged)
                .put("processedByServer", processedByServer)
                .toString()
        )
    }
` : "";

  const billingSetup = c.billing?.enabled ? `
        billingClient = BillingClient.newBuilder(this)
            .setListener { billingResult, purchases ->
                if (
                    billingResult.responseCode ==
                    BillingClient.BillingResponseCode.OK &&
                    purchases != null
                ) {
                    purchases.forEach(::handlePurchase)
                }
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (
                    result.responseCode ==
                    BillingClient.BillingResponseCode.OK
                ) {
                    queryBillingProducts()
                    queryOwnedPurchases()
                    dispatchEvent("appforge-billing-ready", "{}")
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
` : "";

  const firebaseSetup = c.firebase?.analytics ? `
        val analytics = FirebaseAnalytics.getInstance(this)
        analytics.logEvent(
            "appforge_app_started",
            Bundle().apply {
                putString("version", "${esc(String(c.versionName || "1.0.0"))}")
            }
        )
` : "";

  const dispatchFunction = `
    private fun dispatchEvent(
        name: String,
        jsonPayload: String
    ) {
        val safeName =
            name.take(120)

        val safePayload =
            if (
                jsonPayload.length <=
                    65536 &&
                runCatching {
                    JSONObject(
                        jsonPayload
                    )
                }.isSuccess
            ) {
                jsonPayload
            } else {
                JSONObject()
                    .put(
                        "message",
                        "Event payload geçersiz veya çok büyük."
                    )
                    .toString()
            }

        runOnUiThread {
            val quotedName =
                JSONObject.quote(
                    safeName
                )

            val script =
                "window.dispatchEvent(new CustomEvent(" +
                quotedName +
                ",{detail:" +
                safePayload +
                "}));"

            web.evaluateJavascript(
                script,
                null
            )
        }
    }
`;

  return `
package ${pkg}

${imports}

class MainActivity : AppCompatActivity() {

${fields}
${fileChooserLauncher}
${geoLauncher}
${notificationLauncher}
${bridgeClass}
${qrFunctions}
${adFunctions}
${adsSetupFunction}
${billingFunctions}
${dispatchFunction}

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        ${c.splashEnabled ? "installSplashScreen()\n        " : ""}super.onCreate(savedInstanceState)

        web = WebView(this)

        val root = FrameLayout(this)
        root.addView(
            web,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        ${c.branding?.showWatermark ? `
        val appForgeDensity =
            resources
                .displayMetrics
                .density

        fun appForgeDp(
            value: Int
        ): Int =
            (
                value *
                appForgeDensity
            ).toInt()

        val appForgeWatermark =
            TextView(this).apply {
                text =
                    "Built with AppForge"

                setTextColor(
                    Color.WHITE
                )

                textSize =
                    10.5f

                alpha =
                    0.88f

                setPadding(
                    appForgeDp(10),
                    appForgeDp(6),
                    appForgeDp(10),
                    appForgeDp(6)
                )

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable
                                .RECTANGLE

                        cornerRadius =
                            appForgeDp(
                                10
                            ).toFloat()

                        setColor(
                            Color.argb(
                                185,
                                8,
                                13,
                                20
                            )
                        )

                        setStroke(
                            appForgeDp(1),
                            Color.argb(
                                80,
                                255,
                                255,
                                255
                            )
                        )
                    }

                isClickable =
                    false

                isFocusable =
                    false

                elevation =
                    appForgeDp(
                        12
                    ).toFloat()

                contentDescription =
                    "Built with AppForge"
            }

        val appForgeWatermarkParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity =
                    Gravity.START or
                    Gravity.BOTTOM

                leftMargin =
                    appForgeDp(
                        12
                    )

                bottomMargin =
                    appForgeDp(
                        ${c.admob?.enabled && c.admob?.bannerUnitId ? 66 : 12}
                    )
            }

        root.addView(
            appForgeWatermark,
            appForgeWatermarkParams
        )
        ` : ""}

        setContentView(root)

        web.webViewClient =
            object : WebViewClient() {
                ${isLocalSource ? `
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(
                        request.url
                    )
                }
                ` : ""}

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest
                ): Boolean {
                    val uri = request.url
                    val scheme =
                        uri.scheme
                            ?.lowercase()
                            .orEmpty()

                    val isHttp =
                        scheme == "http" ||
                        scheme == "https"

                    ${isLocalSource ? `
                    val trusted =
                        scheme == "https" &&
                        uri.host ==
                            "appassets.androidplatform.net"
                    ` : `
                    val trustedHost =
                        Uri.parse(
                            "${esc(String(c.webUrl || ""))}"
                        ).host.orEmpty()

                    val trusted =
                        scheme == "https" &&
                        uri.host == trustedHost
                    `}

                    if (trusted) {
                        return false
                    }

                    if (isHttp) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                uri
                            )
                        )
                    }

                    return true
                }
            }
${webChrome}
${settings}
${bridgeSetup}
${downloads}
${notifications}
${umpSetup}
${billingSetup}
${firebaseSetup}
${deepLinkHandling}
        ${loadTarget}
    }

    override fun onResume() {
        super.onResume()
        ${c.billing?.enabled ? "queryOwnedPurchases()" : ""}
    }

    override fun onDestroy() {
        ${c.billing?.enabled ? "billingClient?.endConnection()" : ""}
        ${c.admob?.enabled ? "bannerAdView?.destroy()" : ""}
        ${bridgeEnabled ? `
        if (
            WebViewFeature.isFeatureSupported(
                WebViewFeature.WEB_MESSAGE_LISTENER
            )
        ) {
            WebViewCompat.removeWebMessageListener(
                web,
                "AppForgeNative"
            )
        }
        ` : ""}
        web.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack()
        else super.onBackPressed()
    }
}
`.trim();
}

async function extractZipSafely(
  zipPath,
  dest
) {
  const MAX_ENTRIES = 5000;
  const MAX_TOTAL =
    250 * 1024 * 1024;
  const MAX_ENTRY =
    50 * 1024 * 1024;
  const MAX_PATH = 240;
  const MAX_DEPTH = 20;

  await fs.mkdir(
    dest,
    { recursive: true }
  );

  const zip =
    new AdmZip(zipPath);

  const entries =
    zip.getEntries();

  if (
    entries.length >
      MAX_ENTRIES
  ) {
    throw new Error(
      "ZIP çok fazla dosya içeriyor."
    );
  }

  const root =
    path.resolve(dest);

  let declaredTotal = 0;
  let actualTotal = 0;

  for (
    const entry of entries
  ) {
    const rawName =
      String(
        entry.entryName ||
        ""
      ).replaceAll(
        "\\",
        "/"
      );

    if (
      rawName.length >
      MAX_PATH
    ) {
      throw new Error(
        "ZIP dosya yolu çok uzun."
      );
    }

    const depth =
      rawName
        .split("/")
        .filter(Boolean)
        .length;

    if (
      depth >
      MAX_DEPTH
    ) {
      throw new Error(
        "ZIP klasör derinliği sınırı aşıldı."
      );
    }

    const target =
      path.resolve(
        root,
        rawName
      );

    if (
      target !== root &&
      !target.startsWith(
        root +
        path.sep
      )
    ) {
      throw new Error(
        "ZIP path traversal engellendi."
      );
    }

    const mode =
      (
        Number(
          entry.header?.attr ||
          0
        ) >>> 16
      ) & 0xffff;

    if (
      (
        mode &
        0o170000
      ) ===
      0o120000
    ) {
      throw new Error(
        "ZIP symbolic link içeremez."
      );
    }

    const declared =
      Number(
        entry.header?.size ||
        0
      );

    if (
      declared >
      MAX_ENTRY
    ) {
      throw new Error(
        "ZIP içindeki tek dosya boyut sınırını aşıyor."
      );
    }

    declaredTotal +=
      Math.max(
        0,
        declared
      );

    if (
      declaredTotal >
      MAX_TOTAL
    ) {
      throw new Error(
        "ZIP açılmış toplam boyut sınırını aşıyor."
      );
    }
  }

  for (
    const entry of entries
  ) {
    const rawName =
      String(
        entry.entryName ||
        ""
      ).replaceAll(
        "\\",
        "/"
      );

    const target =
      path.resolve(
        root,
        rawName
      );

    if (entry.isDirectory) {
      await fs.mkdir(
        target,
        { recursive: true }
      );
      continue;
    }

    const data =
      entry.getData();

    if (
      data.length >
      MAX_ENTRY
    ) {
      throw new Error(
        "ZIP dosyası açılırken boyut sınırını aştı."
      );
    }

    actualTotal +=
      data.length;

    if (
      actualTotal >
      MAX_TOTAL
    ) {
      throw new Error(
        "ZIP gerçek açılmış boyut sınırını aştı."
      );
    }

    await fs.mkdir(
      path.dirname(target),
      { recursive: true }
    );

    await fs.writeFile(
      target,
      data
    );
  }

  const index =
    await findFirst(
      root,
      p =>
        path
          .basename(p)
          .toLowerCase() ===
        "index.html"
    );

  if (!index) {
    throw new Error(
      "index.html bulunamadı."
    );
  }

  if (
    path.dirname(index) !==
    root
  ) {
    const nested =
      path.dirname(index);

    const temp =
      path.join(
        path.dirname(root),
        "_rebased_site"
      );

    await fs.rm(
      temp,
      {
        recursive: true,
        force: true
      }
    );

    await fs.cp(
      nested,
      temp,
      { recursive: true }
    );

    await fs.rm(
      root,
      {
        recursive: true,
        force: true
      }
    );

    await fs.rename(
      temp,
      root
    );
  }
}


async function runGradle(
  buildId,
  cwd,
  tasks,
  env
) {
  return new Promise(
    (resolve, reject) => {
      let settled = false;
      let cancellationRequested = false;

      const recentGradleLines = [];

      const rememberGradleLine = line => {
        const text =
          String(line || "").trim();

        if (!text) return;

        recentGradleLines.push(text);

        if (
          recentGradleLines.length > 80
        ) {
          recentGradleLines.shift();
        }
      };

      const child =
        spawn(
          config.gradleBin,
          [
            ...tasks,
            "--no-daemon",
            "--stacktrace"
          ],
          {
            cwd,
            shell:
              process.platform ===
              "win32",
            env: {
              ...env,
              GRADLE_OPTS:
                "-Xmx128m " +
                "-XX:MaxMetaspaceSize=96m " +
                "-XX:+UseSerialGC " +
                "-Dfile.encoding=UTF-8 " +
                "-Dorg.gradle.daemon=false"
            }
          }
        );

      const terminate =
        () => {
          if (
            !child ||
            child.killed
          ) {
            return;
          }

          if (
            process.platform ===
            "win32"
          ) {
            spawn(
              "taskkill",
              [
                "/PID",
                String(child.pid),
                "/T",
                "/F"
              ],
              {
                windowsHide: true
              }
            );
          } else {
            try {
              child.kill("SIGTERM");
            } catch {}

            setTimeout(
              () => {
                if (
                  !child.killed
                ) {
                  try {
                    child.kill(
                      "SIGKILL"
                    );
                  } catch {}
                }
              },
              4000
            ).unref();
          }
        };

      const poll =
        setInterval(
          async () => {
            try {
              const result =
                await query(
                  `SELECT cancel_requested
                   FROM appforge_builds
                   WHERE id = $1`,
                  [buildId]
                );

              if (
                result.rows[0]
                  ?.cancel_requested
              ) {
                cancellationRequested =
                  true;

                await appendLog(
                  buildId,
                  "İptal isteği alındı; Gradle süreci durduruluyor..."
                );

                terminate();
              }
            } catch {}
          },
          1000
        );

      poll.unref();

      child.stdout.on(
        "data",
        data => {
          String(data)
            .split(/\r?\n/)
            .filter(Boolean)
            .forEach(
              line => {
                rememberGradleLine(line);

                appendLog(
                  buildId,
                  line
                ).catch(
                  () => {}
                );
              }
            );
        }
      );

      child.stderr.on(
        "data",
        data => {
          String(data)
            .split(/\r?\n/)
            .filter(Boolean)
            .forEach(
              line => {
                rememberGradleLine(line);

                appendLog(
                  buildId,
                  line
                ).catch(
                  () => {}
                );
              }
            );
        }
      );

      child.on(
        "error",
        error => {
          if (settled) return;
          settled = true;
          clearInterval(poll);
          reject(error);
        }
      );

      child.on(
        "close",
        code => {
          if (settled) return;
          settled = true;
          clearInterval(poll);

          if (
            cancellationRequested
          ) {
            reject(
              cancelledError()
            );
            return;
          }

          if (code === 0) {
            resolve();
          } else {
            const tail =
              recentGradleLines
                .slice(-80)
                .join("\n");

            console.error(
              `[GRADLE FAILURE ${buildId}]\n${tail}`
            );

            reject(
              new Error(
                `Gradle başarısız. Çıkış kodu: ${code}` +
                (
                  tail
                    ? `\n\n--- GRADLE SON ÇIKTI ---\n${tail}`
                    : ""
                )
              )
            );
          }
        }
      );
    }
  );
}

async function findFirst(root, predicate) {
  const entries = await fs.readdir(root, { withFileTypes: true });

  for (const entry of entries) {
    const full = path.join(root, entry.name);

    if (entry.isDirectory()) {
      const nested = await findFirst(full, predicate);
      if (nested) return nested;
    } else if (predicate(full.replaceAll("\\", "/"))) {
      return full;
    }
  }

  return null;
}
