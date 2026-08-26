import os from "os";
import { query, tx } from "./db.js";
import { config } from "./config.js";
import {
  requiredSourceWorkerCapabilities
} from "./sourceBuildIsolation.js";

export async function queuedCount() {
  const result = await query(
    `SELECT COUNT(*)::int AS count
     FROM appforge_build_jobs
     WHERE status = 'queued'`
  );
  return result.rows[0].count;
}

export async function enqueueJob({
  buildId,
  userId,
  teamId = null,
  payload,
  priority = 100,
  requiredCapabilities = []
}) {
  const effectiveRequiredCapabilities =
    requiredSourceWorkerCapabilities(
      {
        payload,
        requiredCapabilities,
        requireIsolation:
          config.sourceBuildRequireIsolation,
        isolationCapability:
          config.sourceBuildIsolationCapability
      }
    );

  const count = await queuedCount();

  if (count >= config.maxQueueSize) {
    const error = new Error("Build kuyruğu dolu.");
    error.code = "QUEUE_FULL";
    throw error;
  }

  await query(
    `INSERT INTO appforge_build_jobs(
       build_id,
       user_id,
       team_id,
       payload,
       priority,
       max_attempts,
       required_capabilities
     )
     VALUES($1,$2,$3,$4::jsonb,$5,$6,$7::jsonb)`,
    [
      buildId,
      userId,
      teamId,
      JSON.stringify(payload),
      priority,
      config.maxJobAttempts,
      JSON.stringify(effectiveRequiredCapabilities)
    ]
  );

  await event(buildId, userId, teamId, "queued", {
    priority,
    requiredCapabilities: effectiveRequiredCapabilities
  });
}

export async function registerWorker(
  workerId,
  capabilities,
  slots = 1,
  version = "1.6.0",
  diagnostics = {}
) {
  await query(
    `INSERT INTO appforge_workers(
       worker_id,
       capabilities,
       slots,
       hostname,
       version,
       toolchain_ok,
       diagnostics,
       last_error,
       last_seen_at
     )
     VALUES(
       $1,$2::jsonb,$3,$4,$5,$6,$7::jsonb,$8,NOW()
     )
     ON CONFLICT(worker_id)
     DO UPDATE SET
       capabilities = EXCLUDED.capabilities,
       slots = EXCLUDED.slots,
       hostname = EXCLUDED.hostname,
       version = EXCLUDED.version,
       toolchain_ok = EXCLUDED.toolchain_ok,
       diagnostics = EXCLUDED.diagnostics,
       last_error = EXCLUDED.last_error,
       last_seen_at = NOW()`,
    [
      workerId,
      JSON.stringify(
        capabilities
      ),
      slots,
      os.hostname(),
      version,
      Boolean(
        diagnostics?.ok
      ),
      JSON.stringify(
        diagnostics || {}
      ),
      diagnostics?.ok
        ? null
        : (
            diagnostics?.errors ||
            []
          ).join(" ")
    ]
  );
}

export async function claimNextJob(workerId, capabilities) {
  return tx(async client => {
    const result = await client.query(
      `SELECT j.*
       FROM appforge_build_jobs j
       JOIN appforge_builds b
         ON b.id = j.build_id
       WHERE j.status = 'queued'
         AND b.cancel_requested = FALSE
         AND j.available_at <= NOW()
         AND j.required_capabilities <@ $1::jsonb
       ORDER BY j.priority ASC, j.created_at ASC
       FOR UPDATE OF j SKIP LOCKED
       LIMIT 1`,
      [JSON.stringify(capabilities)]
    );

    if (!result.rowCount) return null;

    const job = result.rows[0];

    await client.query(
      `UPDATE appforge_build_jobs
       SET
         status = 'running',
         worker_id = $2,
         locked_at = NOW(),
         heartbeat_at = NOW(),
         attempts = attempts + 1,
         updated_at = NOW()
       WHERE id = $1`,
      [job.id, workerId]
    );

    await client.query(
      `UPDATE appforge_builds
       SET
         status = 'building',
         started_at = COALESCE(started_at, NOW())
       WHERE id = $1`,
      [job.build_id]
    );

    return {
      ...job,
      status: "running",
      worker_id: workerId,
      attempts: job.attempts + 1
    };
  });
}

