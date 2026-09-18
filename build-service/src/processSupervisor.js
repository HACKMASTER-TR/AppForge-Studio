import fs from "fs";
import { spawn } from "child_process";
import { config } from "./config.js";

function finiteNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

export function memoryPressurePercent(
  currentBytes,
  limitBytes
) {
  const current =
    finiteNumber(currentBytes);

  const limit =
    finiteNumber(limitBytes);

  if (
    current == null ||
    limit == null ||
    current < 0 ||
    limit <= 0
  ) {
    return null;
  }

  return (
    current /
    limit
  ) * 100;
}

function readFirst(paths) {
  for (const file of paths) {
    try {
      const value =
        fs.readFileSync(
          file,
          "utf8"
        ).trim();

      if (value) {
        return value;
      }
    } catch {}
  }

  return null;
}

export function memoryPressureSnapshot() {
  const currentRaw =
    readFirst([
      "/sys/fs/cgroup/memory.current",
      "/sys/fs/cgroup/memory/memory.usage_in_bytes"
    ]);

  const limitRaw =
    readFirst([
      "/sys/fs/cgroup/memory.max",
      "/sys/fs/cgroup/memory/memory.limit_in_bytes"
    ]);

  if (
    currentRaw == null ||
    limitRaw == null ||
    limitRaw === "max"
  ) {
    return null;
  }

  const currentBytes =
    finiteNumber(
      currentRaw
    );

  const limitBytes =
    finiteNumber(
      limitRaw
    );

  const percent =
    memoryPressurePercent(
      currentBytes,
      limitBytes
    );

  if (
    currentBytes == null ||
    limitBytes == null ||
    percent == null
  ) {
    return null;
  }

  return {
    currentBytes,
    limitBytes,
    percent
  };
}

export function spawnProcessGroup(
  command,
  args,
  options = {}
) {
  return spawn(
    command,
    args,
    {
      ...options,
      detached:
        options.detached ??
        (
          process.platform !==
          "win32"
        )
    }
  );
}

function signalTree(
  child,
  signal
) {
  if (
    !child ||
    !child.pid
  ) {
    return;
  }

  if (
    process.platform ===
    "win32"
  ) {
    if (
      signal ===
      "SIGKILL"
    ) {
      try {
        spawn(
          "taskkill",
          [
            "/PID",
            String(
              child.pid
            ),
            "/T",
            "/F"
          ],
          {
            windowsHide: true,
            stdio: "ignore"
          }
        );
      } catch {}
    }

    return;
  }

  try {
    process.kill(
      -child.pid,
      signal
    );
    return;
  } catch {}

  try {
    child.kill(
      signal
    );
  } catch {}
}

export function terminateProcessTree(
  child,
  {
    graceMs = 4000
  } = {}
) {
  if (
    !child ||
    !child.pid
  ) {
    return;
  }

  if (
    process.platform ===
    "win32"
  ) {
    signalTree(
      child,
      "SIGKILL"
    );
    return;
  }

  signalTree(
    child,
    "SIGTERM"
  );

  const force =
    () => {
      signalTree(
        child,
        "SIGKILL"
      );
    };

  if (
    graceMs <= 0
  ) {
    force();
    return;
  }

  const timer =
    setTimeout(
      force,
      graceMs
    );

  timer.unref();
}

function supervisorError(
  code,
  message
) {
  const error =
    new Error(
      message
    );

  error.code =
    code;

  return error;
}

