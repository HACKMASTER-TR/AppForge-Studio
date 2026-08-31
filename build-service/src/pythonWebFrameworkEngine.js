import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import path from "path";
import { fileURLToPath } from "url";

const PYWEB_ENGINE_DIR =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const PYWEB_SERVICE_ROOT =
  path.resolve(
    PYWEB_ENGINE_DIR,
    ".."
  );

const MAX_ZIP_ENTRIES =
  12_000;

const MAX_UNCOMPRESSED_BYTES =
  250 * 1024 * 1024;

const MAX_TEXT_BYTES =
  2 * 1024 * 1024;

function safeInside(
  root,
  candidate
) {
  const a =
    path.resolve(
      root
    );

  const b =
    path.resolve(
      candidate
    );

  return (
    b ===
      a ||
    b.startsWith(
      a +
        path.sep
    )
  );
}

function ignoredPath(
  segments
) {
  const ignored =
    new Set([
      ".git",
      ".idea",
      ".gradle",
      ".venv",
      "venv",
      "__pycache__",
      "node_modules",
      "build",
      "dist"
    ]);

  return segments.some(
    segment =>
      ignored.has(
        segment
      )
  );
}

async function extractPythonWebZip(
  zipPath,
  destination
) {
  const zip =
    new AdmZip(
      zipPath
    );

  const entries =
    zip.getEntries();

  if (
    entries.length >
      MAX_ZIP_ENTRIES
  ) {
    throw new Error(
      "Python web projesinde çok fazla ZIP girdisi var."
    );
  }

  await fs.rm(
    destination,
    {
      recursive: true,
      force: true
    }
  );

  await fs.mkdir(
    destination,
    {
      recursive: true
    }
  );

  let totalBytes =
    0;

  for (
    const entry of
    entries
  ) {
    const raw =
      String(
        entry.entryName ||
        ""
      )
        .replaceAll(
          "\\",
          "/"
        );

    if (
      !raw ||
      raw.startsWith(
        "/"
      ) ||
      raw.includes(
        "\0"
      )
    ) {
      throw new Error(
        "Python web ZIP yolu güvenli değil."
      );
    }

    const normalized =
      path.posix.normalize(
        raw
      );

    if (
      normalized ===
        ".." ||
      normalized.startsWith(
        "../"
      )
    ) {
      throw new Error(
        "Python web ZIP dizin dışına çıkmaya çalışıyor."
      );
    }

    const segments =
      normalized
        .split(
          "/"
        )
        .filter(
          Boolean
        );

    if (
      !segments.length ||
      ignoredPath(
        segments
      )
    ) {
      continue;
    }

    const target =
      path.join(
        destination,
        ...segments
      );

    if (
      !safeInside(
        destination,
        target
      )
    ) {
      throw new Error(
        "Python web ZIP hedef yolu güvenli değil."
      );
    }

    if (
      entry.isDirectory
    ) {
      await fs.mkdir(
        target,
        {
          recursive: true
        }
      );

      continue;
    }

    const data =
      entry.getData();

    totalBytes +=
      data.length;

    if (
      totalBytes >
        MAX_UNCOMPRESSED_BYTES
    ) {
      throw new Error(
        "Python web ZIP açıldığında boyut sınırını aşıyor."
      );
    }

    await fs.mkdir(
      path.dirname(
        target
      ),
      {
        recursive: true
      }
    );

    await fs.writeFile(
      target,
      data
    );
  }

  return {
    entries:
      entries.length,
    bytes:
      totalBytes
  };
}

async function walk(
  root,
  {
    maxDepth = 8,
    maxFiles = 8_000
  } = {}
) {
  const result =
    [];

  async function visit(
    dir,
    depth
  ) {
    if (
      depth >
        maxDepth ||
      result.length >=
        maxFiles
    ) {
      return;
    }

    let entries;

    try {
      entries =
        await fs.readdir(
          dir,
          {
            withFileTypes:
              true
          }
        );
    } catch {
      return;
    }

    for (
      const entry of
      entries
    ) {
      if (
        result.length >=
          maxFiles
      ) {
        break;
      }

      if (
        ignoredPath(
          [
            entry.name
          ]
        )
      ) {
        continue;
      }

      const full =
        path.join(
          dir,
          entry.name
        );

      if (
        entry.isDirectory()
      ) {
        await visit(
          full,
          depth + 1
        );
      } else if (
        entry.isFile()
      ) {
        result.push(
          full
        );
      }
    }
  }

  await visit(
    root,
    0
  );

  return result;
}