export async function heartbeat(
  jobId,
  workerId,
  capabilities = config.workerCapabilities
) {
  await Promise.all([
    query(
      `UPDATE appforge_build_jobs
       SET heartbeat_at = NOW(), updated_at = NOW()
       WHERE id = $1 AND worker_id = $2 AND status = 'running'`,
      [jobId, workerId]
    ),
    query(
      `UPDATE appforge_workers
       SET
         capabilities = $2::jsonb,
         last_seen_at = NOW()
       WHERE worker_id = $1`,
      [workerId, JSON.stringify(capabilities)]
    )
  ]);
}

export async function completeJob(jobId, buildId, userId, teamId) {
  await query(
    `UPDATE appforge_build_jobs
     SET
       status = 'success',
       heartbeat_at = NOW(),
       updated_at = NOW()
     WHERE id = $1`,
    [jobId]
  );

  await event(buildId, userId, teamId, "worker_completed", {});
}

export async function failOrRequeueJob(job, error) {
  const message = String(error?.message || error).slice(0, 4000);
  const canRetry =
    Number(job.attempts || 0) < Number(job.max_attempts || 1);

  if (canRetry) {
    const delaySeconds =
      Math.min(60, 5 * Math.max(1, job.attempts));

    await query(
      `UPDATE appforge_build_jobs
       SET
         status = 'queued',
         worker_id = NULL,
         locked_at = NULL,
         heartbeat_at = NULL,
         last_error = $2,
         available_at = NOW() + ($3 || ' seconds')::interval,
         updated_at = NOW()
       WHERE id = $1`,
      [job.id, message, String(delaySeconds)]
    );

    await event(
      job.build_id,
      job.user_id,
      job.team_id,
      "retry_scheduled",
      {
        attempts: job.attempts,
        delaySeconds,
        error: message
      }
    );
  } else {
    await query(
      `UPDATE appforge_build_jobs
       SET
         status = 'failed',
         last_error = $2,
         heartbeat_at = NOW(),
         updated_at = NOW()
       WHERE id = $1`,
      [job.id, message]
    );

    await query(
      `UPDATE appforge_builds
       SET
         status = 'failed',
         progress = 0,
         error = $2,
         completed_at = NOW()
       WHERE id = $1`,
      [job.build_id, message]
    );

    await event(
      job.build_id,
      job.user_id,
      job.team_id,
      "worker_failed",
      { error: message }
    );
  }
}

export async function requeueStaleJobs() {
  const result = await query(
    `UPDATE appforge_build_jobs
     SET
       status = 'queued',
       worker_id = NULL,
       locked_at = NULL,
       heartbeat_at = NULL,
       available_at = NOW(),
       updated_at = NOW(),
       last_error =
         COALESCE(last_error, '') || ' [stale worker requeue]'
     WHERE status = 'running'
       AND heartbeat_at <
         NOW() - ($1 || ' milliseconds')::interval
     RETURNING id, build_id`,
    [String(config.workerStaleAfterMs)]
  );

  return result.rows;
}


export async function setQueuedPriority(
  buildId,
  priority
) {
  const safe =
    Math.max(
      1,
      Math.min(
        1000,
        Number(priority || 100)
      )
    );

  const result =
    await query(
      `UPDATE appforge_build_jobs
       SET
         priority = $2,
         updated_at = NOW()
       WHERE build_id = $1
         AND status = 'queued'
       RETURNING priority`,
      [buildId, safe]
    );

  if (!result.rowCount) {
    throw new Error(
      "Yalnızca kuyruktaki build'in önceliği değiştirilebilir."
    );
  }

  await query(
    `UPDATE appforge_builds
     SET priority = $2
     WHERE id = $1`,
    [buildId, safe]
  );

  return safe;
}

