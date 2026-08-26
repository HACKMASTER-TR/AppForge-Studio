import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  inspectUnityProjectArchive
} from "../src/unityAndroidBuildEngine.js";

import {
  normalizeUnityEditorVersion,
  unityVersionChannel,
  unityWorkerRequirements,
  unityWorkerCapabilities,
  unityWorkerCanClaim
} from "../src/unityWorkerContract.js";

test(
  "Unity exact editor version becomes strict worker requirements",
  () => {
    assert.equal(
      normalizeUnityEditorVersion(
        "6000.5.9f1"
      ),
      "6000.5.9f1"
    );

    assert.deepEqual(
      unityVersionChannel(
        "6000.5.9f1"
      ),
      {
        major:
          "6000",
        minor:
          "5",
        family:
          "6000.5"
      }
    );

    const required =
      unityWorkerRequirements(
        "6000.5.9f1"
      );

    assert.deepEqual(
      required,
      [
        "unity-editor",
        "unity-android-build-support",
        "unity-family-6000.5",
        "unity-editor-6000.5.9f1"
      ]
    );

    assert.equal(
      unityWorkerCanClaim(
        {
          editorVersion:
            "6000.5.9f1",
          androidBuildSupport:
            true,
          requiredCapabilities:
            required
        }
      ),
      true
    );

    assert.equal(
      unityWorkerCanClaim(
        {
          editorVersion:
            "6000.5.8f1",
          androidBuildSupport:
            true,
          requiredCapabilities:
            required
        }
      ),
      false
    );
  }
);

test(
  "Unity worker does not advertise Android capability without module",
  () => {
    const capabilities =
      unityWorkerCapabilities(
        {
          editorVersion:
            "6000.0.60f1",
          androidBuildSupport:
            false
        }
      );

    assert.equal(
      capabilities.includes(
        "unity-editor"
      ),
      true
    );

    assert.equal(
      capabilities.includes(
        "unity-android-build-support"
      ),
      false
    );
  }
);

test(
  "Unity ZIP inspector reads ProjectVersion without full extraction",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-unity-route-"
        )
      );

    try {
      const zipPath =
        path.join(
          root,
          "project.zip"
        );

      const zip =
        new AdmZip();

      zip.addFile(
        "ProjectSettings/ProjectVersion.txt",
        Buffer.from(
`m_EditorVersion: 6000.5.9f1
m_EditorVersionWithRevision: 6000.5.9f1 (abc123)
`
        )
      );

      zip.addFile(
        "Assets/Scenes/Main.unity",
        Buffer.from(
          "%YAML 1.1\n"
        )
      );

      zip.writeZip(
        zipPath
      );

      const inspected =
        inspectUnityProjectArchive(
          zipPath
        );

      assert.equal(
        inspected.editorVersion,
        "6000.5.9f1"
      );

      assert.match(
        inspected.entryName,
        /ProjectVersion\.txt$/
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
  "Invalid Unity version is rejected",
  () => {
    assert.throws(
      () =>
        normalizeUnityEditorVersion(
          "latest"
        ),
      /Geçersiz Unity Editor sürümü/
    );
  }
);
