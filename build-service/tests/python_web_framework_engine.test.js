import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  parsePythonRequirements,
  preparePythonWebFrameworkSource
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
      path.dirname(
        target
      ),
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
  "requirements parser keeps package names and rejects remote/local directives",
  () => {
    const parsed =
      parsePythonRequirements(
`Flask==3.1.2
requests>=2.32
# comment
-r other.txt
-e .
https://example.com/pkg.whl
`
      );

    assert.deepEqual(
      parsed.dependencies,
      [
        "flask",
        "requests"
      ]
    );

    assert.equal(
      parsed.ignored.length,
      3
    );
  }
);

test(
  "Flask app contract is detected",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-flask-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "requirements.txt":
`Flask==3.1.2
`,
            "app.py":
`from flask import Flask

app = Flask(__name__)

@app.get("/")
def index():
    return "OK"
`
          }
        );

      const prepared =
        await preparePythonWebFrameworkSource(
          {
            projectZip:
              zipPath,
            workDir:
              path.join(
                root,
                "work"
              )
          }
        );

      assert.equal(
        prepared.framework,
        "flask"
      );

      assert.equal(
        prepared.contract.appObject,
        "app"
      );

      assert.equal(
        prepared.contract.ready,
        true
      );

      assert.deepEqual(
        prepared.dependencies,
        [
          "flask"
        ]
      );

      assert.equal(
        prepared.runtimePlan,
        "chaquopy-flask-loopback-webview"
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
  "Django settings and WSGI contract are detected",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-django-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "requirements.txt":
`Django==5.2.5
`,
            "manage.py":
              "# django manage\n",
            "mysite/__init__.py":
              "",
            "mysite/settings.py":
`SECRET_KEY = "test"
ROOT_URLCONF = "mysite.urls"
`,
            "mysite/wsgi.py":
`import os
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "mysite.settings")
`
          }
        );

      const prepared =
        await preparePythonWebFrameworkSource(
          {
            projectZip:
              zipPath,
            workDir:
              path.join(
                root,
                "work"
              )
          }
        );

      assert.equal(
        prepared.framework,
        "django"
      );

      assert.equal(
        prepared.contract.settingsModule,
        "mysite.settings"
      );

      assert.equal(
        prepared.contract.wsgiModule,
        "mysite.wsgi"
      );

      assert.equal(
        prepared.contract.ready,
        true
      );

      assert.equal(
        prepared.runtimePlan,
        "chaquopy-django-wsgi-loopback-webview"
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
