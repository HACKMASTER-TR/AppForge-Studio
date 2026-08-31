import {
  promises as fs
} from "fs";
import path from "path";
import {
  spawn
} from "child_process";

import {
  query
} from "./db.js";
import {
  config
} from "./config.js";
import {
  materializeInput,
  putOutput
} from "./storage.js";
import {
  storeCache
} from "./buildCache.js";
import {
  appendBuildLog
} from "./buildLogs.js";
import {
  prepareUnityAndroidSource
} from "./unityAndroidBuildEngine.js";
import {
  normalizeUnityEditorVersion
} from "./unityWorkerContract.js";
import {
  createSourceBuildEnv
} from "./sourceBuildEnv.js";

const UNITY_LOG_LIMIT =
  400_000;

function csString(
  value
) {
  return String(
    value ?? ""
  )
    .replaceAll(
      "\\",
      "\\\\"
    )
    .replaceAll(
      "\"",
      "\\\""
    )
    .replaceAll(
      "\r",
      ""
    )
    .replaceAll(
      "\n",
      "\\n"
    );
}

export function unityOutputFormats(
  outputType
) {
  const normalized =
    String(
      outputType ||
      "apk"
    )
      .trim()
      .toLowerCase();

  if (
    normalized ===
      "apk"
  ) {
    return [
      "apk"
    ];
  }

  if (
    normalized ===
      "aab"
  ) {
    return [
      "aab"
    ];
  }

  return [
    "apk",
    "aab"
  ];
}

export function createUnityBuildScript({
  appName,
  packageName,
  versionCode,
  versionName
}) {
  const safeAppName =
    csString(
      appName ||
      "AppForge Unity"
    );

  const safePackage =
    csString(
      packageName
    );

  const safeVersionName =
    csString(
      versionName ||
      "1.0.0"
    );

  const safeVersionCode =
    Math.max(
      1,
      Number(
        versionCode ||
        1
      )
    );

  return `using System;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;

public static class AppForgeGeneratedBuild
{
    public static void Run()
    {
        var format =
            (Environment.GetEnvironmentVariable("APPFORGE_UNITY_OUTPUT") ?? "apk")
                .Trim()
                .ToLowerInvariant();

        if (format != "apk" && format != "aab")
        {
            throw new Exception("Unsupported AppForge Unity output: " + format);
        }

        var scenes =
            EditorBuildSettings.scenes
                .Where(scene => scene.enabled && !string.IsNullOrWhiteSpace(scene.path))
                .Select(scene => scene.path)
                .ToArray();

        if (scenes.Length == 0)
        {
            throw new Exception("Unity build için en az bir enabled scene gerekli.");
        }

        PlayerSettings.productName =
            "${safeAppName}";

        PlayerSettings.SetApplicationIdentifier(
            NamedBuildTarget.Android,
            "${safePackage}"
        );

        PlayerSettings.bundleVersion =
            "${safeVersionName}";

        PlayerSettings.Android.bundleVersionCode =
            ${safeVersionCode};

        // Test/installable source output only.
        // Production keystore secrets are deliberately not exposed to Unity project code.
        PlayerSettings.Android.useCustomKeystore =
            false;

        EditorUserBuildSettings.buildAppBundle =
            format == "aab";

        var outputDir =
            Path.Combine(
                Directory.GetCurrentDirectory(),
                "AppForgeBuild"
            );

        Directory.CreateDirectory(
            outputDir
        );

        var outputPath =
            Path.Combine(
                outputDir,
                format == "aab"
                    ? "app-release.aab"
                    : "app-release.apk"
            );

        var options =
            new BuildPlayerOptions
            {
                scenes =
                    scenes,
                locationPathName =
                    outputPath,
                target =
                    BuildTarget.Android,
                targetGroup =
                    BuildTargetGroup.Android,
                options =
                    BuildOptions.None
            };

        var report =
            BuildPipeline.BuildPlayer(
                options
            );

        if (
            report.summary.result !=
            BuildResult.Succeeded
        )
        {
            throw new Exception(
                "Unity Android build failed: " +
                report.summary.result
            );
        }

        Console.WriteLine(
            "APPFORGE_UNITY_ARTIFACT=" +
            outputPath
        );
    }
}
`;
}

