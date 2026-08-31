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
      configOverride.packageName ||
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

  const buildId =
    uuidv4();

  if (cached) {
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
