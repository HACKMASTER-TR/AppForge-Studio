import os from "os";
import { config } from "./config.js";
import {
  waitForBuildSignal
} from "./redis.js";
import {
  captureException
} from "./observability.js";
import {
  claimNextJob,
  completeJob,
  failOrRequeueJob,
  markCancelledJob,
  heartbeat,
  registerWorker,
  requeueStaleJobs,
  touchWorker
} from "./jobQueue.js";
import { executeBuild } from "./buildEngine.js";
import { executeWindowsBuild } from "./windowsBuild.js";
import { executeUnityBuild } from "./unityLicensedBuild.js";
import {
  classifyBuildError
} from "./buildErrorClassifier.js";

let stopping = false;

const presenceTimers =
  new Set();


function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function failClassifiedJob(
  job,
  error
) {
  const classification =
    classifyBuildError(
      error
    );

  const message =
    String(
      error?.message ||
      error ||
      "Build hatası"
    );

  const classifiedError =
    new Error(
      `[${classification.category}] ${message}`
    );

  classifiedError.code =
    error?.code ||
    classification.code;

  console.error(
    "[WORKER ERROR CLASSIFICATION]",
    JSON.stringify({
      buildId:
        job?.build_id || null,
      jobId:
        job?.id || null,
      category:
        classification.category,
      code:
        classification.code,
      retryable:
        classification.retryable
    })
  );

  /*
   * Yalnız geçici altyapı/ağ hataları retry edilir.
   * Kotlin, Gradle, Firebase, signing vb. gerçek hatalar
   * aynı pahalı build'i ikinci kez çalıştırmaz.
   */
  const effectiveJob =
    classification.retryable
      ? job
      : {
          ...job,
          attempts:
            job?.max_attempts ||
            job?.attempts ||
            1
        };

  await failOrRequeueJob(
    effectiveJob,
    classifiedError
  );
}

export async function startWorker({
  workerId =
    `${config.workerId}@${os.hostname()}`,
  concurrency =
    config.buildConcurrency,
  capabilities =
    config.workerCapabilities,
  diagnostics = {}
} = {}) {
  const slotIds =
    Array.from(
      {
        length:
          concurrency
      },
      (_, i) =>
        `${workerId}#${i + 1}`
    );

  for (
    const slotId of slotIds
  ) {
    await registerWorker(
      slotId,
      capabilities,
      1,
      "1.6.0",
      diagnostics
    );
  }

  const presenceEveryMs =
    Math.max(
      2000,
      Math.min(
        config.workerHeartbeatMs,
        Math.floor(
          config.workerStaleAfterMs /
          3
        )
      )
    );

  for (
    const slotId of slotIds
  ) {
    const timer =
      setInterval(
        () => {
          touchWorker(
            slotId,
            capabilities
          ).catch(
            error => {
              console.error(
                `Worker presence heartbeat failed: ${slotId}`,
                error?.message ||
                error
              );
            }
          );
        },
        presenceEveryMs
      );

    timer.unref();

    presenceTimers.add(
      timer
    );
  }

  const slots =
    slotIds.map(
      slotId =>
        workerLoop(
          slotId,
          capabilities
        )
    );

  const reaper =
    staleReaper();

  await Promise.all([
    ...slots,
    reaper
  ]);
}

export function stopWorker() {
  stopping = true;

  for (
    const timer of
    presenceTimers
  ) {
    clearInterval(
      timer
    );
  }

  presenceTimers.clear();
}

async function staleReaper() {
  while (!stopping) {
    try {
      await requeueStaleJobs();
    } catch (error) {
      console.error("Stale reaper error:", error);
    }

    await sleep(
      Math.max(
        config.workerStaleAfterMs / 2,
        5000
      )
    );
  }
}

async function workerLoop(workerSlotId, capabilities) {
  console.log(
    `Worker slot started: ${workerSlotId} [${capabilities.join(", ")}]`
  );

  while (!stopping) {
    let job = null;

    try {
      job = await claimNextJob(
        workerSlotId,
        capabilities
      );

      if (!job) {
        await waitForBuildSignal(
          workerSlotId,
          config.workerPollMs
        );
        continue;
      }

      const interval = setInterval(() => {
        heartbeat(
          job.id,
          workerSlotId,
          capabilities
        ).catch(() => {});
      }, config.workerHeartbeatMs);

      interval.unref();

      try {
        console.log(
          `Worker ${workerSlotId} claimed job ${job.id} build ${job.build_id}`
        );

        const buildConfig =
          job.payload?.config ||
          {};

        const sourceEngine =
          String(
            buildConfig.sourceBuildEngine ||
            ""
          )
            .trim()
            .toLowerCase();

        const executor =
          sourceEngine ===
            "unity-android"
            ? executeUnityBuild
            : (
                buildConfig.buildOutput ===
                  "exe"
                  ? executeWindowsBuild
                  : executeBuild
              );

        await executor({
          jobId: job.id,
          buildId: job.build_id,
          userId: job.user_id,
          teamId: job.team_id,
          workerId: workerSlotId,
          ...job.payload
        });

        await completeJob(
          job.id,
          job.build_id,
          job.user_id,
          job.team_id
        );
      } catch (error) {
        captureException(
          error,
          {
            component: "worker-build",
            worker: workerSlotId,
            jobId: job?.id,
            buildId: job?.build_id
          }
        );

        console.error(
          `[WORKER BUILD ERROR] job=${job?.id} build=${job?.build_id}`,
          error?.stack || error
        );

        if (error?.code === "BUILD_CANCELLED") {
          await markCancelledJob(
            job,
            String(
              error?.message ||
              "Build iptal edildi."
            )
          );
        } else {
          await failClassifiedJob(
            job,
            error
          );
        }
      } finally {
        clearInterval(interval);
      }
    } catch (error) {
      captureException(
        error,
        {
          component: "worker-loop",
          worker: workerSlotId,
          jobId: job?.id,
          buildId: job?.build_id
        }
      );

      console.error("Worker loop error:", error);

      if (job) {
        try {
          if (error?.code === "BUILD_CANCELLED") {
            await markCancelledJob(
              job,
              String(
                error?.message ||
                "Build iptal edildi."
              )
            );
          } else {
            await failOrRequeueJob(
              job,
              error
            );
          }
        } catch {}
      }

      await sleep(config.workerPollMs);
    }
  }
}
