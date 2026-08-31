export function estimateQueueWaitSeconds({
  ahead = 0,
  compatibleWorkerSlots = 0,
  runningCompatibleJobs = 0,
  averageBuildSeconds = 0,
  availableInSeconds = 0
} = {}) {
  const slots =
    Math.max(
      0,
      Math.floor(
        Number(
          compatibleWorkerSlots || 0
        )
      )
    );

  const average =
    Math.max(
      0,
      Math.floor(
        Number(
          averageBuildSeconds || 0
        )
      )
    );

  const retryDelay =
    Math.max(
      0,
      Math.ceil(
        Number(
          availableInSeconds || 0
        )
      )
    );

  if (
    slots <= 0 ||
    average <= 0
  ) {
    return null;
  }

  const busy =
    Math.min(
      slots,
      Math.max(
        0,
        Math.floor(
          Number(
            runningCompatibleJobs || 0
          )
        )
      )
    );

  const availableSlots =
    Math.max(
      0,
      slots - busy
    );

  const aheadCount =
    Math.max(
      0,
      Math.floor(
        Number(
          ahead || 0
        )
      )
    );

  /*
   * Hedef build de hesaplamaya dahil edilir.
   */
  const jobsThroughTarget =
    aheadCount + 1;

  const delayedJobs =
    Math.max(
      0,
      jobsThroughTarget -
      availableSlots
    );

  const batchesToTarget =
    Math.ceil(
      delayedJobs /
      slots
    );

  const queueWaitSeconds =
    batchesToTarget *
    average;

  /*
   * available_at beklemesi ile worker beklemesi
   * paralel ilerlediği için toplanmaz; büyük olan alınır.
   */
  return Math.max(
    retryDelay,
    queueWaitSeconds
  );
}
