import os from "os";
import { query, tx } from "./db.js";
import { config } from "./config.js";
import {
  estimateQueueWaitSeconds
} from "./queueEstimate.js";
import {
  getProEntitlement
} from "./proEntitlements.js";
import { signalBuildQueue } from "./redis.js";
import {
  triggerWorkerAutoscale
} from "./autoscaleDispatch.js";
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
  priority: requestedPriority = 100,
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

  /*
   * Priority istemciden güvenilir kabul edilmez.
   * Free / Pro önceliğini resmi sunucu belirler.
   */
  /*
   * Rol istemciden alınmaz; DB'deki güncel kullanıcı
   * kaydı sunucu tarafından okunur.
   */
  const roleResult =
    await query(
      `SELECT role
       FROM appforge_users
       WHERE id = $1
         AND is_active = TRUE
       LIMIT 1`,
      [userId]
    );

  const isAdmin =
    roleResult.rows[0]
      ?.role ===
      "admin";

  const entitlement =
    await getProEntitlement(
      userId
    );

  const isPro =
    Boolean(
      entitlement?.active
    );

  const plan =
    isAdmin
      ? "admin"
      : (
          isPro
            ? "pro"
            : "free"
        );

  const queuePriority =
    isAdmin
      ? config.adminBuildPriority
      : (
          isPro
            ? config.proBuildPriority
            : config.freeBuildPriority
        );

  const activeLimit =
    isAdmin
      ? config.adminActiveBuildLimit
      : (
          isPro
            ? config.proActiveBuildLimit
            : config.freeActiveBuildLimit
        );

  /*
   * Global advisory transaction lock:
   *
   * COUNT -> LIMIT CHECK -> INSERT zincirini
   * tek kritik bölüm haline getirir.
   *
   * Böylece yüzlerce / binlerce eşzamanlı HTTP isteği
   * MAX_QUEUE_SIZE sınırını aynı anda aşamaz.
   */
  const admission =
    await tx(
      async client => {
        await client.query(
          `SELECT
             pg_advisory_xact_lock(
               hashtext(
                 'appforge-build-queue-admission'
               )::bigint
             )`
        );

        const queueResult =
          await client.query(
            `SELECT
               COUNT(*)::int AS count
             FROM appforge_build_jobs
             WHERE status = 'queued'`
          );

        const queued =
          Number(
            queueResult.rows[0]
              ?.count || 0
          );

        if (
          queued >=
          config.maxQueueSize
        ) {
          const message =
            "Build kuyruğu şu anda dolu. Lütfen kısa süre sonra tekrar dene.";

          await client.query(
            `UPDATE appforge_builds
             SET
               status = 'failed',
               progress = 0,
               error = $2,
               completed_at = NOW()
             WHERE id = $1`,
            [
              buildId,
              message
            ]
          );

          await client.query(
            `INSERT INTO appforge_build_events(
               build_id,
               user_id,
               team_id,
               event_type,
               payload
             )
             VALUES(
               $1,$2,$3,'queue_rejected',$4::jsonb
             )`,
            [
              buildId,
              userId,
              teamId,
              JSON.stringify({
                code:
                  "QUEUE_FULL",
                queued,
                maxQueueSize:
                  config.maxQueueSize,
                plan
              })
            ]
          );

          return {
            rejected: true,
            code:
              "QUEUE_FULL",
            statusCode: 503,
            message,
            queued,
            maxQueueSize:
              config.maxQueueSize
          };
        }

        const activeResult =
          await client.query(
            `SELECT
               COUNT(*)::int AS count
             FROM appforge_build_jobs
             WHERE user_id = $1
               AND status IN (
                 'queued',
                 'running'
               )`,
            [
              userId
            ]
          );

        const active =
          Number(
            activeResult.rows[0]
              ?.count || 0
          );

        if (
          active >=
          activeLimit
        ) {
          const message =
            plan === "admin"
              ? `Admin hesabında aynı anda en fazla ${activeLimit} aktif build kullanılabilir.`
              : (
                  plan === "pro"
                    ? `Aynı anda en fazla ${activeLimit} aktif Pro build kullanılabilir.`
                    : `Free hesapta aynı anda en fazla ${activeLimit} aktif build kullanılabilir.`
                );

          await client.query(
            `UPDATE appforge_builds
             SET
               status = 'failed',
               progress = 0,
               error = $2,
               completed_at = NOW()
             WHERE id = $1`,
            [
              buildId,
              message
            ]
          );

          await client.query(
            `INSERT INTO appforge_build_events(
               build_id,
               user_id,
               team_id,
               event_type,
               payload
             )
             VALUES(
               $1,$2,$3,'queue_rejected',$4::jsonb
             )`,
            [
              buildId,
              userId,
              teamId,
              JSON.stringify({
                code:
                  "ACTIVE_BUILD_LIMIT",
                active,
                activeLimit,
                plan
              })
            ]
          );

          return {
            rejected: true,
            code:
              "ACTIVE_BUILD_LIMIT",
            statusCode: 429,
            message,
            active,
            activeLimit,
            plan
          };
        }

        await client.query(
          `INSERT INTO appforge_build_jobs(
             build_id,
             user_id,
             team_id,
             payload,
             priority,
             max_attempts,
             required_capabilities
           )
           VALUES(
             $1,$2,$3,$4::jsonb,$5,$6,$7::jsonb
           )`,
          [
            buildId,
            userId,
            teamId,
            JSON.stringify(
              payload
            ),
            queuePriority,
            config.maxJobAttempts,
            JSON.stringify(
              effectiveRequiredCapabilities
            )
          ]
        );

        await client.query(
          `INSERT INTO appforge_build_events(
             build_id,
             user_id,
             team_id,
             event_type,
             payload
           )
           VALUES(
             $1,$2,$3,'queued',$4::jsonb
           )`,
          [
            buildId,
            userId,
            teamId,
            JSON.stringify({
              priority:
                queuePriority,
              requestedPriority:
                Number(
                  requestedPriority ||
                  100
                ),
              plan,
              activeLimit,
              queuedBefore:
                queued,
              requiredCapabilities:
                effectiveRequiredCapabilities
            })
          ]
        );

        return {
          rejected: false,
          plan,
          priority:
            queuePriority,
          activeLimit,
          queuedBefore:
            queued
        };
      }
    );

  if (
    admission.rejected
  ) {
    const error =
      new Error(
        admission.message
      );

    error.code =
      admission.code;

    error.statusCode =
      admission.statusCode;

    error.queue =
      admission;

    throw error;
  }

  /*
   * PostgreSQL asıl kayıt sistemidir.
   * Redis yalnız worker'ı beklemeden uyandırır.
   */
  await signalBuildQueue();

  /*
   * Normal trafik GitHub workflow üretmesin.
   * Queue burst eşik değerine ulaşırsa autoscaler
   * hemen tetiklenir. Workflow gerçek replica
   * ihtiyacını kendisi hesaplar.
   */
  const queuedAfter =
    Number(
      admission.queuedBefore ||
      0
    ) + 1;

  if (
    queuedAfter >=
    config.autoscaleDispatchQueueThreshold
  ) {
    void triggerWorkerAutoscale({
      reason:
        `queue_burst_${queuedAfter}`
    }).catch(error => {
      console.warn(
        "AUTOSCALE_DISPATCH ERROR:",
        String(
          error?.message ||
          error
        ).slice(0, 300)
      );
    });
  }

  return admission;
}


