import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  parseSafePythonRequirementSpecs,
  preparePythonWebAndroidProject
} from "../src/pythonWebFrameworkEngine.js";

async function makeZip(
  root,
  files
) {
  const project =
    path.join(
      root,
      "project"
    );

  for (
    const [
      relative,
      content
    ] of
    Object.entries(
      files
    )
  ) {
    const target =
      path.join(
        project,
        relative
      );

    await fs.mkdir(
      path.dirname(target),
      {
        recursive: true
      }
    );

    await fs.writeFile(
      target,
      content
    );
  }

  const zipPath =
    path.join(
      root,
      "project.zip"
    );

  const zip =
    new AdmZip();

  zip.addLocalFolder(
    project
  );

  zip.writeZip(
    zipPath
  );

  return zipPath;
}

test(
  "safe requirements accept PyPI specs and reject remote/local forms",
  () => {
    const parsed =
      parseSafePythonRequirementSpecs(
`Flask==3.1.2
requests>=2.32,<3
-r private.txt
git+https://example.com/repo.git
./local-package
`
      );

    assert.deepEqual(
      parsed.specs,
      [
        "Flask==3.1.2",
        "requests>=2.32,<3"
      ]
    );

    assert.equal(
      parsed.rejected.length,
      3
    );
  }
);

test(
  "Flask becomes Chaquopy loopback WebView Android project",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-flask-live-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "requirements.txt":
              "Flask==3.1.2\n",
            "app.py":
`from flask import Flask
app = Flask(__name__)

@app.get("/")
def index():
    return "<h1>OK</h1>"
`,
            "templates/index.html":
              "<h1>template</h1>\n",
            "static/app.css":
              "body { font-family: sans-serif; }\n"
          }
        );

      const android =
        path.join(
          root,
          "android"
        );

      const result =
        await preparePythonWebAndroidProject(
          {
            projectZip:
              zipPath,
            workDir:
              path.join(
                root,
                "work"
              ),
            androidProjectDir:
              android,
            config: {
              sourceTechnology:
                "python-flask",
              appName:
                "Flask Test",
              packageName:
                "com.example.flasktest",
              versionCode:
                3,
              versionName:
                "1.2.0"
            }
          }
        );

      assert.equal(
        result.framework,
        "flask"
      );

      assert.equal(
        result.runtimeMode,
        "chaquopy-loopback-webview"
      );

      const gradle =
        await fs.readFile(
          path.join(
            android,
            "app",
            "build.gradle.kts"
          ),
          "utf8"
        );

      assert.match(
        gradle,
        /version = "3\.11"/
      );

      assert.match(
        gradle,
        /extractPackages/
      );

      assert.match(
        gradle,
        /Flask==3\.1\.2/
      );

      const manifest =
        await fs.readFile(
          path.join(
            android,
            "app",
            "src",
            "main",
            "AndroidManifest.xml"
          ),
          "utf8"
        );

      assert.match(
        manifest,
        /android\.permission\.INTERNET/
      );

      assert.match(
        manifest,
        /usesCleartextTraffic="true"/
      );

      const runtime =
        await fs.readFile(
          path.join(
            android,
            "app",
            "src",
            "main",
            "python",
            "appforge_web_runtime.py"
          ),
          "utf8"
        );

      assert.match(
        runtime,
        /make_server/
      );

      assert.match(
        runtime,
        /127\.0\.0\.1/
      );

      const activity =
        await fs.readFile(
          path.join(
            android,
            "app",
            "src",
            "main",
            "java",
            "com",
            "appforge",
            "pythonruntime",
            "MainActivity.kt"
          ),
          "utf8"
        );

      assert.match(
        activity,
        /WebView/
      );
    } finally {
      await fs.rm(
        root,
        {
          recursive: true,
          force: true
        }
      );
    }
  }
);

test(
  "Django generates WSGI runtime",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-django-live-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "requirements.txt":
              "Django==5.2.5\n",
            "manage.py":
              "# manage\n",
            "mysite/__init__.py":
              "",
            "mysite/settings.py":
`SECRET_KEY = "test"
ROOT_URLCONF = "mysite.urls"
`,
            "mysite/wsgi.py":
`import os
from django.core.wsgi import get_wsgi_application
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "mysite.settings")
application = get_wsgi_application()
`
          }
        );

      const android =
        path.join(
          root,
          "android"
        );

      const result =
        await preparePythonWebAndroidProject(
          {
            projectZip:
              zipPath,
            workDir:
              path.join(
                root,
                "work"
              ),
            androidProjectDir:
              android,
            config: {
              sourceTechnology:
                "python-django",
              appName:
                "Django Test",
              packageName:
                "com.example.djangotest",
              versionCode:
                1,
              versionName:
                "1.0.0"
            }
          }
        );

      assert.equal(
        result.framework,
        "django"
      );

      const runtime =
        await fs.readFile(
          path.join(
            android,
            "app",
            "src",
            "main",
            "python",
            "appforge_web_runtime.py"
          ),
          "utf8"
        );

      assert.match(
        runtime,
        /mysite\.settings/
      );

      assert.match(
        runtime,
        /mysite\.wsgi/
      );

      assert.match(
        runtime,
        /django\.setup/
      );
    } finally {
      await fs.rm(
        root,
        {
          recursive: true,
          force: true
        }
      );
    }
  }
);