async function readSmallText(
  file
) {
  try {
    const stat =
      await fs.stat(
        file
      );

    if (
      !stat.isFile() ||
      stat.size >
        MAX_TEXT_BYTES
    ) {
      return "";
    }

    return await fs.readFile(
      file,
      "utf8"
    );
  } catch {
    return "";
  }
}

function pythonModuleName(
  projectRoot,
  file
) {
  const relative =
    path.relative(
      projectRoot,
      file
    )
      .replaceAll(
        "\\",
        "/"
      )
      .replace(
        /\.py$/i,
        ""
      );

  return relative
    .split(
      "/"
    )
    .filter(
      Boolean
    )
    .join(
      "."
    );
}

function normalizeRequirementName(
  value
) {
  return String(
    value ||
    ""
  )
    .trim()
    .split(
      /[<>=!~;\[\]\s]/
    )[0]
    .trim()
    .toLowerCase()
    .replaceAll(
      "_",
      "-"
    );
}

export function parsePythonRequirements(
  text
) {
  const dependencies =
    [];

  const ignored =
    [];

  for (
    const rawLine of
    String(
      text ||
      ""
    )
      .split(
        /\r?\n/
      )
  ) {
    const line =
      rawLine
        .trim();

    if (
      !line ||
      line.startsWith(
        "#"
      )
    ) {
      continue;
    }

    if (
      line.startsWith(
        "-"
      ) ||
      line.includes(
        "://"
      ) ||
      line.startsWith(
        "."
      ) ||
      line.startsWith(
        "/"
      )
    ) {
      ignored.push(
        line
      );

      continue;
    }

    const name =
      normalizeRequirementName(
        line
      );

    if (
      name &&
      !dependencies.includes(
        name
      )
    ) {
      dependencies.push(
        name
      );
    }
  }

  return {
    dependencies,
    ignored
  };
}