export function runSupervisedProcess({
  command,
  args = [],
  cwd,
  env,
  hardTimeoutMs = 0,
  stallTimeoutMs =
    config.sourceCommandStallTimeoutMs,
  memoryCriticalPct =
    config.workerMemoryCriticalPct,
  memoryPressureGraceMs =
    config.workerMemoryPressureGraceMs,
  pollMs =
    config.workerResourcePollMs,
  cancelled = null,
  onChunk = null,
  onEvent = null,
  shell = false
}) {
  return new Promise(
    (
      resolve,
      reject
    ) => {
      const child =
        spawnProcessGroup(
          command,
          args,
          {
            cwd,
            env,
            shell,
            stdio: [
              "ignore",
              "pipe",
              "pipe"
            ]
          }
        );

      let settled =
        false;

      let stopReason =
        null;

      let lastOutputAt =
        Date.now();

      let memoryCriticalSince =
        0;

      let cancellationCheckActive =
        false;

      const notify =
        message => {
          if (!onEvent) return;

          Promise.resolve(
            onEvent(
              String(
                message
              )
            )
          ).catch(
            () => {}
          );
        };

      const requestStop =
        error => {
          if (
            settled ||
            stopReason
          ) {
            return;
          }

          stopReason =
            error;

          terminateProcessTree(
            child
          );
        };

      const consume =
        (
          stream,
          chunk
        ) => {
          lastOutputAt =
            Date.now();

          if (
            onChunk
          ) {
            try {
              onChunk(
                chunk,
                stream
              );
            } catch {}
          }
        };

      child.stdout.on(
        "data",
        chunk =>
          consume(
            "stdout",
            chunk
          )
      );

      child.stderr.on(
        "data",
        chunk =>
          consume(
            "stderr",
            chunk
          )
      );

      const hardTimer =
        hardTimeoutMs > 0
          ? setTimeout(
              () => {
                const error =
                  supervisorError(
                    "PROCESS_TIMEOUT",
                    `ProcessTimeout: ${command} ${Math.round(hardTimeoutMs / 1000)} saniyelik toplam süre sınırını aştı.`
                  );

                notify(
                  "⚠️ Build komutu toplam süre sınırını aştı; process tree güvenli biçimde durduruluyor."
                );

                requestStop(
                  error
                );
              },
              hardTimeoutMs
            )
          : null;

      hardTimer?.unref();

      const monitor =
        setInterval(
          () => {
            if (
              settled ||
              stopReason
            ) {
              return;
            }

            const now =
              Date.now();

            if (
              stallTimeoutMs > 0 &&
              now -
                lastOutputAt >=
                stallTimeoutMs
            ) {
              const error =
                supervisorError(
                  "WORKER_STALL_TIMEOUT",
                  `WorkerStallTimeout: ${Math.round(stallTimeoutMs / 1000)} saniye boyunca build ilerlemesi alınamadı.`
                );

              notify(
                "♻️ Build ilerlemesi durdu; AppForge takılan process tree'yi kapatıp build'i otomatik kurtaracak."
              );

              requestStop(
                error
              );

              return;
            }

            const memory =
              memoryPressureSnapshot();

            if (
              memory &&
              memory.percent >=
                memoryCriticalPct
            ) {
              if (
                !memoryCriticalSince
              ) {
                memoryCriticalSince =
                  now;
              }

              if (
                now -
                  memoryCriticalSince >=
                  memoryPressureGraceMs
              ) {
                const error =
                  supervisorError(
                    "WORKER_MEMORY_PRESSURE",
                    `WorkerMemoryPressure: worker bellek kullanımı %${memory.percent.toFixed(1)} seviyesinde ve güvenli sınırı aştı.`
                  );

                notify(
                  "♻️ Worker bellek baskısı algılandı; AppForge build'i güvenli worker'da otomatik yeniden deneyecek."
                );

                requestStop(
                  error
                );

                return;
              }
            } else {
              memoryCriticalSince =
                0;
            }

            if (
              cancelled &&
              !cancellationCheckActive
            ) {
              cancellationCheckActive =
                true;

              Promise.resolve()
                .then(
                  () =>
                    cancelled()
                )
                .catch(
                  error => {
                    requestStop(
                      error
                    );
                  }
                )
                .finally(
                  () => {
                    cancellationCheckActive =
                      false;
                  }
                );
            }
          },
          Math.max(
            500,
            pollMs
          )
        );

      monitor.unref();

      const cleanup =
        () => {
          settled =
            true;

          clearInterval(
            monitor
          );

          if (
            hardTimer
          ) {
            clearTimeout(
              hardTimer
            );
          }
        };

      child.once(
        "error",
        error => {
          if (
            settled
          ) {
            return;
          }

          cleanup();

          reject(
            stopReason ||
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
          if (
            settled
          ) {
            return;
          }

          cleanup();

          if (
            stopReason
          ) {
            reject(
              stopReason
            );
            return;
          }

          resolve({
            code:
              Number(
                code
              ),
            signal:
              signal ||
              null
          });
        }
      );
    }
  );
}