export async function requestBuildCancellation(
  buildId
) {
  return tx(async client => {
    const buildResult =
      await client.query(
        `SELECT
           status,
           cancel_requested
         FROM appforge_builds
         WHERE id = $1
         FOR UPDATE`,
        [buildId]
      );

    const build =
      buildResult.rows[0];

    if (!build) {
      throw new Error(
        "Build bulunamadı."
      );
    }

    if (
      [
        "success",
        "failed",
        "cancelled"
      ].includes(build.status)
    ) {
      return {
        status:
          build.status,
        immediate: true
      };
    }

    await client.query(
      `UPDATE appforge_builds
       SET
         cancel_requested = TRUE,
         cancel_requested_at =
           COALESCE(
             cancel_requested_at,
             NOW()
           )
       WHERE id = $1`,
      [buildId]
    );

    if (
      build.status ===
      "queued"
    ) {
      await client.query(
        `UPDATE appforge_build_jobs
         SET
           status = 'cancelled',
           updated_at = NOW()
         WHERE build_id = $1
           AND status = 'queued'`,
        [buildId]
      );

      await client.query(
        `UPDATE appforge_builds
         SET
           status = 'cancelled',
           progress = 0,
           cancelled_at = NOW(),
           completed_at = NOW()
         WHERE id = $1`,
        [buildId]
      );

      return {
        status:
          "cancelled",
        immediate: true
      };
    }

    return {
      status:
        "cancellation_requested",
      immediate: false
    };
  });
}

export async function markCancelledJob(
  job,
  message = "Build kullanıcı tarafından iptal edildi."
) {
  await query(
    `UPDATE appforge_build_jobs
     SET
       status = 'cancelled',
       last_error = $2,
       heartbeat_at = NOW(),
       updated_at = NOW()
     WHERE id = $1`,
    [job.id, message]
  );

  await query(
    `UPDATE appforge_builds
     SET
       status = 'cancelled',
       progress = 0,
       error = NULL,
       cancelled_at = NOW(),
       completed_at = NOW()
     WHERE id = $1`,
    [job.build_id]
  );

  await event(
    job.build_id,
    job.user_id,
    job.team_id,
    "cancelled",
    { message }
  );
}

export async function queueStats() {
  const [jobs, workers] = await Promise.all([
    query(
      `SELECT
         COUNT(*) FILTER (WHERE status = 'queued')::int AS queued,
         COUNT(*) FILTER (WHERE status = 'running')::int AS running,
         COUNT(*) FILTER (WHERE status = 'failed')::int AS failed,
         COUNT(*) FILTER (WHERE status = 'success')::int AS success
       FROM appforge_build_jobs`
    ),
    query(
      `SELECT
         worker_id,
         capabilities,
         slots,
         version,
         toolchain_ok,
         diagnostics,
         last_error,
         last_seen_at
       FROM appforge_workers
       WHERE last_seen_at >
         NOW() - ($1 || ' milliseconds')::interval
       ORDER BY worker_id`,
      [String(config.workerStaleAfterMs * 2)]
    )
  ]);

  return {
    ...jobs.rows[0],
    configuredConcurrency: config.buildConcurrency,
    maxQueueSize: config.maxQueueSize,
    workers: workers.rows
  };
}

export async function event(
  buildId,
  userId,
  teamId,
  eventType,
  payload = {}
) {
  await query(
    `INSERT INTO appforge_build_events(
       build_id,
       user_id,
       team_id,
       event_type,
       payload
     )
     VALUES($1,$2,$3,$4,$5::jsonb)`,
    [
      buildId,
      userId,
      teamId,
      eventType,
      JSON.stringify(payload)
    ]
  );
}