async function log(
  buildId,
  line
) {
  const text =
    String(
      line ||
      ""
    );

  console.log(
    `[UNITY ${buildId}] ${text}`
  );

  await appendBuildLog(
    buildId,
    text
  );
}

async function updateBuild(
  buildId,
  fields
) {
  const allowed =
    {
      status:
        "status",
      progress:
        "progress",
      outputs:
        "outputs",
      startedAt:
        "started_at",
      completedAt:
        "completed_at",
      workerId:
        "worker_id",
      durationMs:
        "duration_ms",
      artifactManifest:
        "artifact_manifest"
    };

  const sets =
    [];

  const values =
    [
      buildId
    ];

  let index =
    2;

  for (
    const [
      key,
      value
    ] of
    Object.entries(
      fields
    )
  ) {
    const column =
      allowed[key];

    if (
      !column
    ) {
      continue;
    }

    if (
      key ===
        "outputs" ||
      key ===
        "artifactManifest"
    ) {
      sets.push(
        `${column} = $${index}::jsonb`
      );

      values.push(
        JSON.stringify(
          value ||
          {}
        )
      );
    } else {
      sets.push(
        `${column} = $${index}`
      );

      values.push(
        value
      );
    }

    index +=
      1;
  }

  if (
    !sets.length
  ) {
    return;
  }

  await query(
    `UPDATE appforge_builds
     SET ${sets.join(", ")}
     WHERE id = $1`,
    values
  );
}

async function isCancelled(
  buildId
) {
  const result =
    await query(
      `SELECT cancel_requested
       FROM appforge_builds
       WHERE id = $1`,
      [
        buildId
      ]
    );

  return Boolean(
    result.rows[0]
      ?.cancel_requested
  );
}

async function throwIfCancelled(
  buildId
) {
  if (
    await isCancelled(
      buildId
    )
  ) {
    const error =
      new Error(
        "Build kullanıcı tarafından iptal edildi."
      );

    error.code =
      "BUILD_CANCELLED";

    throw error;
  }
}

