import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";

import {
  config,
  assertCriticalConfig
} from "./src/config.js";
import {
  migrate,
  closeDb
} from "./src/db.js";
import {
  startWorker,
  stopWorker
} from "./src/workerRuntime.js";
import {
  normalizeUnityEditorVersion,
  unityWorkerCapabilities
} from "./src/unityWorkerContract.js";

function candidateAndroidModules(
  editorPath
) {
  const editorDir =
    path.dirname(
      editorPath
    );

  const contentsDir =
    path.dirname(
      editorDir
    );

  return [
    config.unityAndroidModulePath,
    path.join(
      editorDir,
      "Data",
      "PlaybackEngines",
      "AndroidPlayer"
    ),
    path.join(
      contentsDir,
      "PlaybackEngines",
      "AndroidPlayer"
    )
  ]
    .filter(
      Boolean
    );
}

async function firstDirectory(
  candidates
) {
  for (
    const candidate of
    candidates
  ) {
    try {
      if (
        (
          await fs.stat(
            candidate
          )
        )
          .isDirectory()
      ) {
        return candidate;
      }
    } catch {}
  }

  return null;
}

assertCriticalConfig();

if (
  !config.unityBuildEnabled
) {
  throw new Error(
    "Unity Worker başlatılmadı: UNITY_BUILD_ENABLED=true gerekli."
  );
}

const editorPath =
  String(
    config.unityEditorPath ||
    ""
  )
    .trim();

if (
  !editorPath
) {
  throw new Error(
    "UNITY_EDITOR_PATH gerekli."
  );
}

const editorStat =
  await fs.stat(
    editorPath
  );

if (
  !editorStat.isFile()
) {
  throw new Error(
    "UNITY_EDITOR_PATH dosya değil."
  );
}

const editorVersion =
  normalizeUnityEditorVersion(
    config.unityEditorVersion
  );

const androidModule =
  await firstDirectory(
    candidateAndroidModules(
      editorPath
    )
  );

if (
  !androidModule
) {
  throw new Error(
    "Unity Android Build Support modülü bulunamadı. UNITY_ANDROID_MODULE_PATH ayarlanabilir."
  );
}

await fs.mkdir(
  config.workRoot,
  {
    recursive: true
  }
);

await fs.mkdir(
  config.outputRoot,
  {
    recursive: true
  }
);

await fs.mkdir(
  config.sharedInputRoot,
  {
    recursive: true
  }
);

await fs.mkdir(
  config.unityWorkerHome,
  {
    recursive: true
  }
);

await migrate();

const capabilities =
  unityWorkerCapabilities(
    {
      editorVersion,
      androidBuildSupport:
        true
    }
  );

const diagnostics =
  {
    ok:
      true,
    detected: {
      unityEditorPath:
        editorPath,
      unityEditorVersion:
        editorVersion,
      unityAndroidModule:
        androidModule,
      license:
        "runtime-check"
    },
    errors:
      []
  };

process.on(
  "SIGINT",
  async () => {
    stopWorker();
    await closeDb();
    process.exit(0);
  }
);

process.on(
  "SIGTERM",
  async () => {
    stopWorker();
    await closeDb();
    process.exit(0);
  }
);

console.log(
  `AppForge Unity Worker starting: ${editorVersion} @ ${os.hostname()}`
);

console.log(
  `Capabilities: ${capabilities.join(", ")}`
);

console.log(
  "Unity lisansı worker makinesinde önceden ve yasal şekilde etkin olmalıdır; kimlik bilgileri build job'larına aktarılmaz."
);

await startWorker(
  {
    workerId:
      `${config.workerId}-unity@${os.hostname()}`,
    concurrency:
      1,
    capabilities,
    diagnostics
  }
);
