import { promises as fs } from "fs";
import path from "path";
import { v4 as uuidv4 } from "uuid";
import { query } from "./db.js";
import { config } from "./config.js";
import { preflight } from "./buildEngine.js";
import {
  computeCacheKey,
  findCache
} from "./buildCache.js";
import { putInput } from "./storage.js";
import { enqueueJob } from "./jobQueue.js";
import {
  reserveProjectQuota,
  recordSuccessfulBuild,
  releaseBuildQuotaReservation,
  releaseProjectQuotaReservation
} from "./projectQuotaV2.js";

import {
  reserveMonthlyBuildQuota,
  releaseMonthlyBuildQuotaReservation
} from "./monthlyBuildQuota.js";
import { enforceProForConfig, applyServerBranding } from "./proEntitlements.js";
import {
  normalizeIdempotencyKey,
  resolveIdempotency,
  rememberIdempotency
} from "./idempotency.js";
import {
  exportProjectZip,
  getProjectForWorkspace
} from "./workspace.js";
import {
  inspectUnityProjectArchive
} from "./unityAndroidBuildEngine.js";
import {
  unityWorkerRequirements
} from "./unityWorkerContract.js";

export async function submitWorkspaceBuild(
  projectId,
  userId,
  {
    buildOutput = "both",
    priority = 100,
    configOverride = {},
    idempotencyKey = null
  } = {}
) {
  const project =
    await getProjectForWorkspace(
      projectId,
      userId,
      "build.create"
    );

  const storedPackageName =
    String(
      project.package_name ||
      ""
    ).trim();

  const requestedPackageName =
    String(
      configOverride?.packageName ||
      storedPackageName
    ).trim();

  if (!requestedPackageName) {
    const error =
      new Error(
        "packageName gerekli."
      );

    error.statusCode = 400;
    error.code =
      "PACKAGE_NAME_REQUIRED";

    throw error;
  }

  /*
   * İlk başarılı build'den sonra aynı project_id artık
   * başka package adına geçirilemez.
   */
  if (
    project.package_locked_at &&
    requestedPackageName !==
      storedPackageName
  ) {
    const error =
      new Error(
        "Bu proje başarılı build aldığı için packageName kilitlidir. " +
        "Yeni packageName kullanmak için yeni proje oluştur."
      );

    error.statusCode = 409;
    error.code =
      "PROJECT_PACKAGE_LOCKED";

    throw error;
  }

  /*
   * Henüz SUCCESS almamış taslak proje package adını
   * değiştirebilir. Kimlik değişikliği DB'ye de yazılır.
   */
  if (
    !project.package_locked_at &&
    requestedPackageName !==
      storedPackageName
  ) {
    try {
      const updated =
        await query(
          `UPDATE appforge_projects
           SET
             package_name = $2,
             config =
               jsonb_set(
                 COALESCE(
                   config,
                   '{}'::jsonb
                 ),
                 '{packageName}',
                 to_jsonb($2::text),
                 TRUE
               ),
             updated_at = NOW()
           WHERE id = $1
           RETURNING
             package_name,
             config`,
          [
            projectId,
            requestedPackageName
          ]
        );

      if (!updated.rowCount) {
        const error =
          new Error(
            "Proje bulunamadı."
          );

        error.statusCode = 404;
        throw error;
      }

      project.package_name =
        updated.rows[0]
          .package_name;

      project.config =
        updated.rows[0]
          .config || {};
    } catch (error) {
      if (
        String(
          error?.code ||
          ""
        ) === "23505"
      ) {
        const conflict =
          new Error(
            "Bu packageName başka bir projede kullanılıyor."
          );

        conflict.statusCode = 409;
        conflict.code =
          "PROJECT_PACKAGE_CONFLICT";

        throw conflict;
      }

      throw error;
    }
  }

  const tempDir =
    path.join(
      config.workRoot,
      "_workspace_builds"
    );

  await fs.mkdir(
    tempDir,
    { recursive: true }
  );

  const tempZip =
    path.join(
      tempDir,
      `${uuidv4()}.zip`
    );

  await exportProjectZip(
    projectId,
    userId,
    tempZip
  );

  const c = {
    ...(project.config || {}),
    ...(configOverride || {}),
    appName:
      configOverride.appName ||
      project.name,
    packageName:
      project.package_name,
    sourceMode: "LOCAL",
    buildOutput,
    versionName:
      configOverride.versionName ||
      project.config?.versionName ||
      "1.0.0",
    versionCode:
      Number(
        configOverride.versionCode ||
        project.config?.versionCode ||
        1
      ),
    signing: {
      mode: "DEBUG",
      ...(
        configOverride.signing ||
        {}
      )
    },
    firebase: {
      ...(
        project.config
          ?.firebase ||
        {}
      ),
      ...(
        configOverride
          .firebase ||
        {}
      )
    },
    features: {
      ...(
        project.config
          ?.features ||
        {}
      ),
      ...(
        configOverride
          .features ||
        {}
      )
    }
  };

  await enforceProForConfig(
    userId,
    c
  );

  await applyServerBranding(
    userId,
    c
  );

  if (
    String(
      c.sourceBuildEngine ||
      ""
    )
      .trim()
      .toLowerCase() ===
      "unity-android"
  ) {
    const unityProject =
      inspectUnityProjectArchive(
        tempZip
      );

    c.unityEditorVersion =
      unityProject.editorVersion;

    c.workerRequirements =
      unityWorkerRequirements(
        unityProject.editorVersion
      );
  }

  const report =
    preflight(
      c,
      {
        hasProject: true,
        hasKeystore: false,
        hasIcon: false,
        hasFirebaseConfig: false
      }
    );

  const cacheKey =
    await computeCacheKey(
      c,
      {
        projectFile:
          tempZip
      }
    );

  const normalizedIdempotencyKey =
    normalizeIdempotencyKey(
      idempotencyKey
    );

  const existing =
    await resolveIdempotency(
      userId,
      normalizedIdempotencyKey,
      cacheKey
    );

  if (existing) {
    await fs.rm(
      tempZip,
      { force: true }
    );

    return {
      buildId:
        existing.buildId,
      status:
        "existing",
      idempotentReplay:
        true
    };
  }

  const cached =
    await findCache(
      cacheKey
    );

  /*
   * Taslak oluşturma değil, gerçek build başlangıcı reserve eder.
   */
  await reserveProjectQuota(
    userId,
    c.packageName
  );

  const buildId =
    uuidv4();

  if (cached) {
    /*
     * Cache HIT de kullanıcıya teslim edilmiş başarılı build'dir.
     */
    await reserveMonthlyBuildQuota(
      buildId,
      userId,
      {
        projectId,
        packageName:
          c.packageName
      }
    );

    await fs.rm(
      tempZip,
      { force: true }
    );

    await query(
      `INSERT INTO appforge_builds(
         id,
         user_id,
         team_id,
         project_id,
         app_name,
         package_name,
         status,
         progress,
         output_type,
         config,
         preflight,
         outputs,
         cache_key,
         cache_hit,
         priority,
         started_at,
         completed_at
       )
       VALUES(
         $1,$2,$3,$4,$5,$6,
         'success',100,$7,
         $8::jsonb,$9::jsonb,
         $10::jsonb,$11,TRUE,
         $12,NOW(),NOW()
       )`,
      [
        buildId,
        userId,
        project.team_id,
        projectId,
        c.appName,
        c.packageName,
        buildOutput,
        JSON.stringify(c),
        JSON.stringify([
          ...report,
          "✅ Workspace build cache HIT."
        ]),
        JSON.stringify(
          cached.outputs ||
          {}
        ),
        cacheKey,
        priority
      ]
    );

    try {
      await recordSuccessfulBuild(
        buildId,
        userId
      );
    } catch (error) {
      try {
        await releaseMonthlyBuildQuotaReservation(
          buildId
        );
      } catch {}

      try {
        await releaseProjectQuotaReservation(
          userId,
          c.packageName,
          {
            force: true
          }
        );
      } catch {}

      throw error;
    }

    await rememberIdempotency(
      userId,
      normalizedIdempotencyKey,
      cacheKey,
      buildId
    );

    return {
      buildId,
      status: "success",
      cacheHit: true
    };
  }

  const projectRef =
    await putInput(
      buildId,
      "project.zip",
      tempZip
    );

  await query(
    `INSERT INTO appforge_builds(
       id,
       user_id,
       team_id,
       project_id,
       app_name,
       package_name,
       status,
       progress,
       output_type,
       config,
       preflight,
       cache_key,
       priority
     )
     VALUES(
       $1,$2,$3,$4,$5,$6,
       'queued',0,$7,
       $8::jsonb,$9::jsonb,
       $10,$11
     )`,
    [
      buildId,
      userId,
      project.team_id,
      projectId,
      c.appName,
      c.packageName,
      buildOutput,
      JSON.stringify(c),
      JSON.stringify([
        ...report,
        "ℹ️ Workspace build cache MISS."
      ]),
      cacheKey,
      priority
    ]
  );

  const requiredCapabilities =
    Array.isArray(
      c.workerRequirements
    )
      ? c.workerRequirements
      : [
          "android-api-37",
          "java-17",
          "gradle"
        ];

  try {
    await enqueueJob({
      buildId,
      userId,
      teamId:
        project.team_id,
      priority,
      requiredCapabilities,
      payload: {
        config: c,
        cacheKey,
        projectRef,
        keystoreRef: null,
        iconRef: null,
        firebaseConfigRef: null
      }
    });
  } catch (error) {
    try {
      await releaseBuildQuotaReservation(
        buildId,
        {
          force: true
        }
      );
    } catch {}

    throw error;
  }

  await rememberIdempotency(
    userId,
    normalizedIdempotencyKey,
    cacheKey,
    buildId
  );

  return {
    buildId,
    status: "queued",
    cacheHit: false,
    requiredCapabilities
  };
}