export async function buildQueuePosition(
  buildId
) {
  const targetResult =
    await query(
      `SELECT
         j.id,
         j.status,
         j.priority,
         j.created_at,
         j.available_at,
         j.required_capabilities,
         b.output_type
       FROM appforge_build_jobs j
       JOIN appforge_builds b
         ON b.id = j.build_id
       WHERE j.build_id = $1
       LIMIT 1`,
      [
        buildId
      ]
    );

  const target =
    targetResult.rows[0];

  /*
   * Cache HIT gibi doğrudan tamamlanan build'lerde
   * queue job kaydı bulunmayabilir.
   */
  if (!target) {
    return null;
  }

  const workerStatsResult =
    await query(
      `SELECT
         COALESCE(
           SUM(w.slots),
           0
         )::int AS slots,
         COALESCE(
           (
             SELECT
               COUNT(*)::int
             FROM appforge_build_jobs r
             JOIN appforge_workers rw
               ON rw.worker_id = r.worker_id
             WHERE r.status = 'running'
               AND rw.last_seen_at >
                 NOW() -
                 ($1 || ' milliseconds')::interval
               AND rw.toolchain_ok = TRUE
               AND $2::jsonb <@
                   rw.capabilities

               -- Dedicated Source Worker yalnız
               -- source-isolation-dedicated isteyen
               -- build'lerde kapasiteye dahil edilir.
               AND (
                 NOT (
                   rw.capabilities ?
                   $3::text
                 )
                 OR (
                   $2::jsonb ?
                   $3::text
                 )
               )
           ),
           0
         )::int AS running
       FROM appforge_workers w
       WHERE w.last_seen_at >
         NOW() -
         ($1 || ' milliseconds')::interval
         AND w.toolchain_ok = TRUE
         AND $2::jsonb <@
             w.capabilities

         -- Source Worker normal Android slotu değildir.
         AND (
           NOT (
             w.capabilities ?
             $3::text
           )
           OR (
             $2::jsonb ?
             $3::text
           )
         )`,
      [
        String(
          config.workerStaleAfterMs *
          2
        ),
        JSON.stringify(
          target.required_capabilities ||
          []
        ),
        config.sourceBuildIsolationCapability
      ]
    );

  const compatibleWorkerSlots =
    Number(
      workerStatsResult.rows[0]
        ?.slots || 0
    );

  const runningCompatibleJobs =
    Number(
      workerStatsResult.rows[0]
        ?.running || 0
    );

  const busyWorkerSlots =
    Math.min(
      compatibleWorkerSlots,
      Math.max(
        0,
        runningCompatibleJobs
      )
    );

  const availableWorkerSlots =
    Math.max(
      0,
      compatibleWorkerSlots -
      busyWorkerSlots
    );

  const durationResult =
    await query(
      `SELECT
         COALESCE(
           AVG(duration_ms),
           0
         )::bigint AS average_ms
       FROM (
         SELECT
           duration_ms
         FROM appforge_builds
         WHERE status = 'success'
           AND duration_ms IS NOT NULL
           AND duration_ms > 0
           AND output_type = $1
         ORDER BY
           completed_at DESC
           NULLS LAST
         LIMIT 30
       ) recent`,
      [
        target.output_type
      ]
    );

  const averageMs =
    Number(
      durationResult.rows[0]
        ?.average_ms || 0
    );

  const averageBuildSeconds =
    averageMs > 0
      ? Math.max(
          1,
          Math.round(
            averageMs /
            1000
          )
        )
      : null;

  /*
   * Çalışmaya başlamış build artık kuyrukta değildir.
   */
  if (
    target.status ===
    "running"
  ) {
    return {
      status:
        "running",
      position: 0,
      ahead: 0,
      compatibleWorkerSlots,
      busyWorkerSlots,
      availableWorkerSlots,
      averageBuildSeconds,
      estimatedWaitSeconds: 0,
      estimate:
        "running"
    };
  }

  if (
    target.status !==
    "queued"
  ) {
    return {
      status:
        target.status,
      position: null,
      ahead: null,
      compatibleWorkerSlots,
      busyWorkerSlots,
      availableWorkerSlots,
      averageBuildSeconds,
      estimatedWaitSeconds: null,
      estimate:
        "not_queued"
    };
  }

  /*
   * Worker'ın kullandığı gerçek sıra:
   *
   * priority ASC
   * created_at ASC
   *
   * Yalnız şu anda claim edilebilir queued job'lar
   * önümüzde sayılır.
   */
  const aheadResult =
    await query(
      `SELECT
         COUNT(*)::int AS count
       FROM appforge_build_jobs q
       WHERE q.status = 'queued'
         AND q.available_at <= NOW()
         AND (
           q.priority < $1
           OR (
             q.priority = $1
             AND q.created_at < $2
           )
         )
         AND EXISTS (
           SELECT 1
           FROM appforge_workers w
           WHERE w.last_seen_at >
             NOW() -
             ($3 || ' milliseconds')::interval
             AND w.toolchain_ok = TRUE

             -- Worker hedef build'i çalıştırabilmeli.
             AND $4::jsonb <@
                 w.capabilities

             -- Hedef normal build ise dedicated Source
             -- Worker bu hedef için uygun değildir.
             AND (
               NOT (
                 w.capabilities ?
                 $5::text
               )
               OR (
                 $4::jsonb ?
                 $5::text
               )
             )

             -- Aynı worker öndeki işi de
             -- çalıştırabiliyorsa gerçekten
             -- hedef build'in önündedir.
             AND q.required_capabilities <@
                 w.capabilities

             -- Source Worker yalnız explicit source
             -- capability taşıyan öndeki işi claim edebilir.
             AND (
               NOT (
                 w.capabilities ?
                 $5::text
               )
               OR (
                 q.required_capabilities ?
                 $5::text
               )
             )
         )`,
      [
        target.priority,
        target.created_at,
        String(
          config.workerStaleAfterMs *
          2
        ),
        JSON.stringify(
          target.required_capabilities ||
          []
        ),
        config.sourceBuildIsolationCapability
      ]
    );

  const ahead =
    Number(
      aheadResult.rows[0]
        ?.count || 0
    );

  const position =
    ahead + 1;

  const availableAtMs =
    new Date(
      target.available_at
    ).getTime();

  const availableInSeconds =
    Number.isFinite(
      availableAtMs
    )
      ? Math.max(
          0,
          Math.ceil(
            (
              availableAtMs -
              Date.now()
            ) /
            1000
          )
        )
      : 0;

  const estimatedWaitSeconds =
    estimateQueueWaitSeconds({
      ahead,
      compatibleWorkerSlots,
      runningCompatibleJobs,
      averageBuildSeconds,
      availableInSeconds
    });

  return {
    status:
      "queued",
    position,
    ahead,
    priority:
      Number(
        target.priority
      ),
    compatibleWorkerSlots,
    busyWorkerSlots,
    availableWorkerSlots,
    averageBuildSeconds,
    availableInSeconds,
    estimatedWaitSeconds,

    /*
     * Build türleri farklı sürelerde çalışabildiği ve
     * daha yüksek öncelikli yeni işler gelebileceği için
     * bu değer tahmindir.
     */
    estimate:
      estimatedWaitSeconds == null
        ? "unavailable"
        : "approximate"
  };
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


export async function touchWorker(
  workerId,
  capabilities =
    config.workerCapabilities
) {
  await query(
    `UPDATE appforge_workers
     SET
       capabilities = $2::jsonb,
       last_seen_at = NOW()
     WHERE worker_id = $1`,
    [
      workerId,
      JSON.stringify(
        capabilities
      )
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

         -- Bir Worker source isolation capability taşıyorsa
         -- yalnız bu capability'yi açıkça isteyen job'ları
         -- claim edebilir. Böylece dedicated Source Worker
         -- normal APK/AAB kuyruğundan ayrılır.
         AND (
           NOT (
             $1::jsonb ?
             $2::text
           )
           OR (
             j.required_capabilities ?
             $2::text
           )
         )

       ORDER BY j.priority ASC, j.created_at ASC
       FOR UPDATE OF j SKIP LOCKED
       LIMIT 1`,
      [
        JSON.stringify(
          capabilities
        ),
        config.sourceBuildIsolationCapability
      ]
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

    await signalBuildQueue();
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

  if (result.rowCount) {
    await signalBuildQueue(
      result.rowCount
    );
  }

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

    const queuedCancellation =
      await client.query(
        `UPDATE appforge_build_jobs
         SET
           status = 'cancelled',
           updated_at = NOW()
         WHERE build_id = $1
           AND status = 'queued'
         RETURNING id`,
        [buildId]
      );

    // Build daha önce "building" durumuna geçmiş olsa bile
    // Worker ölümü/retry sonrasında job tekrar queued olabilir.
    // Böyle bir durumda iptal talebi orphan job bırakmamalıdır.
    if (
      queuedCancellation.rowCount > 0 ||
      build.status === "queued"
    ) {
      await client.query(
        `UPDATE appforge_builds
         SET
           status = 'cancelled',
           progress = 0,
           cancelled_at =
             COALESCE(
               cancelled_at,
               NOW()
             ),
           completed_at =
             COALESCE(
               completed_at,
               NOW()
             )
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
