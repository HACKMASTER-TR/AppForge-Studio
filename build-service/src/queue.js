import { config } from "./config.js";

const pending = [];
let active = 0;
let handler = null;

export function setBuildHandler(fn) {
  handler = fn;
}

export function queueStats() {
  return {
    active,
    pending: pending.length,
    concurrency: config.buildConcurrency,
    maxQueueSize: config.maxQueueSize
  };
}

export function enqueueBuild(job) {
  if (pending.length >= config.maxQueueSize) {
    const error = new Error("Build kuyruğu dolu.");
    error.code = "QUEUE_FULL";
    throw error;
  }

  pending.push(job);
  pump();
}

async function pump() {
  if (!handler) return;

  while (active < config.buildConcurrency && pending.length > 0) {
    const job = pending.shift();
    active += 1;

    Promise.resolve()
      .then(() => handler(job))
      .catch(error => {
        console.error("Queue job failed:", error);
      })
      .finally(() => {
        active -= 1;
        pump();
      });
  }
}