async function runUnity({
  buildId,
  projectRoot,
  format,
  expectedEditorVersion
}) {
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
      "UNITY_EDITOR_PATH tanımlı değil."
    );
  }

  const workerVersion =
    normalizeUnityEditorVersion(
      config.unityEditorVersion
    );

  const projectVersion =
    normalizeUnityEditorVersion(
      expectedEditorVersion
    );

  if (
    workerVersion !==
      projectVersion
  ) {
    throw new Error(
      `Unity Worker sürümü uyuşmuyor. Worker=${workerVersion}, proje=${projectVersion}`
    );
  }

  const workerHome =
    path.resolve(
      config.unityWorkerHome
    );

  const tempDir =
    path.join(
      workerHome,
      "tmp",
      buildId
    );

  await fs.mkdir(
    tempDir,
    {
      recursive: true
    }
  );

  const env =
    createSourceBuildEnv(
      {
        PATH:
          process.env.PATH ||
          "",
        HOME:
          workerHome,
        TMPDIR:
          tempDir,
        CI:
          "true",
        APPFORGE_UNITY_OUTPUT:
          format,
        UNITY_THISISABUILDMACHINE:
          "1"
      }
    );

  await throwIfCancelled(
    buildId
  );

  await log(
    buildId,
    `🎮 Unity ${workerVersion} batchmode • ${format.toUpperCase()}`
  );

  await new Promise(
    (
      resolve,
      reject
    ) => {
      const child =
        spawn(
          editorPath,
          [
            "-batchmode",
            "-nographics",
            "-quit",
            "-buildTarget",
            "Android",
            "-projectPath",
            projectRoot,
            "-executeMethod",
            "AppForgeGeneratedBuild.Run",
            "-logFile",
            "-"
          ],
          {
            cwd:
              projectRoot,
            env,
            shell:
              false,
            stdio: [
              "ignore",
              "pipe",
              "pipe"
            ]
          }
        );

      let output =
        "";

      let cancelled =
        false;

      const consume =
        chunk => {
          const text =
            String(
              chunk
            );

          if (
            output.length <
              UNITY_LOG_LIMIT
          ) {
            output +=
              text.slice(
                0,
                UNITY_LOG_LIMIT -
                  output.length
              );
          }

          for (
            const line of
            text
              .split(
                /\r?\n/
              )
              .map(
                value =>
                  value.trim()
              )
              .filter(
                Boolean
              )
              .slice(
                0,
                24
              )
          ) {
            log(
              buildId,
              line.slice(
                0,
                1000
              )
            )
              .catch(
                () => {}
              );
          }
        };

      child.stdout.on(
        "data",
        consume
      );

      child.stderr.on(
        "data",
        consume
      );

      const timeout =
        setTimeout(
          () => {
            child.kill(
              "SIGKILL"
            );
          },
          config.unityBuildTimeoutMs
        );

      const cancelPoll =
        setInterval(
          () => {
            isCancelled(
              buildId
            )
              .then(
                requested => {
                  if (
                    requested
                  ) {
                    cancelled =
                      true;

                    child.kill(
                      "SIGKILL"
                    );
                  }
                }
              )
              .catch(
                () => {}
              );
          },
          2000
        );

      cancelPoll.unref();

      child.once(
        "error",
        error => {
          clearTimeout(
            timeout
          );

          clearInterval(
            cancelPoll
          );

          reject(
            error
          );
        }
      );

      child.once(
        "close",
        (
          code,
          signal
        ) => {
          clearTimeout(
            timeout
          );

          clearInterval(
            cancelPoll
          );

          if (
            cancelled
          ) {
            const error =
              new Error(
                "Build kullanıcı tarafından iptal edildi."
              );

            error.code =
              "BUILD_CANCELLED";

            reject(
              error
            );

            return;
          }

          if (
            code ===
              0
          ) {
            resolve(
              output
            );
          } else {
            reject(
              new Error(
                signal
                  ? `Unity sinyal ile kapandı: ${signal}\n${output.slice(-16000)}`
                  : `Unity exit=${code}\n${output.slice(-16000)}`
              )
            );
          }
        }
      );
    }
  );
}

async function ensureArtifact(
  projectRoot,
  format
) {
  const file =
    path.join(
      projectRoot,
      "AppForgeBuild",
      format ===
        "aab"
        ? "app-release.aab"
        : "app-release.apk"
    );

  const stat =
    await fs.stat(
      file
    );

  if (
    !stat.isFile() ||
    stat.size <=
      0
  ) {
    throw new Error(
      `Unity ${format.toUpperCase()} artifact boş veya bulunamadı.`
    );
  }

  return file;
}

