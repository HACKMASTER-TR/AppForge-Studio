import os from "os";
import { config } from "./config.js";
import {
  claimNextJob,
  completeJob,
  failOrRequeueJob,
  markCancelledJob,
  heartbeat,
  registerWorker,
  requeueStaleJobs
} from "./jobQueue.js";
import { executeBuild } from "./buildEngine.js";

let stopping = false;

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
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
        await sleep(config.workerPollMs);
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

        await executeBuild({
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
      } finally {
        clearInterval(interval);
      }
    } catch (error) {
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