function flaskContract(
  projectRoot,
  entryFile,
  source
) {
  const moduleName =
    pythonModuleName(
      projectRoot,
      entryFile
    );

  const appObject =
    String(
      source ||
      ""
    )
      .match(
        /^\s*([A-Za-z_]\w*)\s*=\s*Flask\s*\(/m
      )?.[1] ||
    null;

  const hasFactory =
    /^\s*def\s+create_app\s*\(/m
      .test(
        String(
          source ||
          ""
        )
      );

  return {
    framework:
      "flask",
    entryFile,
    moduleName,
    appObject,
    factory:
      hasFactory
        ? "create_app"
        : null,
    ready:
      Boolean(
        appObject ||
        hasFactory
      ),
    reason:
      appObject ||
      hasFactory
        ? "Flask app object/factory bulundu."
        : "Flask bulundu ancak app = Flask(...) veya create_app() giriş kontratı bulunamadı."
  };
}

async function detectFlask(
  projectRoot,
  files
) {
  const candidates =
    files
      .filter(
        file => {
          const name =
            path.basename(
              file
            )
              .toLowerCase();

          return (
            name ===
              "app.py" ||
            name ===
              "main.py"
          );
        }
      )
      .sort(
        (
          a,
          b
        ) =>
          a.split(
            path.sep
          ).length -
          b.split(
            path.sep
          ).length
      );

  for (
    const file of
    candidates
  ) {
    const source =
      await readSmallText(
        file
      );

    if (
      /\bFlask\s*\(/.test(
        source
      ) ||
      /\bfrom\s+flask\b/.test(
        source
      ) ||
      /\bimport\s+flask\b/.test(
        source
      )
    ) {
      return flaskContract(
        projectRoot,
        file,
        source
      );
    }
  }

  return null;
}

async function detectDjango(
  projectRoot,
  files
) {
  const manage =
    files.find(
      file =>
        path.basename(
          file
        )
          .toLowerCase() ===
        "manage.py"
    );

  if (
    !manage
  ) {
    return null;
  }

  const settings =
    files
      .filter(
        file =>
          path.basename(
            file
          )
            .toLowerCase() ===
          "settings.py"
      )
      .sort(
        (
          a,
          b
        ) =>
          a.split(
            path.sep
          ).length -
          b.split(
            path.sep
          ).length
      )[0] ||
    null;

  const wsgi =
    files
      .filter(
        file =>
          path.basename(
            file
          )
            .toLowerCase() ===
          "wsgi.py"
      )
      .sort(
        (
          a,
          b
        ) =>
          a.split(
            path.sep
          ).length -
          b.split(
            path.sep
          ).length
      )[0] ||
    null;

  if (
    !settings
  ) {
    return {
      framework:
        "django",
      manageFile:
        manage,
      settingsFile:
        null,
      settingsModule:
        null,
      wsgiFile:
        wsgi,
      wsgiModule:
        wsgi
          ? pythonModuleName(
              projectRoot,
              wsgi
            )
          : null,
      ready:
        false,
      reason:
        "Django manage.py bulundu ancak settings.py bulunamadı."
    };
  }

  return {
    framework:
      "django",
    manageFile:
      manage,
    settingsFile:
      settings,
    settingsModule:
      pythonModuleName(
        projectRoot,
        settings
      ),
    wsgiFile:
      wsgi,
    wsgiModule:
      wsgi
        ? pythonModuleName(
            projectRoot,
            wsgi
          )
        : null,
    ready:
      Boolean(
        wsgi
      ),
    reason:
      wsgi
        ? "Django settings.py ve wsgi.py bulundu."
        : "Django settings.py bulundu ancak wsgi.py bulunamadı."
  };
}

async function findProjectRoot(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot
    );

  const anchors =
    files
      .filter(
        file => {
          const name =
            path.basename(
              file
            )
              .toLowerCase();

          return (
            name ===
              "manage.py" ||
            name ===
              "app.py" ||
            name ===
              "main.py" ||
            name ===
              "requirements.txt" ||
            name ===
              "pyproject.toml"
          );
        }
      )
      .sort(
        (
          a,
          b
        ) =>
          a.split(
            path.sep
          ).length -
          b.split(
            path.sep
          ).length
      );

  if (
    !anchors.length
  ) {
    throw new Error(
      "Python web proje kökü bulunamadı."
    );
  }

  const manage =
    anchors.find(
      file =>
        path.basename(
          file
        )
          .toLowerCase() ===
        "manage.py"
    );

  if (
    manage
  ) {
    return path.dirname(
      manage
    );
  }

  return path.dirname(
    anchors[0]
  );
}

function databaseHints(
  dependencies,
  filesText
) {
  const names =
    new Set(
      dependencies
    );

  const hints =
    [];

  if (
    [
      "psycopg",
      "psycopg2",
      "asyncpg"
    ].some(
      name =>
        names.has(
          name
        )
    ) ||
    /postgres/i.test(
      filesText
    )
  ) {
    hints.push(
      "postgresql"
    );
  }

  if (
    [
      "mysqlclient",
      "pymysql"
    ].some(
      name =>
        names.has(
          name
        )
    ) ||
    /mysql/i.test(
      filesText
    )
  ) {
    hints.push(
      "mysql"
    );
  }

  return hints;
}

export async function preparePythonWebFrameworkSource({
  projectZip,
  workDir,
  technology = null,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "Python web kaynak ZIP'i eksik."
    );
  }

  if (
    cancelled
  ) {
    await cancelled();
  }

  const extractedRoot =
    path.join(
      workDir,
      "source"
    );

  const extracted =
    await extractPythonWebZip(
      projectZip,
      extractedRoot
    );

  const projectRoot =
    await findProjectRoot(
      extractedRoot
    );

  const files =
    await walk(
      projectRoot
    );

  const requirementsFile =
    files.find(
      file =>
        path.basename(
          file
        )
          .toLowerCase() ===
        "requirements.txt"
    ) ||
    null;

  const requirements =
    parsePythonRequirements(
      requirementsFile
        ? await readSmallText(
            requirementsFile
          )
        : ""
    );

  const django =
    await detectDjango(
      projectRoot,
      files
    );

  const flask =
    django
      ? null
      : await detectFlask(
          projectRoot,
          files
        );

  const contract =
    django ||
    flask;

  if (
    !contract
  ) {
    throw new Error(
      "Flask veya Django proje imzası bulunamadı."
    );
  }

  const framework =
    contract.framework;

  const sourcePreview =
    (
      await Promise.all(
        files
          .filter(
            file =>
              path.extname(
                file
              )
                .toLowerCase() ===
              ".py"
          )
          .slice(
            0,
            30
          )
          .map(
            file =>
              readSmallText(
                file
              )
          )
      )
    )
      .join(
        "\n"
      );

  const dbHints =
    databaseHints(
      requirements.dependencies,
      sourcePreview
    );

  if (
    onLog
  ) {
    await onLog(
      `🐍 ${framework} Android runtime foundation • ${contract.ready ? "giriş kontratı hazır" : "eksik"}`
    );
  }

  return {
    technology:
      technology ||
      (
        framework ===
          "flask"
          ? "python-flask"
          : "python-django"
      ),
    framework,
    projectRoot,
    contract,
    requirementsFile,
    dependencies:
      requirements.dependencies,
    ignoredRequirements:
      requirements.ignored,
    databaseHints:
      dbHints,
    localServerHost:
      "127.0.0.1",
    localServerPort:
      8765,
    runtimePlan:
      framework ===
        "flask"
        ? "chaquopy-flask-loopback-webview"
        : "chaquopy-django-wsgi-loopback-webview",
    buildReady:
      false,
    buildBlockedReason:
      "Chaquopy dependency install + Android loopback server/WebView canlı hattı sonraki aşamada bağlanacak.",
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}

const PYWEB_ALLOWED_RESOURCE_EXTENSIONS =
  new Set([
    ".py", ".html", ".htm", ".css", ".js", ".mjs",
    ".json", ".txt", ".csv", ".xml", ".yaml", ".yml",
    ".toml", ".ini", ".cfg", ".md", ".svg", ".png",
    ".jpg", ".jpeg", ".webp", ".gif", ".ico",
    ".woff", ".woff2", ".ttf", ".otf"
  ]);

const PYWEB_MAX_FILE_BYTES =
  24 * 1024 * 1024;

const PYWEB_MAX_COPY_BYTES =
  220 * 1024 * 1024;

function pywebKotlinLiteral(
  value
) {
  return JSON.stringify(
    String(
      value ?? ""
    )
  );
}

function pywebXmlEscape(
  value
) {
  return String(
    value ?? ""
  )
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&apos;");
}

function safeRequirementSpec(
  raw
) {
  const value =
    String(
      raw ||
      ""
    )
      .trim();

  if (
    !value ||
    value.startsWith("#") ||
    value.startsWith("-") ||
    value.startsWith(".") ||
    value.startsWith("/") ||
    value.includes("://") ||
    value.includes("@") ||
    value.includes(";") ||
    value.includes("\\")
  ) {
    return null;
  }

  const compact =
    value.replace(
      /\s+/g,
      ""
    );

  const pattern =
    new RegExp(
      "^[A-Za-z0-9_.-]+(?:\\\\[[A-Za-z0-9_,.-]+\\\\])?" +
      "(?:(?:==|!=|~=|>=|<=|>|<)[A-Za-z0-9_.+*!-]+" +
      "(?:,(?:==|!=|~=|>=|<=|>|<)[A-Za-z0-9_.+*!-]+)*)?$"
    );

  return pattern.test(compact)
    ? compact
    : null;
}

export function parseSafePythonRequirementSpecs(
  content
) {
  const specs =
    [];

  const rejected =
    [];

  for (
    const rawLine of
    String(
      content ||
      ""
    )
      .split(
        /\r?\n/
      )
  ) {
    const line =
      rawLine.trim();

    if (
      !line ||
      line.startsWith("#")
    ) {
      continue;
    }

    const spec =
      safeRequirementSpec(
        line
      );

    if (
      !spec
    ) {
      rejected.push(
        line
      );

      continue;
    }

    if (
      !specs.includes(
        spec
      )
    ) {
      specs.push(
        spec
      );
    }
  }

  return {
    specs,
    rejected
  };
}

function requirementName(
  spec
) {
  return String(
    spec ||
    ""
  )
    .split(
      /[<>=!~\[\]]/
    )[0]
    .trim()
    .toLowerCase()
    .replaceAll(
      "_",
      "-"
    );
}

function withFrameworkRequirement(
  framework,
  specs
) {
  const names =
    new Set(
      specs.map(
        requirementName
      )
    );

  const result =
    [
      ...specs
    ];

  if (
    framework === "flask" &&
    !names.has("flask")
  ) {
    result.push(
      "Flask>=3,<4"
    );
  }

  if (
    framework === "django" &&
    !names.has("django")
  ) {
    result.push(
      "Django>=5,<6"
    );
  }

  return result;
}

async function copyPythonWebProject(
  projectRoot,
  pythonDest
) {
  const files =
    await walk(
      projectRoot,
      {
        maxDepth: 12,
        maxFiles: 12_000
      }
    );

  let copied =
    0;

  let bytes =
    0;

  for (
    const file of
    files
  ) {
    const relative =
      path.relative(
        projectRoot,
        file
      );

    const segments =
      relative
        .replaceAll("\\", "/")
        .split("/")
        .filter(Boolean);

    if (
      ignoredPath(
        segments
      )
    ) {
      continue;
    }

    const ext =
      path.extname(
        file
      )
        .toLowerCase();

    if (
      !PYWEB_ALLOWED_RESOURCE_EXTENSIONS.has(
        ext
      )
    ) {
      continue;
    }

    if (
      path.basename(file) ===
      "appforge_web_runtime.py"
    ) {
      continue;
    }

    const stat =
      await fs.stat(
        file
      );

    if (
      stat.size >
      PYWEB_MAX_FILE_BYTES
    ) {
      throw new Error(
        `Python web kaynak dosyası çok büyük: ${relative}`
      );
    }

    bytes +=
      stat.size;

    if (
      bytes >
      PYWEB_MAX_COPY_BYTES
    ) {
      throw new Error(
        "Python web kaynakları toplam boyut sınırını aşıyor."
      );
    }

    const target =
      path.join(
        pythonDest,
        relative
      );

    if (
      !safeInside(
        pythonDest,
        target
      )
    ) {
      throw new Error(
        "Python web kaynak kopyalama yolu güvenli değil."
      );
    }

    await fs.mkdir(
      path.dirname(target),
      {
        recursive: true
      }
    );

    await fs.copyFile(
      file,
      target
    );

    copied +=
      1;
  }

  if (
    copied ===
    0
  ) {
    throw new Error(
      "Android runtime'a aktarılacak Python web kaynağı bulunamadı."
    );
  }

  return {
    copied,
    bytes
  };
}

function flaskRuntimeSource(
  prepared
) {
  const moduleName =
    JSON.stringify(
      prepared.contract.moduleName
    );

  const appObject =
    prepared.contract.appObject
      ? JSON.stringify(
          prepared.contract.appObject
        )
      : null;

  const factory =
    prepared.contract.factory
      ? JSON.stringify(
          prepared.contract.factory
        )
      : null;

  const appLookup =
    appObject
      ? `app = getattr(module, ${appObject}, None)`
      : "app = None";

  const factoryLookup =
    factory
      ? `factory = getattr(module, ${factory}, None)
        if callable(factory):
            app = factory()`
      : "pass";

  return `import importlib
import threading
import traceback
from wsgiref.simple_server import make_server, WSGIRequestHandler

HOST = "127.0.0.1"
PORT = 8765
_server = None
_thread = None


class QuietHandler(WSGIRequestHandler):
    def log_message(self, format, *args):
        return


def _application():
    module = importlib.import_module(${moduleName})
    ${appLookup}

    if app is None:
        ${factoryLookup}

    if app is None:
        raise RuntimeError("Flask app object/factory bulunamadı.")

    return app


def start():
    global _server, _thread

    if _thread is not None and _thread.is_alive():
        return f"http://{HOST}:{PORT}/"

    try:
        _server = make_server(
            HOST,
            PORT,
            _application(),
            handler_class=QuietHandler,
        )

        _thread = threading.Thread(
            target=_server.serve_forever,
            name="AppForgeFlask",
            daemon=True,
        )

        _thread.start()
        return f"http://{HOST}:{PORT}/"
    except Exception:
        raise RuntimeError(traceback.format_exc())
`;
}

function djangoRuntimeSource(
  prepared
) {
  return `import importlib
import os
import threading
import traceback
from wsgiref.simple_server import make_server, WSGIRequestHandler

HOST = "127.0.0.1"
PORT = 8765
_server = None
_thread = None


class QuietHandler(WSGIRequestHandler):
    def log_message(self, format, *args):
        return


def _application():
    os.environ.setdefault(
        "DJANGO_SETTINGS_MODULE",
        ${JSON.stringify(prepared.contract.settingsModule)},
    )

    import django
    django.setup()

    module = importlib.import_module(
        ${JSON.stringify(prepared.contract.wsgiModule)}
    )

    application = getattr(
        module,
        "application",
        None,
    )

    if application is None:
        raise RuntimeError("Django WSGI application bulunamadı.")

    return application


def start():
    global _server, _thread

    if _thread is not None and _thread.is_alive():
        return f"http://{HOST}:{PORT}/"

    try:
        _server = make_server(
            HOST,
            PORT,
            _application(),
            handler_class=QuietHandler,
        )

        _thread = threading.Thread(
            target=_server.serve_forever,
            name="AppForgeDjango",
            daemon=True,
        )

        _thread.start()
        return f"http://{HOST}:{PORT}/"
    except Exception:
        raise RuntimeError(traceback.format_exc())
`;
}

function webMainActivitySource() {
  return `package com.appforge.pythonruntime

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private lateinit var webView: WebView

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val loading =
            TextView(this).apply {
                text = "Python web uygulaması başlatılıyor..."
                textSize = 16f
                setPadding(32, 32, 32, 32)
            }

        setContentView(loading)

        Thread {
            val result =
                runCatching {
                    if (!Python.isStarted()) {
                        Python.start(
                            AndroidPlatform(this)
                        )
                    }

                    val url =
                        Python
                            .getInstance()
                            .getModule(
                                "appforge_web_runtime"
                            )
                            .callAttr(
                                "start"
                            )
                            .toString()

                    waitForServer(url)
                    url
                }

            runOnUiThread {
                result
                    .onSuccess {
                        showWebView(it)
                    }
                    .onFailure {
                        loading.text =
                            "Python web çalışma hatası:\\n" +
                            (
                                it.message
                                    ?: it.javaClass.simpleName
                            )
                    }
            }
        }.start()
    }

    private fun waitForServer(
        url: String
    ) {
        var lastError:
            Throwable? =
            null

        repeat(60) {
            try {
                val connection =
                    (
                        URL(url)
                            .openConnection() as
                            HttpURLConnection
                    ).apply {
                        connectTimeout = 500
                        readTimeout = 500
                        instanceFollowRedirects = false
                    }

                try {
                    if (
                        connection.responseCode >=
                        100
                    ) {
                        return
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (
                error: Throwable
            ) {
                lastError = error
            }

            Thread.sleep(250)
        }

        throw IllegalStateException(
            "Local Python server başlatılamadı.",
            lastError
        )
    }

    private fun showWebView(
        startUrl: String
    ) {
        webView = WebView(this)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri =
                        request?.url
                            ?: return false

                    if (
                        uri.host == "127.0.0.1" &&
                        uri.port == 8765
                    ) {
                        return false
                    }

                    if (
                        uri.scheme == "http" ||
                        uri.scheme == "https"
                    ) {
                        runCatching {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    uri
                                )
                            )
                        }

                        return true
                    }

                    return true
                }
            }

        setContentView(webView)
        webView.loadUrl(startUrl)
    }

    override fun onBackPressed() {
        if (
            ::webView.isInitialized &&
            webView.canGoBack()
        ) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }

        super.onDestroy()
    }
}
`;
}

function webManifestSource(
  appName
) {
  return `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="false"
        android:label="${pywebXmlEscape(appName || "Python Web App")}"
        android:supportsRtl="true"
        android:theme="@style/AppTheme"
        android:usesCleartextTraffic="true">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
`;
}

function pythonWebGradleSource({
  packageName,
  versionCode,
  versionName,
  requirementSpecs
}) {
  const installs =
    requirementSpecs
      .map(
        spec =>
          `            install(${pywebKotlinLiteral(spec)})`
      )
      .join(
        "\n"
      );

  return `plugins {
    id("com.android.application")
    id("com.chaquo.python")
}

android {
    namespace = "com.appforge.pythonruntime"
    compileSdk = 37

    defaultConfig {
        applicationId = ${pywebKotlinLiteral(packageName)}
        minSdk = 26
        targetSdk = 37
        versionCode = ${Number(versionCode) || 1}
        versionName = ${pywebKotlinLiteral(versionName || "1.0.0")}

        ndk {
            abiFilters += listOf(
                "arm64-v8a",
                "x86_64"
            )
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig =
                signingConfigs.getByName("debug")
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"

        buildPython(
            "/usr/bin/python3"
        )

        extractPackages(
            "*"
        )

        pip {
${installs}
        }
    }
}
`;
}

export async function preparePythonWebAndroidProject({
  projectZip,
  workDir,
  androidProjectDir,
  config,
  onLog = null,
  cancelled = null
}) {
  const prepared =
    await preparePythonWebFrameworkSource(
      {
        projectZip,
        workDir,
        technology:
          config?.sourceTechnology,
        onLog,
        cancelled
      }
    );

  if (
    !prepared.contract.ready
  ) {
    throw new Error(
      prepared.contract.reason
    );
  }

  if (
    prepared.ignoredRequirements.length
  ) {
    throw new Error(
      "Python web requirements.txt içinde güvenli otomatik Android paketlemede desteklenmeyen satırlar var: " +
      prepared.ignoredRequirements
        .slice(0, 6)
        .join(", ")
    );
  }

  const requirementsText =
    prepared.requirementsFile
      ? await readSmallText(
          prepared.requirementsFile
        )
      : "";

  const safe =
    parseSafePythonRequirementSpecs(
      requirementsText
    );

  if (
    safe.rejected.length
  ) {
    throw new Error(
      "requirements.txt yalnız standart PyPI requirement specifier satırları içermeli. Desteklenmeyen: " +
      safe.rejected
        .slice(0, 6)
        .join(", ")
    );
  }

  const requirementSpecs =
    withFrameworkRequirement(
      prepared.framework,
      safe.specs
    );

  const templateRoot =
    path.join(
      PYWEB_SERVICE_ROOT,
      "python-android-template"
    );

  try {
    if (
      !(
        await fs.stat(
          templateRoot
        )
      ).isDirectory()
    ) {
      throw new Error();
    }
  } catch {
    throw new Error(
      "Python Android runtime template bulunamadı."
    );
  }

  await fs.rm(
    androidProjectDir,
    {
      recursive: true,
      force: true
    }
  );

  await fs.cp(
    templateRoot,
    androidProjectDir,
    {
      recursive: true,
      force: true
    }
  );

  const appDir =
    path.join(
      androidProjectDir,
      "app"
    );

  const pythonDest =
    path.join(
      appDir,
      "src",
      "main",
      "python"
    );

  await fs.rm(
    pythonDest,
    {
      recursive: true,
      force: true
    }
  );

  await fs.mkdir(
    pythonDest,
    {
      recursive: true
    }
  );

  const copied =
    await copyPythonWebProject(
      prepared.projectRoot,
      pythonDest
    );

  await fs.writeFile(
    path.join(
      pythonDest,
      "appforge_web_runtime.py"
    ),
    prepared.framework === "flask"
      ? flaskRuntimeSource(prepared)
      : djangoRuntimeSource(prepared),
    "utf8"
  );

  await fs.writeFile(
    path.join(
      appDir,
      "build.gradle.kts"
    ),
    pythonWebGradleSource(
      {
        packageName:
          config?.packageName,
        versionCode:
          config?.versionCode,
        versionName:
          config?.versionName,
        requirementSpecs
      }
    ),
    "utf8"
  );

  await fs.writeFile(
    path.join(
      appDir,
      "src",
      "main",
      "AndroidManifest.xml"
    ),
    webManifestSource(
      config?.appName
    ),
    "utf8"
  );

  const activity =
    path.join(
      appDir,
      "src",
      "main",
      "java",
      "com",
      "appforge",
      "pythonruntime",
      "MainActivity.kt"
    );

  await fs.mkdir(
    path.dirname(activity),
    {
      recursive: true
    }
  );

  await fs.writeFile(
    activity,
    webMainActivitySource(),
    "utf8"
  );

  if (cancelled) {
    await cancelled();
  }

  if (onLog) {
    await onLog(
      `✅ ${prepared.framework} -> Chaquopy loopback WebView Android proje hazır • ${copied.copied} dosya • ${requirementSpecs.length} dependency`
    );
  }

  return {
    androidProjectDir,
    framework:
      prepared.framework,
    contract:
      prepared.contract,
    sourceFiles:
      copied.copied,
    sourceBytes:
      copied.bytes,
    requirementSpecs,
    localServer:
      "http://127.0.0.1:8765/",
    runtimeMode:
      "chaquopy-loopback-webview"
  };
}