export async function executeUnityBuild({
  buildId,
  workerId,
  config:
    buildConfig,
  cacheKey = null,
  projectRef
}) {
  const startedAt =
    Date.now();

  if (
    !config.unityBuildEnabled
  ) {
    throw new Error(
      "UNITY_BUILD_ENABLED=false. Unity build server tarafında etkin değil."
    );
  }

  if (
    String(
      buildConfig?.signing?.mode ||
      "DEBUG"
    )
      .toUpperCase() ===
      "CUSTOM"
  ) {
    throw new Error(
      "Unity kaynak build'inde CUSTOM signing, izole signer tamamlanana kadar kapalı."
    );
  }

  const expectedVersion =
    normalizeUnityEditorVersion(
      buildConfig?.unityEditorVersion
    );

  const work =
    path.join(
      config.workRoot,
      `unity-${buildId}`
    );

  const projectZip =
    path.join(
      work,
      "project.zip"
    );

  await fs.rm(
    work,
    {
      recursive: true,
      force: true
    }
  );

  await fs.mkdir(
    work,
    {
      recursive: true
    }
  );

  try {
    await updateBuild(
      buildId,
      {
        status:
          "building",
        progress:
          5,
        startedAt:
          new Date(),
        workerId
      }
    );

    await materializeInput(
      projectRef,
      projectZip
    );

    await log(
      buildId,
      "🎮 Unity proje indirildi • güvenli hazırlama başlıyor."
    );

    const prepared =
      await prepareUnityAndroidSource(
        {
          projectZip,
          workDir:
            path.join(
              work,
              "prepared"
            ),
          onLog:
            line =>
              log(
                buildId,
                line
              ),
          cancelled:
            () =>
              throwIfCancelled(
                buildId
              )
        }
      );

    if (
      normalizeUnityEditorVersion(
        prepared.editorVersion
      ) !==
        expectedVersion
    ) {
      throw new Error(
        `Unity proje sürümü queue ile uyuşmuyor. ZIP=${prepared.editorVersion}, job=${expectedVersion}`
      );
    }

    const editorDir =
      path.join(
        prepared.projectRoot,
        "Assets",
        "Editor"
      );

    await fs.mkdir(
      editorDir,
      {
        recursive: true
      }
    );

    await fs.writeFile(
      path.join(
        editorDir,
        "AppForgeGeneratedBuild.cs"
      ),
      createUnityBuildScript(
        {
          appName:
            buildConfig.appName,
          packageName:
            buildConfig.packageName,
          versionCode:
            buildConfig.versionCode,
          versionName:
            buildConfig.versionName
        }
      ),
      "utf8"
    );

    await updateBuild(
      buildId,
      {
        progress:
          20
      }
    );

    const outputs =
      {};

    for (
      const format of
      unityOutputFormats(
        buildConfig.buildOutput
      )
    ) {
      await runUnity(
        {
          buildId,
          projectRoot:
            prepared.projectRoot,
          format,
          expectedEditorVersion:
            expectedVersion
        }
      );

      const artifact =
        await ensureArtifact(
          prepared.projectRoot,
          format
        );

      const ref =
        await putOutput(
          buildId,
          format ===
            "aab"
            ? "app-release.aab"
            : "app-release.apk",
          artifact
        );

      if (
        format ===
          "aab"
      ) {
        outputs.aab =
          ref;
      } else {
        outputs.apk =
          ref;
      }

      await updateBuild(
        buildId,
        {
          progress:
            format ===
              "apk"
              ? 65
              : 85
        }
      );
    }

    const artifactManifest =
      Object.fromEntries(
        Object.entries(
          outputs
        )
          .map(
            (
              [
                key,
                ref
              ]
            ) => [
              key,
              {
                name:
                  ref.name,
                sizeBytes:
                  ref.sizeBytes,
                sha256:
                  ref.sha256
              }
            ]
          )
      );

    await storeCache(
      {
        cacheKey,
        sourceBuildId:
          buildId,
        outputs,
        metadata: {
          buildMode:
            "UNITY_ANDROID",
          unityEditorVersion:
            expectedVersion
        }
      }
    );

    await updateBuild(
      buildId,
      {
        status:
          "success",
        progress:
          100,
        outputs,
        artifactManifest,
        completedAt:
          new Date(),
        durationMs:
          Date.now() -
          startedAt
      }
    );

    await log(
      buildId,
      "✅ Unity Android build tamamlandı."
    );

    return outputs;
  } finally {
    await fs.rm(
      work,
      {
        recursive: true,
        force: true
      }
    )
      .catch(
        () => {}
      );
  }
}
